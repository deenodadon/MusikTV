Musik TV 0.2.6 TorBox approved-token parser correction.

A physical-TV photo confirms that TorBox web approval succeeded but version 0.2.5
reported no usable token. Its parser accepted only a plain data string, data.token
or data.api_key. It discarded other explicit API-token names and envelopes.

The parser now accepts explicit API/access/auth token and API-key fields, including
camelCase, top-level and bounded credential envelopes, and normalizes bearer
prefixes. It does not treat device codes, session tokens, refresh tokens or arbitrary
response strings as API keys. Unknown success responses report only short schema
field names and types; response values are never included in error messages.

Use a fresh QR after updating: TorBox device tokens are collected once and the
previous code may already be consumed. Accepted tokens continue to be stored in
Android Keystore-backed encrypted storage on the TV.

Validation: 48 Python and 17 Kotlin tests pass, optimized APK assembly and release
lint pass. New regressions cover successful response variants, nested credentials
and safe unsupported-response diagnostics. Native instrumentation compiles but is
unrun. The exact approved response in the photo is unavailable, so the expanded
parser has not yet been confirmed against that user's approved response. The live
TorBox start/pending flow was verified in the preceding release; full approval
remains unverified in this cloud workspace.

Version 0.2.6 retains the previous signing certificate, UI contrast correction,
release optimizations and QR pairing interface.
