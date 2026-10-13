# Connect TorBox with a QR code

In Musik TV 0.2.5 or later:

1. Open Torrents and select Connect TorBox · QR code.
2. Scan the QR with your phone's camera. It opens TorBox's official verification
   page with the pairing code already filled in.
3. Sign in to TorBox if needed and approve Musik TV. Keep the QR dialog open on
   the TV until approval finishes.
4. The TV automatically collects the account token, stores it encrypted with
   Android Keystore, closes the dialog and loads your TorBox torrents.

No OAuth app registration, client secret or manually copied API key is required.
The phone must approve the connection; scanning alone does not grant access.
If scanning fails, visit https://torbox.app/oauth/device and enter the code shown
beside the QR. Expired codes require a fresh QR. Cancel stops polling. Disconnect
removes the saved TV token and preserves remote torrents. API-key entry remains
available as an optional fallback under Use API key instead.

## API contract and validation

Official contract: https://api.torbox.app/openapi.json

- GET /v1/api/user/auth/device/start?app=Musik%20TV issues the device code, user
  code, polling interval, expiry and complete verification URL.
- POST /v1/api/user/auth/device/token accepts JSON containing device_code.
- DEVICE_CODE_NOT_USED is a waiting state; ITEM_NOT_FOUND indicates expiry and
  DEVICE_CODE_USED indicates the token has already been collected.

The live API start and pending responses were verified from the cloud workspace
without logging pairing codes or tokens. Unit tests decode the actual QR matrix
back into its URL and verify endpoint requests, pending/expiry/used errors,
trusted HTTPS verification links and successful-token response fixtures.
Real account approval, the success response shape on an approved account and
physical-phone/TV QR rendering remain unverified. TorBox's verification webpage
is blocked by this cloud proxy although the device API is reachable.

## Approved but no token in version 0.2.5

Update to 0.2.6 and generate a fresh QR code. The earlier parser accepted too few
credential field names. The updated parser recognizes explicit API credentials in
more response envelopes. Device codes are single-use after token collection, so
reusing a previously approved/collected code cannot recover a discarded token.

If the updated parser still rejects a success response, the on-screen error includes
only response field names/types. Share that text for diagnosis; never share your API
key or raw response values. Acceptance of the actual previously approved response
remains unverified until tested on the TV.
