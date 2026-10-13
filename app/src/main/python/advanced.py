"""Android counterparts of Musik accounts, Nova engines, and TorBox.

GPL-3.0-or-later. Nova sources retain their own bundled license notices.
Credentials are supplied in memory by Android Keystore; never persisted here.
"""
import ast
import hashlib
import http.cookiejar
import importlib.util
import json
import re
import sys
import threading
from pathlib import Path
from urllib.parse import urlparse, quote
import requests

settings = {}
root = None
_api = None
_stamp = None


class UserError(Exception):
    pass


class Session(requests.Session):
    def request(self, method, url, **kwargs):
        kwargs.setdefault('timeout', 25)
        return super().request(method, url, **kwargs)


def configure(data, directory):
    global settings, root, _api, _stamp
    parsed = json.loads(data)
    stamp = json.dumps({k: parsed.get(k) for k in ('cookies', 'oauth', 'oauth_id', 'oauth_secret')}, sort_keys=True)
    if stamp != _stamp:
        _api = None
        _stamp = stamp
    settings = parsed
    root = Path(directory)
    root.mkdir(parents=True, exist_ok=True)


def authenticated_api():
    global _api
    from ytmusicapi import YTMusic, OAuthCredentials
    if _api is None:
        if settings.get('cookies'):
            from http.cookies import SimpleCookie
            c = SimpleCookie(settings['cookies'])
            if not any(k in c for k in ('SAPISID','__Secure-3PAPISID','__Secure-1PAPISID')):
                raise UserError('This cookie file has no signed-in YouTube session.')
            _api = YTMusic(auth={'cookie': settings['cookies'], 'authorization': 'SAPISIDHASH', 'origin': 'https://music.youtube.com', 'x-goog-authuser':str(settings.get('account', 0))}, requests_session=Session())
        elif settings.get('oauth'):
            _api = YTMusic(auth=settings['oauth'], oauth_credentials=OAuthCredentials(settings['oauth_id'],settings['oauth_secret'],session=Session()), requests_session=Session())
        else:
            raise UserError('Connect your Google account in Account first.')
    return _api


def oauth_start():
    from ytmusicapi import OAuthCredentials
    if not settings.get('oauth_id') or not settings.get('oauth_secret'):
        raise UserError('Enter your Google TV/device OAuth client ID and secret first. Enable YouTube Data API for that client.')
    result = OAuthCredentials(settings['oauth_id'],settings['oauth_secret'],session=Session()).get_code()
    if not result.get('device_code'):
        raise UserError('Google did not issue a login code. Check the OAuth client type and API configuration.')
    return result


def oauth_finish(device_code):
    from ytmusicapi import OAuthCredentials
    result = OAuthCredentials(settings['oauth_id'],settings['oauth_secret'],session=Session()).token_from_code(device_code)
    if result.get('error') == 'authorization_pending':
        raise UserError('Finish approval on Google’s verification page, then check again.')
    if not result.get('refresh_token'):
        raise UserError('Google did not complete authorization. Restart sign-in or check the configured OAuth client.')
    import time
    result["expires_at"] = int(time.time()) + int(result.get("expires_in", 3600))
    return result


def parse_cookies(text):
    """Accept the user's own Netscape export, never scrape another app's storage."""
    entries = {}
    for line in text.splitlines():
        if line.startswith('#HttpOnly_'): line = line[len('#HttpOnly_'):]
        elif line.startswith('#'): continue
        fields = line.split('\t')
        if len(fields) == 7 and (fields[0].lstrip('.') == 'youtube.com' or fields[0].endswith('.youtube.com')):
            if re.fullmatch(r'[A-Za-z0-9_\-]+',fields[5]) and not any(x in fields[6] for x in ('\r','\n',';')):
                entries[fields[5]] = fields[6]
    if not any(k in entries for k in ('SAPISID','__Secure-3PAPISID','__Secure-1PAPISID')):
        raise UserError('Select a Netscape-format cookie export from your own signed-in YouTube session.')
    return '; '.join(f'{k}={v}' for k,v in entries.items())


def account_playlists():
    from catalog import track
    return [dict(id=p['playlistId'],title=p.get('title','Playlist'),artist='Your YouTube library',kind='playlist',artwork=(p.get('thumbnails') or [{}])[-1].get('url','')) for p in authenticated_api().get_library_playlists(limit=100) if p.get('playlistId')]


def cookie_jar():
    from http.cookies import SimpleCookie
    from yt_dlp.cookies import YoutubeDLCookieJar
    jar = YoutubeDLCookieJar()
    for name, morsel in SimpleCookie(settings.get('cookies','')).items():
        jar.set_cookie(http.cookiejar.Cookie(0,name,morsel.value,None,False,'.youtube.com',True,True,'/',True,True,None,True,None,None,{}))
    return jar


def torbox(method, endpoint, **kwargs):
    token = settings.get('torbox_token')
    if not token: raise UserError('Save your TorBox API key in Torrents first.')
    response = Session().request(method,'https://api.torbox.app/v1/api/'+endpoint,headers={'Authorization':'Bearer '+token},**kwargs)
    if response.status_code in (401,403): raise UserError('TorBox rejected the API key. Check your key in Torrents.')
    if response.status_code == 429: raise UserError('TorBox rate limit reached. Try again shortly.')
    response.raise_for_status()
    result = response.json()
    if not result.get('success'): raise UserError('TorBox could not complete this request. Check account status or retry.')
    return result.get('data')


def torbox_list():
    return [dict(id=json.dumps({'torrent_id':int(t['id'])}),title=t.get('name','Torrent'),artist=('Ready' if t.get('download_finished') else f"Preparing · {float(t.get('progress') or 0)*100:.0f}%"),kind='torrent',artwork='') for t in torbox('GET','torrents/mylist') or []]


