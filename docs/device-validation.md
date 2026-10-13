# Device acceptance checks

Build/unit/lint checks do not establish TV-device playback readiness. Run these on
an Android TV emulator or physical TV before distributing a release.

1. Install the APK and launch from the TV launcher. Confirm landscape layout and
   banner appear. Navigate every section using only D-pad, OK, and Back. Confirm
   all cards, dialogs, seek buttons, and offscreen horizontal controls are reachable.
2. Search a song, switch among Songs / Albums / Artists / Videos, open an album and
   artist, return with Back. Disconnect the network and verify an actionable error.
3. Play an available public upload. Check actual audible playback, pause/resume,
   buffering/errors, seek, volume, speed, lyrics, and radio collection loading.
   Record provider denials separately from app errors.
4. Add at least three songs to queue; select the second, reorder it, remove another,
   then test manual Next/Previous and automatic completion. Repeat Song must keep
   automatic completion on the song while manual Next still advances. Repeat Queue
   must wrap. Shuffle must select another track when multiple tracks are queued.
5. Favorite a song; create a playlist and add/remove songs. Restart the process and
   verify favorites, history, playlists, queue, repeat/shuffle, volume, and speed.
6. Import a real MP3 and FLAC via Android’s file picker. Play them with Internet
   disconnected. Restart and check persistent URI access. Unplug USB and confirm
   unavailable files produce an error. Removing an entry must preserve the file.
7. Press Home while playing. Verify audio continues, the system media controls can
   pause/skip, and resuming the app preserves the queue. Check audio focus interruption
   and headphone disconnection behavior. Stop playback before uninstalling.

8. Configure your own Google TV/device OAuth client, approve the displayed code,
   load account playlists, sign out, and confirm imported sessions are removed.
   Separately check cookie import and eligible restricted playback; OAuth catalog
   access alone is not proof of stream eligibility.
9. Configure TorBox, open a known magnet, check preparation/ready status, filter
   audio files and stream them. Confirm disconnect/reset preserve remote torrents.
10. Install a trusted Nuclear provider, verify lifecycle and provider selection,
    edit settings, search/browse/play/lyrics, then disable and uninstall. Confirm
    unsupported desktop dependencies produce an error rather than fake success.
11. Check flat/bass/vocal response and actual loudness bars with audible music.
    Import a music folder with LRC sidecars and check local lyrics offline.
12. Reset after reviewing the confirmation. Verify app data/credentials/plugins
    are cleared while original audio files and remote torrents remain intact.

Native Node-only dependencies, custom React settings widgets and proof-of-origin
attestation are not claimed universally supported. Record provider restrictions
separately from defects in the Android implementation.

## Automated native smoke test

An instrumentation test imports both on-device provider libraries, opens Library,
plays an actual generated PCM WAV through ExoPlayer, verifies time advances while
the Activity is in the background, persists a favorite, and pauses from the GUI.
It needs a booted Android TV device and does not require YouTube access.

```sh
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r tv.musik.test/androidx.test.runner.AndroidJUnitRunner
```

This verifies decoding and the UI/service integration; audible output and real
provider streaming still need physical-device checks.
