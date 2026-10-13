Standalone Android TV APK with Material 3 styling and remote navigation.

This update adds encrypted Google device OAuth/session import and account playlists,
TorBox audio streaming, standalone Nova engines and engine installation, a verified
Nuclear provider host using local WebAssembly bundling, provider selection/settings,
charts and discovery, native flat/bass/vocal equalizers, measured loudness history,
folder/USB scanning with LRC sidecars, automatic radio queues, expiring stream
preparation, queue-to-playlist saving, stop/reset, and a GitHub download/update link.
YouTube signature challenges use yt-dlp's verified EJS scripts inside a separate
Android WebView, rather than a desktop Node process.

Validation: APK assembly and Android lint pass; 17 Python and 7 Kotlin tests pass.
The plugin host test exercises the actual WASM bundler with TypeScript, lifecycle,
settings, native-transport callbacks, search, and unload. Device instrumentation
is compiled but has not run successfully in the cloud's software TV emulator.
Live provider playback still requires validation on a physical TV and appropriate
provider access. No Google or TorBox credentials are bundled.

Compatibility: Google device OAuth requires a configured TV/device client, or an
eligible imported YouTube session. Google can deny restricted streams or require
proof-of-origin attestation. Nuclear providers needing native desktop Node modules
or custom React settings widgets require further Android adapters. Such failures
are reported; this release does not claim universal plugin or YouTube compatibility.

This is a debug-signed sideload build. The published APK is the exact checksum-
verified artifact committed before the release tag, preserving its signing identity
for updates from this environment. It is not a Play Store production release.
