package tv.musik

import org.json.JSONArray
import org.json.JSONObject

 data class Track(val id: String, val title: String, val artist: String = "Unknown artist", val kind: String = "song", val artwork: String = "") {
    val playable get() = kind in listOf("song", "video", "local", "stream", "torbox", "nuclear")
    val key get() = "$kind:$id"
    fun json() = JSONObject().put("id", id).put("title", title).put("artist", artist).put("kind", kind).put("artwork", artwork)
    companion object {
        fun from(j: JSONObject) = Track(j.getString("id"), j.optString("title", "Untitled"), j.optString("artist", "Unknown artist"), j.optString("kind", "song"), j.optString("artwork"))
        fun list(a: JSONArray) = (0 until a.length()).map { from(a.getJSONObject(it)) }
    }
}
data class Playlist(val name: String, val tracks: List<Track>, val itemIds:List<String> = tracks.map { java.util.UUID.randomUUID().toString() })
data class Library(val favorites: List<Track> = emptyList(), val history: List<Track> = emptyList(), val local: List<Track> = emptyList(), val playlists: List<Playlist> = emptyList())
data class Playing(val queue: List<Track> = emptyList(), val index: Int = -1, val active: Boolean = false, val loading: Boolean = false, val position: Long = 0, val duration: Long = 0, val shuffle: Boolean = false, val repeat: Int = 0, val volume: Float = 0.7f, val speed: Float = 1f, val error: String? = null, val equalizer: String="flat") {
    val current get() = queue.getOrNull(index)
}
/** Musik repeat semantics: manual Next skips even when repeating a song. */
object QueueRules {
    fun next(index: Int, size: Int, direction: Int, repeat: Int, finished: Boolean): Int? {
        if (size == 0) return null
        if (finished && repeat == 1 && index in 0 until size) return index
        val next = index + direction
        return when { next in 0 until size -> next; repeat == 2 -> ((next % size) + size) % size; else -> null }
    }
    fun movedCurrent(current: Int, from: Int, to: Int) = when(current) { from -> to; to -> from; else -> current }
}
