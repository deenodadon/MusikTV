import importlib.util
import sys
sys.path.insert(0,str(__import__("pathlib").Path(__file__).parents[1]/"app/src/main/python"))
import json
from pathlib import Path
from unittest.mock import Mock
import pytest
spec = importlib.util.spec_from_file_location('catalog', Path(__file__).parents[1] / 'app/src/main/python/catalog.py')
catalog = importlib.util.module_from_spec(spec)
spec.loader.exec_module(catalog)

@pytest.fixture
def api(monkeypatch):
    fake = Mock()
    monkeypatch.setattr(catalog, '_api', fake)
    return fake

def test_search_maps_real_provider_shape_and_filters_unavailable(api):
    api.search.return_value = [{'videoId':'abcdefghijk','title':'Song','artists':[{'name':'Artist'}], 'thumbnails':[{'url':'https://img.test/a'}]}, {'videoId':'abcdefghijk','title':'Duplicate'}, {'videoId':'other','isAvailable':False}]
    result = catalog.search('Song')
    assert result == [{'id':'abcdefghijk','title':'Song','artist':'Artist','kind':'song','artwork':'https://img.test/a'}]
    api.search.assert_called_once_with('Song',filter='songs',limit=40)

@pytest.mark.parametrize('kind,method', [('album','get_album'),('playlist','get_playlist')])
def test_browse_collection(api,kind,method):
    getattr(api,method).return_value={'tracks':[{'videoId':'abcdefghijk','title':'A'}]}
    assert catalog.browse('collection',kind)[0]['title']=='A'

def test_artist_returns_songs_and_browsable_albums(api):
    api.get_artist.return_value={'songs':{'results':[{'videoId':'abcdefghijk','title':'A'}]},'albums':{'results':[{'browseId':'MPRE','title':'Record'}]}}
    assert [t['kind'] for t in catalog.browse('artist','artist')]==['song','album']

def test_youtube_links_cannot_be_confused_with_other_hosts(api):
    assert catalog.video_id('https://youtu.be/abcdefghijk')=='abcdefghijk'
    assert catalog.video_id('https://youtube.com.evil.test/watch?v=abcdefghijk') is None
    with pytest.raises(ValueError): catalog.search('https://other.test/audio')

def test_provider_errors_are_safe_and_actionable(api):
    api.search.side_effect=RuntimeError('HTTP 403 secret=value signed-url=https://private')
    result=json.loads(catalog.call('search','["song"]'))
    assert result['ok'] is False
    assert 'secret' not in result['error']
    assert 'denied' in result['error']

def test_lyrics_missing_is_not_fake_success(api):
    api.get_watch_playlist.return_value={}
    assert catalog.lyrics('abcdefghijk')=='Lyrics are not available for this track.'

def test_resolver_preserves_required_headers_and_rejects_unsafe_urls(monkeypatch):
    import yt_dlp
    fake=Mock()
    fake.__enter__=Mock(return_value=fake)
    fake.__exit__=Mock(return_value=False)
    constructor=Mock(return_value=fake)
    monkeypatch.setattr(yt_dlp,'YoutubeDL',constructor)
    fake.extract_info.return_value={'url':'https://media.test/a', 'http_headers':{'User-Agent':'agent','Cookie':'private'}}
    assert catalog.resolve('abcdefghijk')=={'url':'https://media.test/a','headers':{'User-Agent':'agent'}}
    # Browser-only overrides excluded upstream's token-free device fallback
    # and authenticated TV clients. Keep selection under upstream control.
    assert not constructor.call_args.args[0].get('extractor_args',{}).get('youtube',{}).get('player_client')
    fake.extract_info.return_value={'url':'http://media.test/a'}
    with pytest.raises(RuntimeError): catalog.resolve('abcdefghijk')
    with pytest.raises(ValueError): catalog.resolve('../private')

@pytest.mark.parametrize('failure,code', [
    ('Requested format is not available secret=value', 'YT-FORMAT'),
    ('PO Token required signed-url=https://private', 'YT-ATTESTATION'),
    ('JavaScript challenge failed secret=value', 'YT-JS'),
    ('HTTP 403 secret=value', 'YT-ACCESS'),
])
def test_stream_failures_are_distinct_and_do_not_expose_provider_details(monkeypatch,failure,code):
    monkeypatch.setattr(catalog,'resolve',Mock(side_effect=RuntimeError(failure)))
    result=json.loads(catalog.call('resolve','["abcdefghijk"]'))
    assert result['ok'] is False
    assert code in result['error']
    assert 'secret' not in result['error'] and 'https://' not in result['error']

def test_unsigned_android_resolver_has_a_device_client_without_browser_attestation(monkeypatch):
    import android_jsc
    from yt_dlp import YoutubeDL
    monkeypatch.setattr(android_jsc,'solver',object())
    with YoutubeDL({'quiet':True,'cachedir':False}) as ydl:
        ie=ydl.get_info_extractor('Youtube')
        ie.initialize()
        assert any(p.is_available() for p in ie._jsc_director.providers.values())
        clients=ie._get_requested_clients('https://www.youtube.com/watch?v=abcdefghijk',{},False)
        assert 'visionos' in clients
