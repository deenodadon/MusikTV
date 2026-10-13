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
