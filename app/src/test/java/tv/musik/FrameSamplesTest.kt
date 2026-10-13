package tv.musik

import org.junit.Assert.*
import org.junit.Test

class FrameSamplesTest {
    @Test fun countsFramesThatExceedTheirActualDeadline() {
        val samples=FrameSamples()
        repeat(19) { samples.add(10_000_000,16_666_666) }
        samples.add(40_000_000,16_666_666)
        assertEquals("20 frames · 5.0% slow · 95% rendered within 10.0 ms",samples.summary())
    }
    @Test fun invalidOrMissingMetricsDoNotProduceFakePerformanceNumbers() {
        val samples=FrameSamples()
        samples.add(-1,16_666_666);samples.add(5_000_000,0)
        assertTrue(samples.summary().startsWith("No frames recorded"))
    }
}
