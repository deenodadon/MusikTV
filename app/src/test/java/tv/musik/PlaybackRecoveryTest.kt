package tv.musik

import org.junit.Assert.*
import org.junit.Test

class PlaybackRecoveryTest {
    @Test fun temporaryConnectionErrorsCanRecover() {
        assertTrue(PlaybackRecovery.transientResolution("Could not reach YouTube. Check the TV’s connection and try again."))
        assertTrue(PlaybackRecovery.transientResolution("Request timed out"))
    }
    @Test fun permanentProviderRestrictionsAreNotRetried() {
        listOf(null,"YT-FORMAT","YT-ACCESS","YT-JS","This upload requires an eligible signed-in account.").forEach {
            assertFalse(PlaybackRecovery.transientResolution(it))
        }
    }
}
