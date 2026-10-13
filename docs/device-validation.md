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

No Google-account login, Nuclear executable plugins, TorBox, or desktop equalizer
should be presented as implemented. They are documented limitations of this build.

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
