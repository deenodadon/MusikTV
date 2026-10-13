# Validation of 0.2.2

Passed in the current cloud workspace:

- Android debug APK assembly for arm64-v8a, armeabi-v7a, and x86_64.
- Android lint: zero errors (advisory platform/style warnings remain).
- 12 Kotlin tests: queue transitions, measured equalizer frequency response,
  transport-retry classification and shared-resolution cancellation/failure cleanup.
- 22 Python tests: provider mapping/error handling, TorBox audio selection/status,
  cookie-domain filtering, in-process Nova execution, archive traversal defense,
  npm version resolution, and retained EJS script integrity checks.
- Previously passed unchanged JavaScript host test: actual WASM TypeScript bundling, plugin lifecycle/settings,
  controlled native HTTP callbacks, provider search and unload.
- Android instrumentation test APK compilation.

The user reports that a Bon Jovi upload failed on the first attempt and played
on the second, on Android TV 14 without account sign-in. No raw provider error
or frame-time trace is available. The release fixes a demonstrated stale-failure
lifecycle bug and adds bounded transport recovery; it does not assert that this
was the exact cause of that first failure. It isolates playback-clock composition
and meter drawing, limits warm-up to two upcoming tracks, and moves JSON writes
off the UI thread. Physical-TV smoothness and automatic audible recovery remain
unmeasured. New coroutine tests reproduce cancelled-waiter/failing-work recovery.

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
