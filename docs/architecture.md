# Architecture

`MusikUi.kt` renders a 10-foot layout with TV focusable buttons and cards, Material
3 text input/dialogs, explicit loading/error/empty states, and D-pad accessible
playback controls. Search and browsing use an Android ViewModel. Stale catalog
requests and stale stream resolutions cannot replace more recent selections.

`Repository.kt` serializes Python calls on Dispatchers.IO through Chaquopy. The
catalog module is packaged inside the APK; requests go directly to YouTube.
`catalog.py` adapts the ytmusicapi catalog mapping from upstream Musik and uses
its yt-dlp dependency for public stream resolution. It validates video IDs and
removes raw provider error bodies from user-facing responses. Streaming headers
are passed to Media3, never saved in the library. Resolved stream URLs are not
persisted because they expire.

`PlaybackService` owns ExoPlayer and an Android MediaSession. A foreground media
notification is managed by Media3 when playback starts. Audio focus, becoming-noisy
handling, and standard trusted controller restrictions are enabled. The in-process
PlaybackEngine owns queue state and resolves each selected item. StateFlow feeds
the UI; closing the Activity does not destroy playback. The controller connection
binds the session on entry and releases on Activity destruction.

`LibraryStore` saves library and queue metadata to app-private SharedPreferences.
Android’s asynchronous apply updates in-memory state immediately and serializes
the preference writes. URI grants persist for imported local audio. No cookies,
Google credentials, browser profiles, desktop processes, or API keys are needed.

## Deliberate differences from desktop Musik

Native ExoPlayer replaces mpv. Android MediaSessionService replaces the terminal
daemon. Local playlists replace account library management. Android document
providers replace recursive unrestricted filesystem scanning. Arbitrary desktop
Python/Node executable plugins are excluded. The standalone build does not inherit
upstream desktop installation/update scripts.
