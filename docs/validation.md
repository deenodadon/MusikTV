# Validation of 0.2.4

Passed in the current cloud workspace:

- Android optimized release APK assembly for arm64-v8a, armeabi-v7a, and x86_64.
- Android lint: zero errors (advisory platform/style warnings remain).
- 16 Kotlin tests: queue transitions, measured equalizer frequency response,
  transport-retry classification, shared-resolution cancellation/failure cleanup,
  measured render-frame summaries, trusted Google verification URLs and polling intervals.
- 27 Python tests: provider mapping/error handling, TorBox audio selection/status,
  cookie-domain filtering, in-process Nova execution, archive traversal defense,
  npm version resolution, retained EJS script integrity checks, and Google
  device polling states/denial/expiry/partial-response validation.
- Previously passed unchanged JavaScript host test: actual WASM TypeScript bundling, plugin lifecycle/settings,
  controlled native HTTP callbacks, provider search and unload.
- Android instrumentation test APK compilation.

The user reports black labels against the dark-blue background and requests a
Google sign-in window. The root Material 3 Surface now supplies off-white default
text. Account has an OAuth setup dialog and a Google pairing-code dialog with
automatic polling and external official-browser approval. Google does not allow
embedded WebView password sign-in. The user has no registered OAuth client yet;
docs/google-sign-in.md covers registration and setup on the TV. There is no
configured client here, so actual Google approval cannot be validated.

The optimized release build is retained. Native text-layout regression compiles
but is unrun; it checks the actual default Material 3 Text layout color within
MusikTheme. Unit tests cover wait/slow-down states, denial, expiry, partial code
responses, Google-only HTTPS verification URLs and polling intervals. Release
APK metadata, signing identity, R8 bridge method preservation and baseline assets
are checked separately. These checks do not establish on-TV visual behavior.

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
