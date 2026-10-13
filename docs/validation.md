# Validation in the cloud workspace

Verified for the current source:

- Debug APK assembled for arm64-v8a, armeabi-v7a, and x86_64.
- Android lint completed with 0 errors and 8 advisory warnings.
- Kotlin queue rules: 4 tests passed, 0 skipped, 0 failures.
- Portable Python catalog/resolver adapter: 8 tests passed, 0 failures.
- Android instrumentation test APK compiled successfully.
- Saved environment install commands were executed successfully from MusikTV.

Not verified:

- Native GUI/playback instrumentation: unrun. The software Android TV emulator
  booted but remained ADB-unauthorized and did not expose a usable rendered display.
  The cloud machine has no /dev/kvm. These are test-environment limitations; they
  do not establish that the application behaves correctly on a TV.
- Live YouTube catalog and streaming: unverified. Requests to music.youtube.com
  were denied by the cloud egress proxy with CONNECT HTTP 403, before reaching
  the provider. Required domains have been saved in the environment draft.
- Physical remote navigation, USB provider compatibility, actual audible output,
  and system media notification behavior need the device checks documented in
  device-validation.md.
- The GitHub Actions workflow has been written but has not run on GitHub.

This is an initial native port, not complete desktop feature parity. Account
libraries, desktop executable plugins, TorBox/torrent search, and the mpv
filter/equalizer are not implemented. See README.md for runtime provider limits.
