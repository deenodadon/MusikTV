Musik TV 0.2.4 text contrast and Google account sign-in update.

The dark theme now supplies a light default content color through its root
Material 3 Surface. Previously, labels without an explicit text color inherited
black despite the dark palette. Normal text is off-white; focused lime buttons
retain dark text for contrast. The duplicate root background fill was removed.

Account now offers Sign in with Google and a first-use OAuth setup dialog.
A pairing-code dialog can open Google's official verification page in an external
browser, with a phone alternative for TVs without a browser. It polls automatically
at Google's requested interval, backs off on slow_down, stores accepted tokens
encrypted on the TV and loads account playlists. Denied/expired codes and cancelled
polling have distinct handling. Google password forms are not embedded in a WebView.
Malformed responses missing the user code are rejected before UI rendering.

A Google OAuth client is required; none is bundled or borrowed. Complete the
registration steps in docs/google-sign-in.md and enter its ID/secret on the TV.
The publisher would need its own Google-approved client for zero-configuration
public sign-in. Existing cookie import remains under Other sign-in options.

Validation: optimized APK assembly, release lint, 27 Python and 16 Kotlin tests
pass. Tests cover polling wait/denial/expiry, partial responses, trusted Google
verification URLs and polling intervals. A native default-text-color regression
compiles but is unrun. End-to-end Google approval and TV visual rendering are
unverified; there is no configured OAuth client in this cloud environment.

Install over the existing app; version 0.2.4 retains the earlier signing certificate
and release optimizations. No Google passwords, cookies or client secrets are
bundled. Provider restrictions can still affect account access and playback.
