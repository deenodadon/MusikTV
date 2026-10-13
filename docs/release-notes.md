Musik TV 0.2.2 playback recovery and UI performance update.

Temporary resolver connection failures now receive one retry with a short delay.
Native stream connection failures receive one fresh-resolution retry, preserving
playback position. Account/format/JavaScript resolution restrictions are not retried.
Completed failed background requests are removed even when their waiter was
cancelled, preventing a later Play from inheriting a stale failure.

Playback clock updates now affect only the progress display. Audio meter updates
invalidate only canvas drawing, rather than recomposing the player screen.
Queue warming resolves only the next two tracks after playback begins, replacing
up to 256 background requests. Stop cancels pending preparations. Library and
queue JSON serialization uses an ordered background writer instead of the UI thread.

Validation: Android APK assembly and lint pass; 22 Python and 12 Kotlin tests pass.
New coroutine tests exercise shared requests, a cancelled waiter followed by a
failed request and successful retry, and cancellation/reset. Recovery tests verify
that transport errors qualify for retries and provider restrictions do not.
Native instrumentation compiles but is unrun; the local-audio persistence test
now waits for the asynchronous write. TV frame times and audible playback recovery
have not been measured in this cloud environment. The user's successful second
play confirms that the previous version can play that upload on their TV, but
it does not establish the precise cause of the first connection failure.

Install over your existing app; the APK retains the previous debug signing identity.
No credentials are bundled. Provider restrictions can still prevent playback.
