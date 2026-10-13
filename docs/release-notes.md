Musik TV 0.2.7 bundled Nova engine loading correction.

The APK includes solidtorrents and torrentscsv, but the earlier loader discovered
engines with filesystem globbing beside advanced.py. Chaquopy packages Python
source in APK archives; those paths are not ordinary directories on the TV, so
the built-ins could disappear from discovery and could not be loaded as files.

Bundled engine names are now explicit and their code loads through Python package
imports. Upstream helper modules are exposed under the names Nova engines expect,
without inserting an assumed extracted source directory on sys.path. Custom
installed engines still load from app-private files. The UI immediately shows
both built-in engines selected by default. Cached built-in modules bind a fresh
result collector for every query, preventing repeat searches from reusing old rows.

Validation: 50 Python and 17 Kotlin tests pass; optimized APK assembly and release
lint pass. An archive-only regression packs the real Python sources, imports the
built-ins from the ZIP in a fresh process and runs successive torrentscsv searches
against controlled provider JSON, checking separate magnet results. This directly
exercises archive loading and the vendored engine code, not a filesystem-only stub.
Native instrumentation compiles but remains unrun. Live search-site availability
and the corrected Chaquopy behavior on the physical TV remain unverified.

Install over the existing app, then open Torrents: solidtorrents and torrentscsv
should both appear selected. You do not need to install either engine manually.
The signing certificate, TorBox QR linking and release optimizations are retained.
