package tv.musik

import kotlin.math.ceil

/** Bounded, local-only render samples; no per-frame Compose state updates. */
class FrameSamples {
    private val durations=ArrayList<Long>(1024)
    private var slow=0
    @Synchronized fun add(duration:Long,budget:Long) {
        if(duration<0 || budget<=0 || durations.size>=4096) return
        durations.add(duration)
        if(duration>budget) slow++
    }
    @Synchronized fun summary():String {
        if(durations.isEmpty()) return "No frames recorded. Start again and move between buttons or scroll."
        val sorted=durations.sorted()
        val p95=sorted[(ceil(sorted.size*.95).toInt()-1).coerceAtLeast(0)]/1_000_000.0
        return String.format(java.util.Locale.US,"%d frames · %.1f%% slow · 95%% rendered within %.1f ms",durations.size,slow*100.0/durations.size,p95)
    }
}
