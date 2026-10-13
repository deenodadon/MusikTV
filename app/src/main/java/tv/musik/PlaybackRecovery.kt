package tv.musik

/** Retry transport failures, never account, format or challenge restrictions. */
object PlaybackRecovery {
    fun transientResolution(message:String?):Boolean {
        val text=message?.lowercase() ?: return false
        return listOf("could not reach", "connection", "timed out", "timeout").any { it in text }
    }
}
