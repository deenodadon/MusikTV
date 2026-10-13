# Musik TV

A standalone Android TV port of [Musik](https://github.com/deenodadon/Musik), with
native Kotlin / Compose for TV UI, Material 3 styling, Media3 playback, and an
embedded Python runtime. It runs on the TV without a computer or companion server.

## Download and install

[Download the latest APK](https://github.com/deenodadon/MusikTV/raw/refs/heads/main/downloads/MusikTV-latest.apk)
· [APK directory](https://github.com/deenodadon/MusikTV/tree/main/downloads)
· [Releases](https://github.com/deenodadon/MusikTV/releases)

If this repository is private, sign in to GitHub before downloading. Allow installation
from your chosen downloader/file manager in Android TV settings, then open the APK.
Do not unzip it. Debug builds are for sideloading and testing. No signing keys or
provider credentials are stored in this repository.

Use the D-pad to focus controls, OK to select, and Back to return. Media keys work
throughout the app. Focus is shown with lime highlighting and TV focus scaling.
The sidebar scrolls to make every section reachable on shorter displays.

## Features

| Desktop Musik feature | Android TV implementation |
| --- | --- |
| Songs / albums / artists / videos | On-device catalog search, exact YouTube links, collection browsing |
| Radio / ordered collections | Automatic radio after song selection, explicit radio, Play all |
| Queue | Add, remove, reorder, clear, skip, shuffle, repeat song/queue, save as playlist |
| Favorites / history / playlists | Persistent private library, collection favorites, named playlists |
| Local audio | Multi-file or recursive folder/USB import using scoped Android document access |
| Lyrics | YouTube lyrics, Nuclear lyrics providers, matching folder-imported LRC sidecars |
| Playback controls | Play/pause/stop/seek, volume, speed, remote media keys |
| Equalizer / visualizer | Native PCM flat/bass/vocal filtering and measured RMS loudness history |
| Background daemon | Native foreground MediaSessionService and system media controls |
| Queue preparation | Shared expiring stream cache and sequential queue preparation |
| Browser account access | Google device OAuth or encrypted import of your own YouTube session |
| Account playlists | Authenticated YouTube library browsing |
| TorBox / torrent audio | Search/open magnets, preparation status, audio-file browsing, streaming |
| Nova engines | Bundled Solid Torrents / TorrentsCSV, trusted HTTPS engine install/removal |
| Nuclear providers | Verified releases, verified npm packages, local WASM bundling, lifecycle and provider APIs |
| Provider configuration | Select metadata/streaming/lyrics/dashboard/playlists/discovery providers, encrypted settings |
| Charts / discovery | Dashboard collections, recommendation providers, playlist URL providers |
| Update / reset | GitHub APK directory link; confirmed reset preserving music files and remote torrents |

### Accounts and provider setup

**Account:** Enter your Google TV/device OAuth client ID and secret (YouTube Data API
must be enabled), request a code, approve it on Google's displayed page on your phone,
and check authorization. Alternatively import a Netscape cookie export from your own
eligible YouTube session. Android Keystore encrypts session data, keys, and settings.
The app never requests your Google password. OAuth authorizes catalog/library access;
restricted stream playback can still require an eligible browser session or attestation.

**Torrents:** Save your own TorBox API key, choose Nova engines, and search or paste a
magnet / direct HTTPS .torrent URL. Open a result to inspect ready audio files, then
play or queue them. Uncached items show preparation status; open them again to refresh.
Audio collections are streamed rather than downloaded to the TV. Uninstalling engines,
signing out, or resetting the app does not delete remote TorBox torrents.

**Plugins:** Install only trusted code from the Nuclear registry. GitHub release SHA-256
and npm SRI checks remain enabled; npm install scripts never run. Providers execute in
Android WebView after local esbuild/WASM bundling. Select providers and configure their
settings in Plugins. Charts and playlist URLs open through the chosen providers.

### Compatibility boundaries

A desktop executable cannot simply run on Android. Provider plugins using native
Node-only modules or custom React settings widgets need additional Android adapters;
the app reports failures instead of claiming success. The core provider, HTTP, settings,
search, playback, queue, library, lyrics, dashboard, and recommendation bridges are native.
The included JavaScript-host tests validate bundling and a representative provider, not
all registry plugins or live services. Some advanced plugin APIs still require adapters.

Signature challenges use verified yt-dlp EJS scripts in a separate credential-isolated
Android WebView. Keep Android System WebView updated. YouTube can still reject streams,
require proof-of-origin tokens, or deny account/region eligibility. No credentials,
subscription, guaranteed access, or fabricated playback fallback are bundled.

SAF USB/folder access depends on the TV's document provider; install a compatible file
manager if the TV has no picker. Reset and library removal preserve original audio files.
Queue/metadata/settings survive restarts, but playback does not automatically start or
restore old elapsed position after a process restart. There are no analytics services.

## Build and test

Requires JDK 17/21 with javac, Android SDK 35 / build-tools 35.0.0, Python 3.11+ for
build-time pip, and Node 24 for the JavaScript host test. Node is not needed on the TV.
Minimum Android API 24. APK ABIs: arm64-v8a, armeabi-v7a, x86_64.

```sh
# Set ANDROID_HOME, or sdk.dir in ignored local.properties.
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
python -m venv .venv
.venv/bin/pip install -r tests/requirements.txt
.venv/bin/python -m pytest -q
node tests/plugin-host.test.mjs
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n tv.musik/.MainActivity
```

In the prepared cloud snapshot, `python scripts/cloud-build.py` selects retained tools,
writable caches, the network proxy, and its verified CA trust store. Build checks do not
establish real-TV readiness. See [validation](docs/validation.md),
[device checks](docs/device-validation.md), and [architecture](docs/architecture.md).

GPL-3.0-or-later. [LICENSE](LICENSE) · [Attribution](NOTICE.md).
Unofficial and unaffiliated with Google, YouTube, TorBox, or Nuclear.
