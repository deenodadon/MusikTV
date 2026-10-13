Musik TV 0.2.8 audio-only TorBox library.

My TorBox torrents now lists ready audio files directly instead of all torrent
folders. Paginated fresh account-list requests filter each torrent's files, hides video,
images, text and other non-audio files, and omits unfinished downloads. Tracks
retain both torrent and file IDs for playback and show their source torrent name
so identical filenames from different collections remain distinguishable.

The same audio filter is used when opening a magnet or torrent-search result.
File extensions are case-insensitive; supported audiobook .m4b and Ogg-audio .oga
extensions are included. Explicit video MIME metadata overrides an audio-looking
extension. Empty accounts and collections without ready audio show a clear empty
message. The audio-only list is also loaded immediately after TorBox linking.

Validation: optimized APK assembly, release lint and 53 Python/17 Kotlin tests
pass. New tests cover mixed audio/video/artwork/text, uppercase extensions, video
MIME overrides, duplicate filenames with distinct IDs, pending downloads, a single
request for a small account and correct paging beyond 1,000 torrents and empty accounts. Native instrumentation compiles but is
unrun. Real TorBox account listing and physical-TV playback remain unverified.

Install over the existing app and select My TorBox torrents. The signing identity,
QR linking, bundled Nova engines, light text and release optimizations are retained.
