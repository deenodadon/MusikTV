package tv.musik
import org.junit.Assert.*
import org.junit.Test
class QueueRulesTest {
    @Test fun repeatSongOnlyAffectsAutomaticAdvance() {
        assertEquals(1, QueueRules.next(1,3,1,1,true))
        assertEquals(2, QueueRules.next(1,3,1,1,false))
    }
    @Test fun endStopsUnlessQueueRepeatIsEnabled() {
        assertNull(QueueRules.next(2,3,1,0,true))
        assertEquals(0,QueueRules.next(2,3,1,2,true))
        assertEquals(2,QueueRules.next(0,3,-1,2,false))
    }
    @Test fun emptyQueueNeverAdvances() { assertNull(QueueRules.next(-1,0,1,2,true)) }
    @Test fun reorderingPreservesCurrentTrack() {
        assertEquals(2,QueueRules.movedCurrent(1,1,2))
        assertEquals(1,QueueRules.movedCurrent(2,1,2))
        assertEquals(0,QueueRules.movedCurrent(0,1,2))
    }
}
