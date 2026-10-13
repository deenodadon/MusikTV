"""Portable Musik catalog adapter. All calls run locally on the Android device.

Adapted from deenodadon/Musik service.py (GPL-3.0-or-later).
No desktop daemon, shell process, account cookie scraping, or remote backend.
"""
import json
import re
from urllib.parse import urlparse, parse_qs

_api = None


def set_solver(runtime):
    import android_jsc
    android_jsc.solver=runtime


def configure(data, directory):
    import advanced
    advanced.configure(data, directory)


def api():
    global _api
    import advanced
    if advanced.settings.get("cookies") or advanced.settings.get("oauth"):
        return advanced.authenticated_api()
    if _api is None:
        import requests
        from ytmusicapi import YTMusic
        class Session(requests.Session):
            def request(self, method, url, **kwargs):
                kwargs.setdefault("timeout", 20)
                return super().request(method, url, **kwargs)
        _api = YTMusic(requests_session=Session())
    return _api


def track(item, kind="song"):
    if not isinstance(item, dict) or item.get("isAvailable") is False:
        return None
    identity = item.get("videoId") if kind in ("song", "video") else item.get("browseId") or item.get("playlistId")
    if not identity:
        return None
    artists = item.get("artists") or []
    artist = ", ".join(a.get("name", "") for a in artists if isinstance(a, dict))
    thumbs = item.get("thumbnails") or []
    return dict(id=identity, title=item.get("title") or item.get("artist") or "Untitled",
                artist=artist or ("Artist" if kind == "artist" else "Unknown artist"),
                kind=kind, artwork=thumbs[-1].get("url", "") if thumbs else "")


def rows(items, kind="song"):
    result, seen = [], set()
    for item in items or []:
        t = track(item, kind)
        if t and t["id"] not in seen:
            result.append(t)
            seen.add(t["id"])
    return result


def video_id(value):
    parsed = urlparse(value)
    if parsed.hostname == "youtu.be":
        identity = parsed.path.strip("/").split("/")[0]
    elif parsed.hostname in ("youtube.com", "www.youtube.com", "music.youtube.com", "m.youtube.com"):
        identity = parse_qs(parsed.query).get("v", [""])[0]
        if not identity and parsed.path.startswith(("/shorts/", "/embed/", "/live/")):
            identity = parsed.path.split("/")[2]
    else:
        return None
    return identity if re.fullmatch(r"[A-Za-z0-9_-]{11}", identity) else None


def search(query, kind="song"):
    query = query.strip()
    if not query:
        return []
    if query.startswith(("https://", "http://")):
        identity = video_id(query)
        if not identity:
            raise ValueError("Use a YouTube video link, or search by title.")
        data = api().get_song(identity).get("videoDetails", {})
        return [dict(id=identity, title=data.get("title", "YouTube video"),
                     artist=data.get("author", "YouTube"), kind="video",
                     artwork=f"https://i.ytimg.com/vi/{identity}/hqdefault.jpg")]
    filters = {"song": "songs", "album": "albums", "artist": "artists", "video": "videos"}
    if kind not in filters:
        raise ValueError("Unknown search category")
    return rows(api().search(query, filter=filters[kind], limit=40), kind)


def browse(identity, kind):
    if kind == "album":
        return rows(api().get_album(identity).get("tracks"))
    if kind == "playlist":
        return rows(api().get_playlist(identity, limit=100).get("tracks"))
    if kind == "artist":
        data = api().get_artist(identity)
        return rows((data.get("songs") or {}).get("results")) + rows((data.get("albums") or {}).get("results"), "album")
    raise ValueError("This item cannot be browsed")


def radio(identity):
    return rows(api().get_watch_playlist(videoId=identity, radio=True, limit=25).get("tracks"))


def lyrics(identity):
    watch = api().get_watch_playlist(videoId=identity, limit=1)
    if not watch.get("lyrics"):
        return "Lyrics are not available for this track."
    return api().get_lyrics(watch["lyrics"]).get("lyrics") or "Lyrics are not available for this track."


