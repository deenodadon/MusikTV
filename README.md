# Musik TV

A standalone Android TV adaptation of [Musik](https://github.com/deenodadon/Musik),
with a native Kotlin / Compose for TV interface, Material 3 colors and typography,
and Media3 audio playback. Catalog search and stream resolution run **on the TV**
in an embedded Python runtime. No desktop companion server is used.

## What you can do

- Search YouTube Music songs, albums, artists, and videos; open artist and album pages.
- Paste a YouTube video link into search.
- Play, pause, seek, skip, shuffle, repeat a song or queue, adjust volume and speed.
- Save favorites, recent listening, and named playlists locally on the TV.
- Add, remove, and reorder queue tracks; play collections in order.
- Open radio collections and lyrics for public YouTube songs.
- Import multiple local audio files from a USB drive or Android document provider.
- Continue playback with the screen closed using Android’s media session and notification.

Use the D-pad to focus controls, OK to select, and Back to return. Media remote keys
work across screens. Search uses the TV’s on-screen keyboard. Focused controls use
high-contrast lime highlighting and TV focus scaling. No touch input is required.

## Build and install

Requires JDK 17 or 21 **with javac**, Android SDK 35 / build-tools 35.0.0, Python
3.11 or newer for the build-time package installer, and Internet access to Google
Maven, Maven Central, PyPI, and chaquo.com. The APK embeds Python 3.11 and requires
Android 7.0 / API 24 or newer. Supported ABIs: arm64-v8a, armeabi-v7a, x86_64.

```sh
# Set ANDROID_HOME to your SDK, or put sdk.dir in ignored local.properties.
./gradlew assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n tv.musik/.MainActivity
```

The debug APK is for sideloading and testing. Create your own signing configuration
before distributing a release; no signing keys are stored in this project.

Python adapter tests:

```sh
python -m venv .venv
.venv/bin/pip install -r tests/requirements.txt
.venv/bin/python -m pytest -q
```

## Provider and platform limits

Public browsing is supported without Google sign-in. Account libraries and
restricted uploads are not supported in this release. YouTube can reject streams
or require proof-of-origin / JavaScript challenges that the original desktop
Node.js helper handles. The Android resolver currently uses Safari HLS; playback
availability is provider-dependent, and failures are shown in the interface.
There is no fabricated catalog, playback fallback, or remote resolver.

Desktop Nuclear executable plugins, browser-cookie import, TorBox/torrent search,
and the mpv equalizer are not included. Native replacements would be separate
features. The artwork fallback is decorative; it is not a measured visualizer.

Local import uses persistent, scoped Android document permissions. Some TV devices
need a Storage Access Framework file provider installed to browse USB storage.
Removing a library entry does not delete the original file. The app requests no
broad storage access. Favorites, history, queue, and playlists survive restarts;
playback does not auto-start or restore the old elapsed position after a process
restart. There are no analytics or cloud storage services.

See [architecture](docs/architecture.md) and [device validation](docs/device-validation.md).

## Source and license

GPL-3.0-or-later. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md) for upstream
attribution. Unofficial and unaffiliated with Google or YouTube.
