# Android architecture

The app is standalone. No terminal daemon, mpv process, companion computer, or
Node executable runs on the TV.

- `MusikUi.kt` / `AdvancedUi.kt`: Material 3 styling with Compose for TV focus,
  remote navigation, catalog and library screens, account/device authorization,
  Nova/TorBox, Nuclear providers/settings, reset and update controls.
- `PlaybackService` / `PlaybackEngine`: native Media3 foreground playback/session,
  persisted queue settings, shared expiring stream preparation, radio, repeat,
  shuffle, audio focus and interruption handling.
- `MusikAudioProcessor`: actual decoded PCM loudness measurement and native RBJ
  biquad flat/bass/vocal filtering, with separate filter state per channel.
- `LibraryStore`: app-private metadata and stable playlist item IDs. Imported
  audio/LRC files use Android's scoped, persistent document/tree URI grants.
- `SecureVault`: Android Keystore AES-GCM encryption for session data, OAuth
  credentials/tokens, TorBox keys, and provider settings. No credentials are
  bundled, logged, backed up, or placed in the repository.
- `Catalog` / `catalog.py` / `advanced.py`: serialized on-device Python catalog,
  authenticated library, device OAuth, explicit session import, TorBox endpoints,
  and in-process Nova engines. HTTP errors omit response bodies and credentials.
- `android_jsc.py` / `ChallengeSolver`: yt-dlp's verified EJS code executes in a
  separate Android WebView, with only a result callback. It has no provider/vault
  bridge, file access, storage access, or remote-resource loading. Upstream solver
  version/hash checks remain enabled. This handles signature challenges, not
  proof-of-origin attestation or access eligibility.
- `plugins.py` / `PluginHost` / `plugin-host.js`: registry releases require
  GitHub SHA-256 digests; npm tarballs require official registry HTTPS/SRI. Archive
  traversal, links, oversized content, and undeclared desktop dependencies are
  rejected. Releases, dependency sources and licenses remain in private storage.
  esbuild's pinned, verified WASM/browser assets bundle JS/TS locally in WebView.
  Provider HTTP/settings and player/library calls cross explicit native bridges.

Provider plugins execute with the user's trust and their declared app capabilities.
They are not a security sandbox against malicious installed plugin code. The UI asks
for trust before installation. Session credentials remain outside plugin source files.
Custom desktop React widgets and native Node imports need Android adapters and are
reported as unsupported. Plugin lifecycle/search/bundling tests use a representative
provider with controlled responses; they do not prove all registry plugins work.

Saving configuration does not publish the environment. Pushing source/APKs does not
prove GitHub Actions has executed. Tagged releases publish the exact committed APK
rather than rebuilding it with a different ephemeral debug signing key.
