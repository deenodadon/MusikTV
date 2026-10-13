Musik TV 0.2.1 playback compatibility update.

Removes the browser-only YouTube client override, restoring yt-dlp’s device-client
fallback for unsigned playback and its account-aware choices for imported sessions.
The Android WebView signature solver and upstream integrity checks remain enabled.
Errors now distinguish missing formats, access denial, JavaScript challenges, and
stream attestation without exposing cookies or signed URLs.

Validation: Android APK assembly, lint and 7 Kotlin tests pass; 22 Python tests pass.
An integration regression checks actual yt-dlp client selection with the Android
solver registered. The WebView bridge instrumentation test compiles but is unrun.
The user's Android TV 14 photo confirms the prior GUI runs but playback fails.
Live YouTube playback cannot be verified through this cloud proxy (CONNECT 403),
so this update is a targeted compatibility correction, not confirmed on-TV success.

Install over the existing app to preserve settings. The APK uses the same debug
signing identity as previous releases. No Google or TorBox credentials are bundled.
Provider restrictions and proof-of-origin attestation can still prevent playback.
