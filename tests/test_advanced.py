import json,io,zipfile
from pathlib import Path
from unittest.mock import Mock
import sys
sys.path.insert(0,str(Path(__file__).parents[1]/'app/src/main/python'))
import advanced,plugins,catalog
import pytest

@pytest.fixture(autouse=True)
def config(tmp_path):
    advanced.configure('{}',str(tmp_path))

def test_cookie_import_filters_other_domains():
    text='.youtube.com\tTRUE\t/\tTRUE\t0\tSAPISID\tvalue\n.evil.test\tTRUE\t/\tTRUE\t0\tother\tprivate\n'
    assert advanced.parse_cookies(text)=='SAPISID=value'
    with pytest.raises(advanced.UserError):advanced.parse_cookies('not a cookie export')

def test_torbox_only_returns_audio_and_refreshes_stream(monkeypatch):
    fake=Mock(return_value=[{'id':3,'download_finished':True,'files':[{'id':2,'name':'A.mp3'},{'id':5,'name':'movie.mp4'}]}])
    monkeypatch.setattr(advanced,'torbox',fake)
    rows=advanced.torbox_files('{"torrent_id":3}')
    assert len(rows)==1 and rows[0]['kind']=='torbox'
    advanced.settings['torbox_token']='test-local-fixture'
    fake.return_value='https://media.test/audio'
    assert advanced.torbox_stream(rows[0]['id'])['url']=='https://media.test/audio'
    assert fake.call_args.kwargs['params']['file_id']==2
    fake.return_value='file:///private'
    with pytest.raises(advanced.UserError):advanced.torbox_stream(rows[0]['id'])

def test_preparing_torrent_reports_progress(monkeypatch):
    monkeypatch.setattr(advanced,'torbox',Mock(return_value=[{'id':1,'download_finished':False,'progress':.25}]))
    with pytest.raises(advanced.UserError,match='25%'):advanced.torbox_files('{"torrent_id":1}')

def test_nova_engine_executes_in_process_and_returns_magnet(tmp_path):
    d=tmp_path/'nova-engines';d.mkdir()
    (d/'fixture.py').write_text("from novaprinter import prettyPrinter\nclass fixture:\n def search(self,q,cat):\n  prettyPrinter({'link':'magnet:?xt=urn:btih:test','name':'Audio','seeds':7})\n")
    rows=advanced.nova_search('audio','["fixture"]')
    assert rows[0]['kind']=='torrent' and 'magnet:' in rows[0]['id']
    assert advanced.nova_remove('fixture') is True

def test_nuclear_zip_rejects_traversal_before_extraction(tmp_path):
    data=io.BytesIO()
    with zipfile.ZipFile(data,'w') as z:z.writestr('../escaped','bad')
    with pytest.raises(advanced.UserError,match='unsafe'):plugins.unpack_zip(data.getvalue(),tmp_path/'install')
    assert not (tmp_path/'escaped').exists()

def test_npm_resolution_obeys_declared_range():
    assert plugins.npm_version({'versions':{'1.1.0':{},'1.3.0':{},'2.0.0':{}}},'^1.0.0')=='1.3.0'

def test_account_and_plugin_errors_do_not_echo_credentials():
    result=json.loads(catalog.call('account_playlists'))
    assert result['ok'] is False and 'Connect your Google account' in result['error']

def test_android_challenge_provider_verifies_shipped_ejs_code(monkeypatch):
    from unittest.mock import Mock
    import android_jsc
    from yt_dlp.extractor.youtube.jsc._builtin.ejs import ScriptType
    ie=Mock()
    ie.get_param.return_value={}
    provider=android_jsc.AndroidWebViewJCP(ie,Mock(),{})
    assert provider._get_script(ScriptType.CORE).hash in provider._ALLOWED_HASHES[ScriptType.CORE].values()
    assert provider._get_script(ScriptType.LIB).hash in provider._ALLOWED_HASHES[ScriptType.LIB].values()
    runtime=Mock();runtime.run.return_value='{"type":"result","responses":[]}'
    monkeypatch.setattr(android_jsc,'solver',runtime)
    assert provider.is_available()
    assert provider._run_js_runtime('verified code')=='{"type":"result","responses":[]}'
    runtime.run.assert_called_once_with('verified code')

def test_oauth_completion_returns_a_recognized_refreshable_token(monkeypatch):
    from ytmusicapi.auth.oauth.token import OAuthToken
    from requests.structures import CaseInsensitiveDict
    import ytmusicapi,time
    advanced.settings.update(oauth_id='fixture-client',oauth_secret='fixture-secret')
    fake=Mock();fake.token_from_code.return_value={'access_token':'fixture-access','refresh_token':'fixture-refresh','scope':'https://www.googleapis.com/auth/youtube','token_type':'Bearer','expires_in':3600}
    monkeypatch.setattr(ytmusicapi,'OAuthCredentials',Mock(return_value=fake))
    token=advanced.oauth_finish('fixture-device-code')
    assert OAuthToken.is_oauth(CaseInsensitiveDict(token))
    assert token['expires_at']>time.time()+3500

@pytest.mark.parametrize('state', ['authorization_pending', 'slow_down'])
def test_google_polling_wait_states_are_not_failed_logins(monkeypatch,state):
    import ytmusicapi
    advanced.settings.update(oauth_id='fixture-client',oauth_secret='fixture-secret')
    fake=Mock();fake.token_from_code.return_value={'error':state,'error_description':'private provider details'}
    monkeypatch.setattr(ytmusicapi,'OAuthCredentials',Mock(return_value=fake))
    assert advanced.oauth_poll('fixture-device-code')=={'status':state}

