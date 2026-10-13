Musik TV 0.2.5 TorBox QR account linking.

Torrents now offers Connect TorBox · QR code using TorBox's documented device
third-party authorization API. Scan the QR on your phone, sign in to TorBox and
approve Musik TV. The TV polls automatically at TorBox's interval, stores the
returned account token encrypted with Android Keystore and loads your torrents.
No app registration, OAuth client configuration or manual API key is required.

The QR includes the complete official HTTPS TorBox verification link. Manual
address/code entry is available beside it. Cancel, expiry, already-collected
codes and rejected approval have explicit handling. Disconnect preserves remote
torrents and clears the app's token/cache; manual API-key entry is retained as
an optional fallback. QR generation runs off the UI thread.

Validation: live TorBox API start and pending responses verified; 38 Python and
17 Kotlin tests pass, including QR encode/decode and device API/error fixtures.
Optimized APK assembly and release lint pass. Native instrumentation compiles
but is unrun. Approved-account success and physical TV/phone scanning remain
unverified; the cloud proxy blocks the verification webpage. No live account
credentials were supplied or logged.

Install over the existing app. The APK retains the original signing certificate,
release optimizations, light UI text and Google account sign-in functionality.
See docs/torbox-sign-in.md for use and validation details.
