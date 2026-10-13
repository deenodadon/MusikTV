# Validation of 0.2.3

Passed in the current cloud workspace:

- Android optimized release APK assembly for arm64-v8a, armeabi-v7a, and x86_64.
- Android lint: zero errors (advisory platform/style warnings remain).
- 14 Kotlin tests: queue transitions, measured equalizer frequency response,
  transport-retry classification, shared-resolution cancellation/failure cleanup,
  and measured render-frame summaries.
- 22 Python tests: provider mapping/error handling, TorBox audio selection/status,
  cookie-domain filtering, in-process Nova execution, archive traversal defense,
  npm version resolution, and retained EJS script integrity checks.
- Previously passed unchanged JavaScript host test: actual WASM TypeScript bundling, plugin lifecycle/settings,
  controlled native HTTP callbacks, provider search and unload.
- Android instrumentation test APK compilation.

The user reports broad navigation/scrolling lag on Onn 4K Pro, Cortex-A55,
Mali-G31 MP2 and 3GB RAM, including the 0.2.2 build. Earlier distributed APKs
were debug builds. Distribution now uses R8-optimized non-debuggable release
output, disables focus zoom and caps artwork decode targets. Settings correctly
reports the installed version/build type and provides an optional 15-second
on-TV render measurement. Build output and size reduction are evidence of
optimization, not evidence of smooth physical-TV navigation.

Artifact verification confirms versionCode 5/versionName 0.2.3, no debuggable
manifest flag, retained Python/JavaScript method names in R8 mapping, packaged
baseline profile assets, and the original update-compatible signing certificate.
Uncompressed DEX decreased from 63,865,884 bytes in 0.2.2 to 3,809,852 bytes.
This size comparison is not a frame-rate measurement. Checksum files now use
basenames, matching the release workflow's downloads working directory.

Not verified:

- Physical TV GUI, audible output, background notifications, real USB providers,
  Android Keystore/runtime WebView integration, and the native instrumentation
  workflow. The cloud software TV emulator did not provide a usable authorized
  debug/display connection. Native instrumentation is compiled but unrun.
- Live YouTube, Google OAuth, TorBox, torrent search sites, and registry plugin
  compatibility. Provider access is constrained by the cloud proxy; authentication
  and eligibility require the user's own credentials on their TV.
- GitHub Actions execution. Workflow files are supplied; publishing a tag triggers
  release automation, but local validation is not evidence of runner success.

The plugin test uses Node as the host-test engine for the same browser host and
WASM bundler; it does not run a real Android WebView. The Android EJS adapter keeps
upstream checks enabled but live YouTube challenges have not been verified on a TV.
Proof-of-origin attestation, custom desktop React widgets and native Node-only
plugin APIs remain compatibility boundaries, not claimed working features.
