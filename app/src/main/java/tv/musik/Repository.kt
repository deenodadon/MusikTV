package tv.musik

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

class Catalog(private val context: Context) {
    private val mutex = Mutex()
    suspend fun call(op: String, vararg args: String): Any = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!Python.isStarted()) Python.start(AndroidPlatform(context))
            val module = Python.getInstance().getModule("catalog")
            module.callAttr("set_solver", (context.applicationContext as MusikApplication).solver)
            module.callAttr("configure", (context.applicationContext as MusikApplication).vault.read().toString(), context.filesDir.absolutePath)
            val reply = JSONObject(module.callAttr("call", op, JSONArray(args.toList()).toString()).toString())
            if (!reply.getBoolean("ok")) throw IllegalStateException(reply.getString("error"))
            reply.get("data")
        }
    }
    suspend fun tracks(op: String, vararg args: String) = Track.list(call(op, *args) as JSONArray)
}
class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("musik", Context.MODE_PRIVATE)
    // Preserve write order while keeping large JSON snapshots off the UI thread.
    private val writer=java.util.concurrent.Executors.newSingleThreadExecutor()
    val state = MutableStateFlow(load())
    private fun load(): Library = try {
        val j = JSONObject(prefs.getString("library", "{}")!!)
        fun tracks(key: String) = Track.list(j.optJSONArray(key) ?: JSONArray())
        val p = j.optJSONArray("playlists") ?: JSONArray()
        Library(tracks("favorites"), tracks("history"), tracks("local"), (0 until p.length()).map { val item = p.getJSONObject(it); run { val ts=Track.list(item.getJSONArray("tracks"));val ids=item.optJSONArray("itemIds");Playlist(item.getString("name"),ts,if(ids!=null && ids.length()==ts.size) (0 until ids.length()).map { ids.getString(it) } else ts.map { java.util.UUID.randomUUID().toString() }) } })
    } catch (_: Exception) { Library() }
    private fun save(l: Library) {
        state.value = l
        writer.execute {
        fun array(ts: List<Track>) = JSONArray(ts.map { it.json() })
        prefs.edit().putString("library", JSONObject().put("favorites", array(l.favorites)).put("history", array(l.history)).put("local", array(l.local)).put("playlists", JSONArray(l.playlists.map { JSONObject().put("name", it.name).put("tracks", array(it.tracks)).put("itemIds",JSONArray(it.itemIds)) })).toString()).apply()
        }
    }
    fun favorite(t: Track) { val l = state.value; save(l.copy(favorites = if (l.favorites.any { it.key == t.key }) l.favorites.filterNot { it.key == t.key } else l.favorites + t)) }
    fun remember(t: Track) { val l = state.value; save(l.copy(history = (listOf(t) + l.history.filterNot { it.key == t.key }).take(200))) }
    fun import(tracks: List<Track>) { val l = state.value; save(l.copy(local = (l.local + tracks).distinctBy { it.key })) }
    fun reset() { writer.execute { prefs.edit().clear().apply() };save(Library()); saveQueue(Playing()) }
    fun lyricsUri(t:Track)=prefs.getString("lyrics."+t.key,null)
    fun setLyricsUri(t:Track,uri:String) { prefs.edit().putString("lyrics."+t.key,uri).apply() }
    fun removeLocal(t: Track) { save(state.value.copy(local = state.value.local.filterNot { it.key == t.key })) }
    fun create(name: String) { val n = name.trim(); if (n.isNotEmpty() && state.value.playlists.none { it.name == n }) save(state.value.copy(playlists = state.value.playlists + Playlist(n, emptyList()))) }
    fun add(name: String, t: Track) { save(state.value.copy(playlists = state.value.playlists.map { if (it.name == name) it.copy(tracks = it.tracks + t,itemIds=it.itemIds+java.util.UUID.randomUUID().toString()) else it })) }
    fun delete(name: String) { save(state.value.copy(playlists = state.value.playlists.filterNot { it.name == name })) }
    fun removeFromPlaylist(name: String, t: Track) { val p=state.value.playlists.firstOrNull { it.name==name } ?: return;val index=p.tracks.indexOfFirst { it.key==t.key };if(index>=0) removePlaylistItems(name,listOf(p.itemIds[index])) }
    fun removePlaylistItems(name:String,ids:List<String>) { save(state.value.copy(playlists=state.value.playlists.map { p -> if(p.name!=name) p else { val keep=p.itemIds.indices.filter { p.itemIds[it] !in ids };p.copy(tracks=keep.map { p.tracks[it] },itemIds=keep.map { p.itemIds[it] }) } })) }
    fun movePlaylistItem(name:String,from:Int,to:Int) { save(state.value.copy(playlists=state.value.playlists.map { p -> if(p.name!=name || from !in p.tracks.indices || to !in p.tracks.indices) p else { val ts=p.tracks.toMutableList();val ids=p.itemIds.toMutableList();ts.add(to,ts.removeAt(from));ids.add(to,ids.removeAt(from));p.copy(tracks=ts,itemIds=ids) } })) }
    fun loadQueue(): Playing = try {
        val j = JSONObject(prefs.getString("queue", "{}")!!)
        val ts = Track.list(j.optJSONArray("tracks") ?: JSONArray())
        Playing(queue=ts, index=j.optInt("index", -1).takeIf { it in ts.indices } ?: -1, shuffle=j.optBoolean("shuffle"), repeat=j.optInt("repeat").coerceIn(0,2), volume=j.optDouble("volume",0.7).toFloat().coerceIn(0f,1f), speed=j.optDouble("speed",1.0).toFloat().coerceIn(0.5f,2f),equalizer=j.optString("equalizer","flat"))
    } catch (_: Exception) { Playing() }
    fun saveQueue(p: Playing) { writer.execute { prefs.edit().putString("queue", JSONObject().put("tracks", JSONArray(p.queue.map { it.json() })).put("index",p.index).put("shuffle",p.shuffle).put("repeat",p.repeat).put("volume",p.volume).put("speed",p.speed).put("equalizer",p.equalizer).toString()).apply() } }
}
