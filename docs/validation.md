# Validation of 0.2.5

Passed in the current cloud workspace:

- Android optimized release APK assembly for arm64-v8a, armeabi-v7a, and x86_64.
- Android lint: zero errors (advisory platform/style warnings remain).
- 17 Kotlin tests: queue transitions, measured equalizer frequency response,
  transport-retry classification, shared-resolution cancellation/failure cleanup,
  measured render-frame summaries, trusted Google verification URLs and polling intervals.
- 38 Python tests: provider mapping/error handling, TorBox audio selection/status,
  cookie-domain filtering, in-process Nova execution, archive traversal defense,
  npm version resolution, retained EJS script integrity checks, and Google
  device polling states/denial/expiry/partial-response validation.
- Previously passed unchanged JavaScript host test: actual WASM TypeScript bundling, plugin lifecycle/settings,
  controlled native HTTP callbacks, provider search and unload.
- Android instrumentation test APK compilation.

TorBox's live OpenAPI schema documents the device-start GET endpoint and token
POST JSON schema. A live start call returned the expected code/interval/expiry/
complete verification URL, and an unapproved token call returned HTTP 400 with
DEVICE_CODE_NOT_USED. Codes and tokens were not logged. The pairing UI uses this
contract and stores accepted tokens in Android Keystore-backed encrypted storage.
Unit tests cover the exact endpoint/body requests, trusted verification links,
pending/expiry/used/denied states and successful-token fixtures. A ZXing test
encodes the actual QR matrix and decodes it back to the exact pairing URL.

The cloud proxy permits api.torbox.app but rejects torbox.app CONNECT requests.
Consequently real user approval and its success-response shape are unverified.
The QR's physical TV appearance and phone-camera scanning remain unverified.
Native instrumentation compiles but is unrun. Existing optimized release and
light-text theme are retained.

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
