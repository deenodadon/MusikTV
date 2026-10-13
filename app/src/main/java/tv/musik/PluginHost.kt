package tv.musik

import android.annotation.SuppressLint
import android.webkit.*
import android.content.Intent
import android.net.Uri
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Verified plugins run in Android WebView with local WASM bundling; no desktop Node daemon. */
class PluginHost(private val app:MusikApplication) {
    val status=MutableStateFlow(JSONObject().put("providers",JSONArray()).put("settings",JSONArray()))
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val sequence=AtomicInteger()
    private val requests=ConcurrentHashMap<Int,CompletableDeferred<Any>>()
    private val ready=CompletableDeferred<Unit>()
    private var web: WebView?=null
    private var active=JSONObject()
    fun selected(kind:String):String? = active.optString(kind).takeIf { it.isNotBlank() }
    @SuppressLint("SetJavaScriptEnabled")
    private fun create() {
        if(web!=null) return
        web=WebView(app).apply {
            settings.javaScriptEnabled=true; settings.allowFileAccess=false; settings.allowContentAccess=false
            settings.domStorageEnabled=false
            addJavascriptInterface(Bridge(),"Android")
            webViewClient=object:WebViewClient() {
                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=true
                override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? {
                    val u=request.url
                    if(u.host!="appassets.androidplatform.net" || !u.path.orEmpty().startsWith("/assets/")) return WebResourceResponse("text/plain","UTF-8",java.io.ByteArrayInputStream(ByteArray(0)))
                    val name=u.path!!.removePrefix("/assets/")
                    if(name.contains("..")) return null
                    return try { WebResourceResponse(if(name.endsWith(".wasm")) "application/wasm" else if(name.endsWith(".html")) "text/html" else "application/javascript","UTF-8",app.assets.open(name)) } catch(_:Exception) { null }
                }
            }
            loadUrl("https://appassets.androidplatform.net/assets/plugin-host.html")
        }
    }
    suspend fun call(method:String,args:JSONObject=JSONObject()):Any = withContext(Dispatchers.Main.immediate) {
        create(); withTimeout(30000) { ready.await() }
        val id=sequence.incrementAndGet(); val reply=CompletableDeferred<Any>(); requests[id]=reply
        try {
            web!!.evaluateJavascript("musikHost.request($id,${JSONObject.quote(method)},$args)",null)
            withTimeout(120000) { reply.await() }
        } finally { requests.remove(id) }
    }
    suspend fun reload():JSONObject {
        val config=app.catalog.call("plugins_status") as JSONObject
        active=config.optJSONObject("active") ?: JSONObject()
        val installed=config.getJSONObject("installed")
        val entries=JSONArray(installed.keys().asSequence().map { installed.getJSONObject(it) }.filter { it.optBoolean("enabled",true) }.toList())
        val result=call("init",JSONObject().put("active",active).put("plugins",entries)) as JSONObject
        status.value=result
        val available=result.getJSONArray("providers")
        val ids=(0 until available.length()).map { available.getJSONObject(it).getString("id") }
        active.keys().asSequence().toList().forEach { kind -> if(active.getString(kind) !in ids) { active.remove(kind); app.catalog.call("plugins_select",kind,"") } }
        return result
    }
    suspend fun select(kind:String,id:String) { app.catalog.call("plugins_select",kind,id); reload() }
    fun nuclear(t:Track):JSONObject = if(t.kind.startsWith("nuclear")) JSONObject(t.id) else JSONObject().put("title",t.title).put("artists",JSONArray().put(JSONObject().put("name",t.artist))).put("source",JSONObject().put("id",t.id).put("provider","musik-youtube"))
    fun track(item:JSONObject,kind:String="song",provider:String=""):Track {
        val copy=JSONObject(item.toString())
        if(!copy.has("source")) copy.put("source",JSONObject().put("id",copy.optString("id")).put("provider",provider))
        val artists=copy.optJSONArray("artists") ?: JSONArray()
        val thumbs=copy.optJSONArray("thumbnails") ?: JSONArray()
        val art=copy.optJSONObject("artwork")?.optString("url") ?: if(thumbs.length()>0) thumbs.optJSONObject(thumbs.length()-1)?.optString("url").orEmpty() else ""
        return Track(copy.toString(),copy.optString("title",copy.optString("name","Untitled")),(0 until artists.length()).joinToString(", ") { artists.optJSONObject(it)?.optString("name").orEmpty() }.ifBlank { provider.ifBlank { "Plugin" } },if(kind=="song") "nuclear" else "nuclear_$kind",art)
    }
    fun tracks(array:JSONArray,kind:String="song",provider:String="")=(0 until array.length()).map { track(array.getJSONObject(it),kind,provider) }
    suspend fun search(query:String,kind:String):List<Track> {
        val provider=selected("metadata") ?: return emptyList()
        val plural=when(kind) { "album"->"albums"; "artist"->"artists"; else->"tracks" }
        val data=call("search",JSONObject().put("provider",provider).put("params",JSONObject().put("query",query).put("types",JSONArray().put(plural)).put("limit",40))) as JSONObject
        return tracks(data.optJSONArray(plural) ?: JSONArray(),kind,provider)
    }
    suspend fun browse(t:Track):List<Track> {
        val item=JSONObject(t.id);val source=item.getJSONObject("source");val provider=source.getString("provider")
        suspend fun invoke(method:String,kind:String="metadata"):Any = call("invoke",JSONObject().put("provider",provider).put("kind",kind).put("method",method).put("args",JSONArray().put(source.getString("id"))))
        return when(t.kind) {
            "nuclear_album" -> { val data=invoke("fetchAlbumDetails") as JSONObject;tracks(data.optJSONArray("tracks") ?: JSONArray(),provider=provider) }
            "nuclear_artist" -> tracks(invoke("fetchArtistTopTracks") as JSONArray,provider=provider)+tracks(invoke("fetchArtistAlbums") as JSONArray,"album",provider)
            "nuclear_playlist" -> { val data=invoke("fetchPlaylistByUrl","playlists") as JSONObject; val rows=data.optJSONArray("tracks") ?: data.optJSONArray("items") ?: JSONArray(); tracks(JSONArray((0 until rows.length()).map { rows.getJSONObject(it).optJSONObject("track") ?: rows.getJSONObject(it) }),provider=provider) }
            else -> emptyList()
        }
    }
    suspend fun stream(t:Track):JSONObject {
        val item=nuclear(t);val source=item.optJSONObject("source")
        val provider=selected("streaming") ?: run {
            val providers=status.value.getJSONArray("providers")
            (0 until providers.length()).map { providers.getJSONObject(it) }.firstOrNull { it.optString("id")==source?.optString("provider") }?.optString("streamingProviderId")?.takeIf { it.isNotBlank() }
        }
        if(provider==null) {
            val matches=app.catalog.tracks("search","${t.artist} ${t.title}","song")
            if(matches.isEmpty()) throw IllegalStateException("No YouTube match. Choose this plugin’s streaming provider.")
            return app.catalog.call("resolve",matches.first().id) as JSONObject
        }
        return call("stream",JSONObject().put("track",item).put("provider",provider)) as JSONObject
    }
    suspend fun playlistUrl(url:String):List<Track> {
        val provider=selected("playlists") ?: return emptyList()
        val matched=call("invoke",JSONObject().put("provider",provider).put("kind","playlists").put("method","matchesUrl").put("args",JSONArray().put(url)))
        if(matched!=true) return emptyList()
        return listOf(track(JSONObject().put("title","Open playlist").put("source",JSONObject().put("id",url).put("provider",provider)),"playlist",provider))
    }
    suspend fun dashboard():List<Track> {
        val provider=selected("dashboard") ?: throw IllegalStateException("Select a dashboard provider in Plugins.")
        val result=mutableListOf<Track>()
        for((method,kind) in listOf("fetchTopTracks" to "song","fetchTopAlbums" to "album","fetchTopArtists" to "artist","fetchNewReleases" to "album","fetchEditorialPlaylists" to "playlist")) {
            val p=status.value.getJSONArray("providers");val descriptor=(0 until p.length()).map { p.getJSONObject(it) }.firstOrNull { it.optString("id")==provider } ?: continue
            if(!descriptor.getJSONArray("methods").toString().contains(method)) continue
            val data=call("invoke",JSONObject().put("provider",provider).put("kind","dashboard").put("method",method).put("args",JSONArray())) as JSONArray
            result+=tracks(data,kind,descriptor.optString("metadataProviderId",provider))
        }
        return result
    }
    private fun safeError(message:String):String {
        var text=message.take(1000)
        fun redact(value:Any?) {
            when(value) {
                is JSONObject -> value.keys().asSequence().forEach { redact(value.opt(it)) }
                is JSONArray -> (0 until value.length()).forEach { redact(value.opt(it)) }
                is String -> if(value.length>=6) text=text.replace(value,"[redacted]")
            }
        }
        try { redact(app.vault.read()) } catch(_:Exception) { return "Provider request failed." }
        return text.take(300)
    }
    private inner class Bridge {
        @JavascriptInterface fun ready() { ready.complete(Unit) }
        @JavascriptInterface fun read(relative:String):String? = try {
            val root=File(app.filesDir,"plugins").canonicalFile;val f=File(root,relative).canonicalFile
            if(!f.path.startsWith(root.path+File.separator) || !f.isFile || f.length()>5_000_000) null else f.readText()
        } catch(_:Exception) { null }
        @JavascriptInterface fun result(id:Int,json:String) {
            val result=JSONObject(json);requests[id]?.let { if(result.has("error")) it.completeExceptionally(IllegalStateException(safeError(result.getString("error")))) else it.complete(result.opt("result") ?: JSONObject.NULL) }
        }
        @JavascriptInterface fun invoke(id:Int,method:String,json:String) {
            scope.launch {
                try { val value=callback(method,JSONArray(json));web?.evaluateJavascript("musikHost.reply($id,${JSONArray().put(value).toString().let { it.substring(1,it.length-1) }},null)",null) }
                catch(e:Exception) { web?.evaluateJavascript("musikHost.reply($id,null,${JSONObject.quote(safeError(e.message ?: "Android host request failed."))})",null) }
            }
        }
    }
    private suspend fun callback(method:String,args:JSONArray):Any? {
        if(method=="http") return app.catalog.call("plugins_http",args.getString(0),args.optJSONObject(1)?.toString() ?: "{}")
        if(method.startsWith("settings.")) {
            val values=app.vault.read().optJSONObject("plugin_settings") ?: JSONObject();val namespace=args.getString(0)
            when(method) {
                "settings.register" -> { val definitions=args.getJSONArray(1); for(i in 0 until definitions.length()) { val d=definitions.getJSONObject(i);val k=namespace+":"+d.getString("id");if(!values.has(k)) values.put(k,d.opt("default") ?: JSONObject.NULL) } }
                "settings.get" -> return values.opt(namespace+":"+args.getString(1))
                "settings.set" -> values.put(namespace+":"+args.getString(1),args.get(2))
            }
            app.vault.put("plugin_settings",values);return null
        }
        if(method=="shell.openExternal") { val u=Uri.parse(args.getString(0));require(u.scheme=="https") { "Only HTTPS external pages are supported." };app.startActivity(Intent(Intent.ACTION_VIEW,u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return null }
        if(method.startsWith("ytdlp.")) {
            return when(method) {
                "ytdlp.search" -> JSONArray(app.catalog.tracks("search",args.getString(0),"video").map { nuclear(it) })
                "ytdlp.getStream" -> app.catalog.call("resolve",args.getString(0).substringAfter("v=").substringAfterLast('/').substringBefore('&'))
                "ytdlp.getPlaylist" -> JSONArray(app.catalog.tracks("browse",args.getString(0),"playlist").map { nuclear(it) })
                else -> throw IllegalStateException("Unsupported extractor request.")
            }
        }
        return playerCallback(method,args)
    }
    suspend fun shutdown() { withContext(Dispatchers.Main.immediate) { if(web!=null) { try { call("close") } finally { web?.destroy();web=null;scope.cancel() } } } }
    private fun playerCallback(method:String,args:JSONArray):Any? {
        val engine=app.playback;val p=engine.state.value;val library=app.library;val action=method.substringAfter('.')
        fun item(i:Int)=JSONObject().put("id",i.toString()).put("track",nuclear(p.queue[i])).put("status",if(i==p.index) "success" else "idle")
        when(method.substringBefore('.')) {
            "Queue" -> when(action) {
                "getQueue" -> return JSONObject().put("items",JSONArray(p.queue.indices.map { item(it) })).put("currentIndex",p.index)
                "getCurrentItem" -> return if(p.index in p.queue.indices) item(p.index) else null
                "goToNext" -> engine.advance(1); "goToPrevious" -> engine.advance(-1)
                "goToIndex","goToId" -> engine.select(args.getString(0).toInt())
                "clearQueue" -> engine.clearQueue()
                "addToQueue","addNext","addAt" -> { val ts=tracks(args.getJSONArray(0)); ts.forEach { engine.enqueue(it) }; if(action!="addToQueue") { var target=if(action=="addNext") p.index+1 else args.getInt(1); repeat(ts.size) { var from=engine.state.value.queue.lastIndex;while(from>target) { engine.move(from,-1);from-- };target++ } } }
                "removeByIndices","removeByIds" -> { val ids=args.getJSONArray(0);(0 until ids.length()).map { ids.getString(it).toInt() }.sortedDescending().forEach { engine.remove(it) } }
                "reorder" -> { val from=args.getInt(0);val to=args.getInt(1);repeat(kotlin.math.abs(to-from)) { engine.move(from+if(to>from) it else -it,if(to>from) 1 else -1) } }
                else -> throw IllegalStateException("This queue API needs an Android adapter: $action")
            }
            "Playback" -> when(action) {
                "getState" -> return JSONObject().put("status",if(p.active) "playing" else if(p.current!=null) "paused" else "stopped").put("seek",p.position/1000.0).put("duration",p.duration/1000.0)
                "getVolume" -> return p.volume; "isMuted" -> return p.volume==0f; "isShuffleEnabled" -> return p.shuffle; "getRepeatMode" -> return listOf("off","one","all")[p.repeat]
                "play" -> { if(!p.active) engine.toggle() }; "pause" -> { if(p.active) engine.toggle() }; "toggle" -> engine.toggle(); "stop" -> engine.stop()
                "seekTo" -> engine.seek((args.getDouble(0)*1000).toLong()-p.position)
                "setVolume" -> engine.volume(args.getDouble(0).toFloat()-p.volume)
                "setMuted" -> engine.volume((if(args.getBoolean(0)) 0f else .7f)-p.volume)
                "setShuffleEnabled" -> { if(args.getBoolean(0)!=p.shuffle) engine.shuffle() }
                "setRepeatMode" -> { val target=listOf("off","one","all").indexOf(args.getString(0));require(target>=0);repeat((target-p.repeat+3)%3) { engine.repeat() } }
                else -> throw IllegalStateException("Unsupported playback API: $action")
            }
            "Favorites" -> when(action) {
                "getTracks","getAlbums","getArtists" -> return JSONArray(library.state.value.favorites.filter { if(action=="getTracks") it.playable else it.kind.endsWith(if(action=="getAlbums") "album" else "artist") }.map { JSONObject().put("ref",nuclear(it)).put("addedAtIso","") })
                "addTrack","removeTrack","addAlbum","removeAlbum","addArtist","removeArtist","isTrackFavorite","isAlbumFavorite","isArtistFavorite" -> {
                    val incoming=args.getJSONObject(0);val ref=incoming.optJSONObject("source") ?: incoming
                    val found=library.state.value.favorites.firstOrNull { val source=nuclear(it).optJSONObject("source");source?.optString("id")==ref.optString("id") && source?.optString("provider")==ref.optString("provider") }
                    if(action.startsWith("is")) return found!=null
                    if(action.startsWith("remove") && found!=null) library.favorite(found)
                    if(action.startsWith("add") && found==null) library.favorite(track(incoming,if(action.contains("Album")) "album" else if(action.contains("Artist")) "artist" else "song"))
                }
                else -> throw IllegalStateException("Unsupported favorites API.")
            }
            "Playlists" -> when(action) {
                "getIndex" -> return JSONArray(library.state.value.playlists.map { JSONObject().put("id",it.name).put("name",it.name).put("itemCount",it.tracks.size).put("isReadOnly",false) })
                "getPlaylist" -> { val l=library.state.value.playlists.firstOrNull { it.name==args.getString(0) } ?: return null; return JSONObject().put("id",l.name).put("name",l.name).put("items",JSONArray(l.tracks.mapIndexed { i,t -> JSONObject().put("id",l.itemIds[i]).put("track",nuclear(t)) })) }
                "createPlaylist","saveQueueAsPlaylist" -> { val name=args.getString(0);library.create(name);if(action=="saveQueueAsPlaylist") p.queue.forEach { library.add(name,it) };return name }
                "deletePlaylist" -> library.delete(args.getString(0))
                "addTracks" -> { val name=args.getString(0);val old=library.state.value.playlists.first { it.name==name }.tracks.size;tracks(args.getJSONArray(1)).forEach { library.add(name,it) };val updated=library.state.value.playlists.first { it.name==name };return JSONArray((old until updated.tracks.size).map { i -> JSONObject().put("id",updated.itemIds[i]).put("track",nuclear(updated.tracks[i])) }) }
                "removeTracks" -> { val ids=args.getJSONArray(1);library.removePlaylistItems(args.getString(0),(0 until ids.length()).map { ids.getString(it) }) }
                "reorderTracks" -> library.movePlaylistItem(args.getString(0),args.getInt(1),args.getInt(2))
                "importPlaylist" -> { val data=args.getJSONObject(0);val original=data.optString("name","Imported playlist");var name=original;var suffix=2;while(library.state.value.playlists.any { it.name==name }) { name="$original ($suffix)";suffix++ };library.create(name);val items=data.optJSONArray("items") ?: JSONArray();for(i in 0 until items.length()) library.add(name,track(items.getJSONObject(i).optJSONObject("track") ?: items.getJSONObject(i)));return name }
                else -> throw IllegalStateException("This playlist API needs an Android adapter: $action")
            }
            else -> throw IllegalStateException("Unsupported Android host API: $method")
        }
        return null
    }
}
