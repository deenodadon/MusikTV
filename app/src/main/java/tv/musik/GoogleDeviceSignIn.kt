package tv.musik

import java.net.URI

object GoogleDeviceSignIn {
    private const val fallback="https://www.google.com/device"
    fun verificationUrl(value:String?):String = runCatching {
        val uri=URI(value ?: fallback)
        require(uri.scheme=="https" && uri.host in listOf("google.com","www.google.com","accounts.google.com") && uri.userInfo==null && uri.port in listOf(-1,443))
        uri.toASCIIString()
    }.getOrDefault(fallback)
    fun pollInterval(seconds:Long)=seconds.coerceIn(5,3600)
}
