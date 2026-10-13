# Validation of 0.2.7

Passed in the current cloud workspace:

- Android optimized release APK assembly for arm64-v8a, armeabi-v7a, and x86_64.
- Android lint: zero errors (advisory platform/style warnings remain).
- 17 Kotlin tests: queue transitions, measured equalizer frequency response,
  transport-retry classification, shared-resolution cancellation/failure cleanup,
  measured render-frame summaries, trusted Google verification URLs and polling intervals.
- 50 Python tests: provider mapping/error handling, TorBox audio selection/status,
  cookie-domain filtering, in-process Nova execution, archive traversal defense,
  npm version resolution, retained EJS script integrity checks, and Google
  device polling states/denial/expiry/partial-response validation.
- Previously passed unchanged JavaScript host test: actual WASM TypeScript bundling, plugin lifecycle/settings,
  controlled native HTTP callbacks, provider search and unload.
- Android instrumentation test APK compilation.

The built-in Nova engines were shipped in the APK but discovered/loaded with
ordinary filesystem paths, which do not represent Chaquopy's packaged archives.
The loader now imports explicit built-in modules and lists the two default names
independently of filesystem discovery. The UI shows both selected immediately.
A fresh-process test imports the actual sources from a ZIP archive, loads both
built-ins, and executes torrentscsv twice against controlled provider JSON. It
checks that magnet results are captured independently across queries. A second
regression lists defaults with a synthetic non-filesystem Android source path.

Custom-engine file loading remains covered by the existing in-process Nova test.
Package contents, release metadata, signing identity and lint are checked. Actual
on-TV Chaquopy execution and live provider site responses remain unverified.

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
