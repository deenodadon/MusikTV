# Set up Google sign-in for Musik TV

Musik TV uses Google's OAuth flow for TVs and limited-input devices. A sign-in
dialog shows a short pairing code and can open Google's official verification
page in a browser. Google does not allow password sign-in in embedded WebViews.
If your TV has no browser, approve the displayed code on your phone instead.

You need to register an OAuth client once. Google account passwords are entered
only on Google's own page. Keep the client secret out of chat and source control.

## Register your Google OAuth app

Use your phone or computer for Google Cloud setup:

1. Open [Google Cloud Console](https://console.cloud.google.com/) with the Google
   account that will own the app's configuration. Create or select a project.
2. In **APIs & Services → Library**, enable **YouTube Data API v3** for that project.
3. Open **Google Auth Platform** (or **OAuth consent screen** in APIs & Services).
   Complete Branding with an app name, support email and developer contact email.
4. In Audience, choose External if this is your personal Google account. Keep the
   app in Testing for personal use and add the exact account you will sign in with
   under Test users. Use Internal only for a qualifying Google Workspace project.
5. In Data Access, add `https://www.googleapis.com/auth/youtube`, the scope used by
   the current YouTube Music OAuth adapter. Google will show its permissions when
   you approve access; review them before approving.
6. In Clients, choose **Create client → TVs and Limited Input devices**. This is
   the required client type for the pairing-code flow; Android and Web application
   clients use different flows. Name the client Musik TV.
7. Copy the resulting **client ID** and **client secret**. You do not need an
   Android package registration, redirect URI or signing-certificate fingerprint
   for this TV/device flow. Store these details privately.

Google's documentation:
[OAuth 2.0 for TV and limited-input devices](https://developers.google.com/identity/protocols/oauth2/limited-input-device).
The project's publishing status and Google policies determine who may sign in.

## Sign in on your TV

1. Install Musik TV 0.2.4 or later. Settings should show the actual version and
   Optimized build type.
2. Open **Account → Sign in with Google**. On first use, the setup dialog asks for
   your client ID and secret. Enter them on the TV, then choose **Save and sign in**.
   They remain encrypted with Android Keystore in the app's private storage.
3. The Google sign-in dialog shows the verification address and pairing code.
   Choose **Open Google sign-in** if a browser is available, or visit the displayed
   address on your phone. The fallback address is https://www.google.com/device.
4. Enter the pairing code on Google's page, choose your account and approve access.
   Return to Musik TV if you opened a separate browser. The app checks automatically
   at Google's requested polling interval and loads your playlists after approval.
5. If the code expires or approval is denied, start sign-in again. Cancel dismisses
   the pending sign-in; Sign out removes the saved account session from the TV.

## If Google refuses sign-in

- Check that YouTube Data API v3 is enabled in the same project as the OAuth client.
- Check that the client type is TVs and Limited Input devices and both copied
  values belong to that client.
- While the app is in Testing, add the signing-in account to Test users. Testing
  refresh tokens can expire after seven days; reconnect when needed. Publishing
  for other users can require Google's consent-screen verification.
- If your browser shows an unverified-app notice, proceed only if you recognize
  your own project and its requested permissions.
- Check the TV's network connection. Setup does not supply connectivity or override
  restrictions applied by Google or YouTube.

OAuth connects the YouTube Music catalog and playlists. It does not automatically
create browser cookies for uploads requiring an eligible YouTube browser session.
The existing cookie-import option remains under Other sign-in options.

No working OAuth client is bundled in the APK. Registering a publisher-owned,
Google-approved client would be needed to offer sign-in without user configuration
for a broadly distributed public app.
