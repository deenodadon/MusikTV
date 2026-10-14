Musik TV 0.2.8 — audio-only TorBox browsing.

TorBox is optional. If you connect your own TorBox account, selecting **My TorBox
torrents** now shows only ready music and audio files from that account. Each user
supplies their own TorBox access; no TorBox account or subscription is included
with Musik TV.

The list hides videos, artwork, text files and other non-audio files, and omits
unfinished downloads. Tracks display their source torrent name, and account
listings are paginated so audio files beyond the first 1,000 torrents are included.
The same filter applies when opening a magnet or torrent-search result.

File extensions are case-insensitive, with audiobook .m4b and Ogg-audio .oga
support. Explicit video MIME metadata excludes files with audio-looking extensions.
Accounts without ready audio show a clear empty message.

Validation of the published APK: optimized assembly, release lint and 70 unit tests
passed (53 Python, 17 Kotlin). Native instrumentation compiled but was not run;
live account listing and physical-TV playback remain unverified. Automated account
tests use fixtures. Users connect their own accounts privately on their TVs.

Install over the existing app. QR linking, bundled Nova engines, light UI text,
release optimizations and the signing identity are retained.
