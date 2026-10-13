Musik TV is an independent Android adaptation of deenodadon/Musik.
Original source reviewed: https://github.com/deenodadon/Musik
Commit: 174ed3504d8efb348389b9796e40cff60b7e2347 (v0.9.2).
The catalog adapter and queue behavior are adapted from musik/service.py and
musik/core.py. This project is GPL-3.0-or-later; see LICENSE.
Unaffiliated with Google or YouTube. Third-party dependencies retain their licenses.
Additional adapters derive from musik/auth.py, musik/torrents.py, musik/nuclear.py,
and musik/nuclear_host.mjs at the same upstream commit. Vendored Nova sources retain
their original notices in app/src/main/python/vendor/nova/. esbuild-WASM 0.25.12 is
included under its MIT license, retained as app/src/main/assets/esbuild-LICENSE.txt.
yt-dlp EJS scripts retain their own package license and integrity checks.
Desktop browser scraping, mpv binaries, desktop daemons and native Node executables
are not included; Android provides replacements. Installed third-party providers
retain their individual licenses in their verified source releases.