@pytest.mark.parametrize('state,message', [('expired_token','expired'),('access_denied','denied')])
def test_google_denial_and_expiry_are_safe_actionable_failures(monkeypatch,state,message):
    import ytmusicapi
    advanced.settings.update(oauth_id='fixture-client',oauth_secret='fixture-secret')
    fake=Mock();fake.token_from_code.return_value={'error':state,'error_description':'fixture-secret'}
    monkeypatch.setattr(ytmusicapi,'OAuthCredentials',Mock(return_value=fake))
    reply=json.loads(catalog.call('oauth_poll','["fixture-device-code"]'))
    assert not reply['ok'] and message in reply['error']
    assert 'fixture-secret' not in reply['error']

def test_partial_google_device_response_never_reaches_a_crashing_dialog(monkeypatch):
    import ytmusicapi
    advanced.settings.update(oauth_id='fixture-client',oauth_secret='fixture-secret')
    fake=Mock();fake.get_code.return_value={'device_code':'fixture-code'}
    monkeypatch.setattr(ytmusicapi,'OAuthCredentials',Mock(return_value=fake))
    with pytest.raises(advanced.UserError,match='did not issue a login code'):
        advanced.oauth_start()

def test_torbox_pairing_uses_the_official_device_endpoints(monkeypatch):
    fake=Mock();fake.get.return_value.json.return_value={'success':True,'data':{'device_code':'private-device','code':'USER-CODE','verification_url_complete':'https://torbox.app/oauth/device?code=USER-CODE','interval':5,'expires_in':600}}
    monkeypatch.setattr(advanced,'Session',Mock(return_value=fake))
    data=advanced.torbox_auth_start()
    assert data['code']=='USER-CODE'
    fake.get.assert_called_once_with('https://api.torbox.app/v1/api/user/auth/device/start',params={'app':'Musik TV'})
    fake.post.return_value.json.return_value={'success':False,'error':'DEVICE_CODE_NOT_USED','data':None}
    assert advanced.torbox_auth_poll(data['device_code'])=={'status':'pending'}
    fake.post.assert_called_once_with('https://api.torbox.app/v1/api/user/auth/device/token',json={'device_code':'private-device'})

@pytest.mark.parametrize('url',['https://torbox.app.evil.test/oauth/device','http://torbox.app/oauth/device','https://attacker@torbox.app/oauth/device','https://torbox.app/other'])
def test_torbox_qr_cannot_redirect_to_an_untrusted_verification_page(monkeypatch,url):
    fake=Mock();fake.get.return_value.json.return_value={'success':True,'data':{'device_code':'private-device','code':'USER-CODE','verification_url_complete':url}}
    monkeypatch.setattr(advanced,'Session',Mock(return_value=fake))
    with pytest.raises(advanced.UserError,match='invalid pairing address'):advanced.torbox_auth_start()

@pytest.mark.parametrize('state',['ITEM_NOT_FOUND','DEVICE_CODE_USED','ACCESS_DENIED'])
def test_torbox_pairing_failures_never_echo_private_codes_or_tokens(monkeypatch,state):
    fake=Mock();fake.post.return_value.json.return_value={'success':False,'error':state,'detail':'private-device private-token'}
    monkeypatch.setattr(advanced,'Session',Mock(return_value=fake))
    reply=json.loads(catalog.call('torbox_auth_poll','["private-device"]'))
    assert not reply['ok']
    assert 'private-device' not in reply['error'] and 'private-token' not in reply['error']

@pytest.mark.parametrize('payload',['fixture-token',{'token':'fixture-token'},{'api_key':'fixture-token'}])
def test_torbox_approval_returns_a_token_for_native_secure_storage(monkeypatch,payload):
    fake=Mock();fake.post.return_value.json.return_value={'success':True,'data':payload}
    monkeypatch.setattr(advanced,'Session',Mock(return_value=fake))
    assert advanced.torbox_auth_poll('fixture-device')=={'status':'connected','token':'fixture-token'}

@pytest.mark.parametrize('payload',[
    {'success':True,'data':{'api_token':'fixture-approved-api-token'}},
    {'success':True,'data':{'apiKey':'fixture-approved-api-token'}},
    {'success':True,'data':{'access_token':'fixture-approved-api-token'}},
    {'success':True,'api_key':'fixture-approved-api-token'},
    {'success':True,'data':{'credentials':{'apiToken':'fixture-approved-api-token'}}},
    {'success':True,'data':{'key':'Bearer fixture-approved-api-token'}},
])
def test_approved_torbox_response_variants_are_not_discarded(monkeypatch,payload):
    fake=Mock();fake.post.return_value.json.return_value=payload
    monkeypatch.setattr(advanced,'Session',Mock(return_value=fake))
    assert advanced.torbox_auth_poll('fixture-device')=={'status':'connected','token':'fixture-approved-api-token'}

@pytest.mark.parametrize('payload',[
    {'success':True,'data':{'device_code':'private-device-code','session_token':'private-session-token','refresh_token':'private-refresh-token'}},
    {'success':True,'data':{'detail':'private-provider-message'}},
    {'success':True,'data':{'token':{'unknown':'private-provider-value'}}},
])
def test_unknown_approved_shapes_do_not_expose_values_or_use_other_tokens(monkeypatch,payload):
    fake=Mock();fake.post.return_value.json.return_value=payload
    monkeypatch.setattr(advanced,'Session',Mock(return_value=fake))
    reply=json.loads(catalog.call('torbox_auth_poll','["private-device-code"]'))
    assert not reply['ok']
    assert 'Response schema:' in reply['error']
    assert 'private-' not in reply['error']

def test_nested_torbox_credential_envelopes_keep_the_api_token():
    assert advanced._torbox_device_token({'success':True,'data':{'token':{'api_key':'fixture-approved-api-token'}}})=='fixture-approved-api-token'
