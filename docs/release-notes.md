Musik TV 0.2.3 optimized TV build.

The distributed APK now uses the non-debuggable release build with R8 code
optimization, obfuscation, resource shrinking and packaged dependency baseline
profiles. Earlier downloads were debug builds and were unsuitable for assessing
Compose performance on the Onn 4K Pro Cortex-A55/Mali-G31 hardware.

D-pad focus retains the lime highlight without button zoom animation. Artwork
requests use explicit decode targets (384px cards, 768px player, 96px mini player),
with remembered requests and no image crossfade. Artwork no longer draws a
placeholder vector and gradient behind every loaded image.

Settings shows the real installed version and Optimized/Development build status.
Measure responsiveness records app rendering locally for 15 seconds while the
user navigates and scrolls, then reports slow frames and the 95th percentile
render duration. It runs only on request and sends no telemetry. These metrics
do not measure provider latency or complete remote-input latency.

Validation: optimized APK assembly, release lint, and 14 Kotlin/22 Python tests
pass. Debug instrumentation compiles but remains unrun. R8 output is checked for
retained Python/JavaScript bridge entry points and baseline profile assets.
Physical-TV frame times and interoperability in the minified APK remain unverified.
No measured on-TV speedup is claimed from build results alone.

Install over the existing app to preserve data. This optimized APK retains the
previous debug signing certificate for compatibility; it is not a Play Store
production package. No credentials are bundled.