def torbox_files(identity):
    payload = json.loads(identity)
    torrent_id = payload.get('torrent_id')
    if torrent_id is None:
        url = payload.get('url','')
        if urlparse(url).scheme not in ('magnet','https'): raise UserError('Choose a magnet or HTTPS .torrent link.')
        path = root/'torbox-items.json'
        saved = json.loads(path.read_text()) if path.exists() else {}
        key = hashlib.sha256((settings.get('torbox_token','')+'\0'+url).encode()).hexdigest()
        torrent_id = saved.get(key)
        if torrent_id is None:
            data = {'seed':'1','allow_zip':'false','as_queued':'false'}
            files = {}
            if url.startswith('magnet:'): data['magnet'] = url
            else:
                with Session().get(url,stream=True) as response:
                    response.raise_for_status()
                    content = bytearray()
                    for chunk in response.iter_content(65536):
                        content.extend(chunk)
                        if len(content)>2_000_000: raise UserError('Torrent metadata exceeds the 2 MB limit.')
                if not content.startswith(b'd') or not content.endswith(b'e') or b'4:info' not in content: raise UserError('This is a web page, not a torrent file. Use a magnet instead.')
                files = {'file':('torrent.torrent',bytes(content),'application/x-bittorrent')}
            result = torbox('POST','torrents/createtorrent',data=data,files=files)
            torrent_id = int(result['torrent_id'])
            saved[key] = torrent_id
            temporary = path.with_suffix('.tmp'); temporary.write_text(json.dumps(saved)); temporary.replace(path)
    result = torbox('GET','torrents/mylist',params={'id':int(torrent_id),'bypass_cache':'true'})
    item = next((t for t in result if int(t['id']) == int(torrent_id)),None) if isinstance(result,list) else result
    if not item or not item.get('download_finished'): raise UserError(f"TorBox is preparing this torrent ({float((item or {}).get('progress') or 0)*100:.0f}%). Open it again to check progress.")
    supported = {'.mp3','.flac','.aac','.m4a','.wav','.ogg','.opus','.alac','.aiff','.aif','.ape','.wma'}
    rows = [dict(id=json.dumps({'torrent_id':int(torrent_id),'file_id':int(f['id'])}),title=Path(f.get('short_name') or f.get('name','Audio')).name,artist='TorBox',kind='torbox',artwork='') for f in item.get('files',[]) if Path(f.get('short_name') or f.get('name','')).suffix.lower() in supported]
    if not rows: raise UserError('This torrent contains no supported audio files.')
    return rows


def torbox_stream(identity):
    item = json.loads(identity)
    url = torbox('GET','torrents/requestdl',params={'token':settings['torbox_token'],'torrent_id':int(item['torrent_id']),'file_id':int(item['file_id']),'zip_link':'false','torrent_file':'false','redirect':'false'})
    if not isinstance(url,str) or urlparse(url).scheme != 'https': raise UserError('TorBox did not return a secure audio stream.')
    return {'url':url,'headers':{}}


def nova_engines():
    bundled = Path(__file__).parent/'vendor/nova/engines'
    custom = root/'nova-engines'; custom.mkdir(exist_ok=True)
    return sorted({p.stem for d in (bundled,custom) for p in d.glob('*.py') if not p.stem.startswith('_')})


def nova_install(url):
    if urlparse(url).scheme != 'https': raise UserError('Use an HTTPS Nova Python plugin URL.')
    name = Path(urlparse(url).path).stem
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]{0,80}',name): raise UserError('Invalid Nova filename.')
    with Session().get(url,stream=True) as r:
        r.raise_for_status(); data = bytearray()
        for block in r.iter_content(65536):
            data.extend(block)
            if len(data)>2_000_000: raise UserError('Nova plugin is too large.')
    tree = ast.parse(data)
    if not any(isinstance(n,ast.ClassDef) and n.name==name for n in tree.body): raise UserError('A Nova engine must contain a class matching its filename.')
    dest = root/'nova-engines'; dest.mkdir(exist_ok=True)
    (dest/(name+'.py')).write_bytes(data)
    return name


def nova_remove(name):
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]{0,80}',name): raise UserError('Invalid engine name.')
    p = root/'nova-engines'/(name+'.py')
    if not p.exists(): raise UserError('Bundled engines cannot be uninstalled.')
    p.unlink(); return True


def nova_search(query, engines_json='[]'):
    import novaprinter
    query = query.strip()
    if not query: raise UserError('Enter a search query.')
    engine_names = json.loads(engines_json) or nova_engines()
    found, failures = [], 0
    for name in engine_names:
        if name not in nova_engines(): raise UserError('Select an installed search engine.')
        path = root/'nova-engines'/(name+'.py')
        if not path.exists(): path = Path(__file__).parent/'vendor/nova/engines'/(name+'.py')
        records = []
        previous = novaprinter.prettyPrinter
        novaprinter.prettyPrinter = records.append
        try:
            spec = importlib.util.spec_from_file_location('musik_nova_'+name,path)
            module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
            getattr(module,name)().search(quote(query),'all')
            for item in records:
                if urlparse(item.get('link','')).scheme not in ('magnet','https'): continue
                found.append(dict(id=json.dumps({'url':item['link']}),title=item.get('name','Torrent'),artist=f"{item.get('seeds',0)} seeds · {name}",kind='torrent',artwork=''))
        except Exception: failures += 1
        finally: novaprinter.prettyPrinter = previous
    if not found and failures: raise UserError('Search engines failed or timed out. Try another engine or a magnet link.')
    return list({t['id']:t for t in found}.values())


sys.path.insert(0,str(Path(__file__).parent/'vendor/nova'))