class QuietLogger:
    def debug(self, *_): pass
    def warning(self, *_): pass
    def error(self, *_): pass


def resolve(identity):
    if not re.fullmatch(r"[A-Za-z0-9_-]{11}", identity):
        raise ValueError("Invalid YouTube video ID")
    from yt_dlp import YoutubeDL
    # Let upstream select its supported clients, including account-aware clients.
    # Forcing web/web_safari excludes its device client and can leave no formats
    # when browser streams require proof-of-origin tokens.
    options = dict(format="bestaudio/best[acodec!=none]", quiet=True, no_warnings=True,
                   logger=QuietLogger(), socket_timeout=20, noplaylist=True,
                   cachedir=False)
    import advanced
    with YoutubeDL(options) as ydl:
        if advanced.settings.get("cookies"):
            ydl.cookiejar = advanced.cookie_jar()
        info = ydl.extract_info(f"https://www.youtube.com/watch?v={identity}", download=False)
    url = info.get("url")
    if not url or not url.startswith("https://"):
        raise RuntimeError("YouTube did not provide a playable stream. Try another upload.")
    headers = {k: str(v) for k, v in (info.get("http_headers") or {}).items()
               if k.lower() in ("user-agent", "referer", "origin")}
    return {"url": url, "headers": headers}


def diagnostics():
    from ytmusicapi import YTMusic
    from yt_dlp import YoutubeDL
    return {"catalog_loaded": YTMusic is not None, "resolver_loaded": YoutubeDL is not None}


def call(operation, args_json="[]"):
    """Stable JSON boundary; deliberately do not return raw service errors."""
    import advanced
    operations = {name: getattr(advanced, name) for name in ("oauth_start", "oauth_finish", "parse_cookies", "account_playlists", "torbox_list", "torbox_files", "torbox_stream", "nova_engines", "nova_install", "nova_remove", "nova_search")}
    import plugins
    operations.update({"plugins_"+name: getattr(plugins,name) for name in ("registry","status","install","uninstall","enable","select","http")})
    operations.update(dict(diagnostics=diagnostics, search=search, browse=browse, radio=radio, lyrics=lyrics, resolve=resolve))
    try:
        if operation not in operations:
            raise ValueError("Unsupported operation")
        return json.dumps({"ok": True, "data": operations[operation](*json.loads(args_json))})
    except Exception as exc:
        text = str(exc).lower()
        if isinstance(exc, advanced.UserError):
            message = str(exc)
        elif any(word in text for word in ("age-restricted", "sign in", "confirm your age", "private video")):
            message = "This upload requires an eligible signed-in account. Refresh your eligible account session in Account; Google must authorize this upload."
        elif operation == "resolve" and any(word in text for word in ("challenge", "javascript", "webview")):
            message = "The TV could not solve YouTube’s JavaScript challenge. Check for an Android System WebView update, then retry. (YT-JS)"
        elif operation == "resolve" and any(word in text for word in ("po token", "proof of origin", "proof-of-origin")):
            message = "YouTube requires stream attestation that this TV cannot currently provide. Try another upload. (YT-ATTESTATION)"
        elif operation == "resolve" and "format" in text:
            message = "YouTube returned no playable audio formats for this upload. Try another upload or refresh your Account session. (YT-FORMAT)"
        elif any(word in text for word in ("403", "token")):
            message = ("YouTube denied access to this stream. Refresh your Account session or try another upload. (YT-ACCESS)" if operation == "resolve" else "YouTube denied this catalog request. Check your connection and try again later.")
        elif any(word in text for word in ("timeout", "timed out", "connection", "network", "resolve host")):
            message = "Could not reach YouTube. Check the TV’s connection and try again."
        elif isinstance(exc, ValueError):
            message = str(exc)
        else:
            message = "The music provider could not complete this request. Try again later."
        return json.dumps({"ok": False, "error": message})
