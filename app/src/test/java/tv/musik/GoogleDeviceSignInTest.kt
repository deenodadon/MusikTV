package tv.musik

import org.junit.Assert.*
import org.junit.Test

class GoogleDeviceSignInTest {
    @Test fun verificationAlwaysOpensGoogleOverHttps() {
        assertEquals("https://accounts.google.com/device",GoogleDeviceSignIn.verificationUrl("https://accounts.google.com/device"))
        listOf("https://google.com.evil.test/device","http://google.com/device","javascript:alert(1)","https://attacker@google.com/device","https://google.com:8443/device",null).forEach {
            assertEquals("https://www.google.com/device",GoogleDeviceSignIn.verificationUrl(it))
        }
    }
    @Test fun respectsGooglesPollingIntervalAndMinimum() {
        assertEquals(5L,GoogleDeviceSignIn.pollInterval(0))
        assertEquals(15L,GoogleDeviceSignIn.pollInterval(15))
    }
}
