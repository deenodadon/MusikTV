package tv.musik

import android.app.Application
import android.content.Intent
import androidx.media3.common.*
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import android.content.Context
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

class MusikApplication : Application() {
    lateinit var vault: SecureVault
    lateinit var library: LibraryStore
    lateinit var solver: ChallengeSolver
    lateinit var plugins: PluginHost
    lateinit var catalog: Catalog
    lateinit var playback: PlaybackEngine
    override fun onCreate() { super.onCreate(); solver = ChallengeSolver(this); vault = SecureVault(this); library = LibraryStore(this); catalog = Catalog(this); playback = PlaybackEngine(this); plugins = PluginHost(this); if(java.io.File(filesDir,"plugins/index.json").exists()) CoroutineScope(SupervisorJob()+Dispatchers.Main).launch { try { plugins.reload() } catch(_:Exception) {} } }
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackEngine(private val app: MusikApplication) {
    val state = MutableStateFlow(app.library.loadQueue())
    val levels = MutableStateFlow(List(40) { 0f })
    val audioProcessor = MusikAudioProcessor { level -> levels.value=(levels.value.drop(1)+level) }.apply { preset=state.value.equalizer }
    fun equalizer(name:String) { if(name !in listOf("flat","bass","vocal")) return; audioProcessor.preset=name; change(state.value.copy(equalizer=name)) }
    fun stop() { generation++; resolving?.cancel(); warm?.cancel();preparing.clear();warmedKeys=emptyList();player?.stop(); state.value=state.value.copy(active=false,loading=false,position=0); levels.value=List(40){0f} }
    fun clearQueue() { stop(); clearPrepared(); change(state.value.copy(queue=emptyList(),index=-1,error=null)) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var resolving: Job? = null
    private var generation = 0
    private var networkRetries = 0
    private data class Prepared(val stream:JSONObject,val expires:Long)
    private val prepared=LinkedHashMap<String,Prepared>()
    private val preparing=SingleFlight<String,JSONObject>(scope)
    private var warm:Job?=null
    private var warmedKeys=emptyList<String>()
    fun clearPrepared() { prepared.clear();warm?.cancel();preparing.clear();warmedKeys=emptyList() }
    private suspend fun resolved(t:Track):JSONObject {
        prepared[t.key]?.takeIf { it.expires>System.currentTimeMillis() }?.let { return it.stream }
        return preparing.await(t.key) {
            var result:JSONObject
            try {
                result=if(t.kind=="nuclear") app.plugins.stream(t) else app.catalog.call(if(t.kind=="torbox") "torbox_stream" else "resolve",t.id) as JSONObject
            } catch(e:CancellationException) { throw e }
              catch(e:Exception) {
                if(!PlaybackRecovery.transientResolution(e.message)) throw e
                delay(750)
                result=if(t.kind=="nuclear") app.plugins.stream(t) else app.catalog.call(if(t.kind=="torbox") "torbox_stream" else "resolve",t.id) as JSONObject
            }
            ensureActive()
            val now=System.currentTimeMillis()
            val expire=android.net.Uri.parse(result.getString("url")).getQueryParameter("expire")?.toLongOrNull()?.times(1000)?.minus(60000) ?: now+300000
            prepared[t.key]=Prepared(result,minOf(expire,now+300000))
            while(prepared.size>256) prepared.remove(prepared.keys.first())
            result
        }
    }

    private fun prepareQueue() {
        // Only prepare the next two tracks, after foreground playback starts.
        val s=state.value
        val ts=s.queue.drop((s.index+1).coerceAtLeast(0)).take(2).filter { it.kind !in listOf("local","stream") }
        val keys=ts.map { it.key }
        if(keys==warmedKeys) return
        warmedKeys=keys;warm?.cancel()
        warm=scope.launch {
            delay(3000)
            while(!state.value.active) delay(1000)
            for(t in ts) {
                try { resolved(t) } catch(e:CancellationException) { throw e } catch(_:Exception) {}
            }
        }
    }
    fun attach(p: ExoPlayer) {
        player = p
        p.volume = state.value.volume
        p.setPlaybackSpeed(state.value.speed)
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { state.value = state.value.copy(active=isPlaying) }
            override fun onPlaybackStateChanged(playbackState: Int) {
                state.value = state.value.copy(loading=playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_ENDED) advance(1, true)
            }
            override fun onPlayerError(error: PlaybackException) {
                state.value.current?.let { prepared.remove(it.key) }
                val http=(error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
                val recoverable=error.errorCode in listOf(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) || http in listOf(403,410,429,500,502,503,504)
                if(recoverable && networkRetries<1) {
                    networkRetries++
                    val request=generation
                    val resume=p.currentPosition.coerceAtLeast(0)
                    state.value=state.value.copy(active=false,loading=true,error=null)
                    resolving?.cancel()
                    resolving=scope.launch { delay(1000);if(request==generation && player===p) playCurrent(false,resume) }
                } else state.value = state.value.copy(active=false, loading=false, error=if(recoverable) "The stream connection failed after retrying. Check your connection, then press Retry. (PLAY-NETWORK)" else "Playback failed. Press Retry or try another upload. (PLAY-${error.errorCode})")
            }
        })
        scope.launch { while (player === p) { state.value = state.value.copy(position=p.currentPosition.coerceAtLeast(0), duration=p.duration.takeIf { it > 0 } ?: 0); delay(500) } }
    }
    fun detach() { generation++; resolving?.cancel();warm?.cancel(); player = null; state.value = state.value.copy(active=false, loading=false) }
    private fun change(p: Playing) { state.value = p; app.library.saveQueue(p);prepareQueue() }
    fun playRadio(t:Track) {
        startQueue(listOf(t))
        scope.launch {
            try {
                val extra=if(t.kind=="local") {
                    val parent=android.net.Uri.parse(t.id).lastPathSegment.orEmpty().substringBeforeLast('/')
                    app.library.state.value.local.filter { it.key!=t.key && android.net.Uri.parse(it.id).lastPathSegment.orEmpty().substringBeforeLast('/')==parent }
                } else if(t.kind in listOf("song","video")) app.catalog.tracks("radio",t.id) else emptyList()
                val s=state.value
                if(s.queue.size==1 && s.current?.key==t.key) change(s.copy(queue=s.queue+extra.filter { it.playable && it.key!=t.key }.distinctBy { it.key }))
            } catch(_:Exception) { /* Optional radio failures never interrupt the selected audio. */ }
        }
    }
    fun startQueue(ts: List<Track>, index: Int = 0) {
        val playable = ts.filter { it.playable }
        if (playable.isEmpty()) return
        change(state.value.copy(queue=playable, index=index.coerceIn(playable.indices), error=null)); playCurrent()
    }
    fun select(index: Int) { if (index !in state.value.queue.indices) return; change(state.value.copy(index=index)); playCurrent() }
    fun playCurrent(resetRetry:Boolean=true,resumePosition:Long=0) {
        val t = state.value.current ?: return
        val p = player ?: run { state.value=state.value.copy(error="The player is starting. Try Play again."); return }
        val request = ++generation
        if(resetRetry) networkRetries=0
        resolving?.cancel(); p.stop(); state.value = state.value.copy(loading=true, error=null, position=0, duration=0)
        resolving = scope.launch {
            try {
                var headers = emptyMap<String,String>()
                val url = if (t.kind in listOf("local", "stream")) t.id else {
                    val data = resolved(t)
                    val jsonHeaders = data.optJSONObject("headers") ?: JSONObject()
                    headers = jsonHeaders.keys().asSequence().associateWith { jsonHeaders.getString(it) }
                    data.getString("url")
                }
                if (request != generation || player !== p) return@launch
                val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers).setConnectTimeoutMs(20000).setReadTimeoutMs(20000)
                val source = DefaultMediaSourceFactory(DefaultDataSource.Factory(app,http)).createMediaSource(
                    MediaItem.Builder().setMediaId(t.key).setUri(url).setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setArtworkUri(t.artwork.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) }).build()).build())
                p.setMediaSource(source); if(resumePosition>0) p.seekTo(resumePosition);p.prepare(); p.play(); app.library.remember(t)
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { if (request == generation) state.value = state.value.copy(loading=false, active=false, error=e.message ?: "Unable to load this track.") }
        }
    }
    fun toggle() {
        val p=player ?: return
        if (state.value.loading) { generation++; resolving?.cancel(); p.stop(); state.value=state.value.copy(loading=false); return }
        if (p.isPlaying) p.pause() else if (p.playbackState in listOf(Player.STATE_IDLE, Player.STATE_ENDED)) playCurrent() else p.play()
    }
    fun advance(direction: Int, finished: Boolean = false) {
        val s=state.value
        val next = if (s.shuffle && direction>0 && s.queue.size>1 && !(finished && s.repeat==1)) s.queue.indices.filter { it!=s.index }.random() else QueueRules.next(s.index,s.queue.size,direction,s.repeat,finished)
        if (next!=null) select(next) else { player?.pause(); state.value=state.value.copy(active=false,loading=false) }
    }
    fun enqueue(t: Track) { if(t.playable) change(state.value.copy(queue=state.value.queue+t)) }
    fun remove(index: Int) {
        val s=state.value; if(index !in s.queue.indices) return
        val rows=s.queue.toMutableList().apply { removeAt(index) }
        val current = when { rows.isEmpty() -> -1; index<s.index -> s.index-1; index==s.index -> index.coerceAtMost(rows.lastIndex); else -> s.index }
        change(s.copy(queue=rows,index=current))
        if(index==s.index) { generation++; resolving?.cancel(); player?.stop(); if(current>=0) playCurrent() else state.value=state.value.copy(active=false,loading=false,position=0,duration=0) }
    }
    fun move(index: Int, delta: Int) {
        val s=state.value; val to=index+delta; if(index !in s.queue.indices || to !in s.queue.indices) return
        val rows=s.queue.toMutableList(); java.util.Collections.swap(rows,index,to)
        change(s.copy(queue=rows,index=QueueRules.movedCurrent(s.index,index,to)))
    }
    fun shuffle() = change(state.value.copy(shuffle=!state.value.shuffle))
    fun repeat() = change(state.value.copy(repeat=(state.value.repeat+1)%3))
    fun seek(delta: Long) { player?.let { p -> p.seekTo((p.currentPosition+delta).coerceIn(0, p.duration.takeIf { it>0 } ?: Long.MAX_VALUE)) } }
    fun volume(delta: Float) { val v=(state.value.volume+delta).coerceIn(0f,1f); player?.volume=v; change(state.value.copy(volume=v)) }
    fun speed() { val v=when(state.value.speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 0.75f; else -> 1f }; player?.setPlaybackSpeed(v); change(state.value.copy(speed=v)) }
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    override fun onCreate() {
        super.onCreate()
        val app=application as MusikApplication
        val renderers=object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context,enableFloatOutput: Boolean,enableAudioTrackPlaybackParams: Boolean): AudioSink = DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(app.playback.audioProcessor)).setEnableFloatOutput(false).setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams).build()
        }
        player=ExoPlayer.Builder(this,renderers).setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true).setHandleAudioBecomingNoisy(true).build()
        app.playback.attach(player)
        val forwarding=object : ForwardingPlayer(player) {
            override fun getAvailableCommands() = super.getAvailableCommands().buildUpon().add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_PREVIOUS).build()
            override fun isCommandAvailable(command: Int) = availableCommands.contains(command)
            override fun seekToNextMediaItem() = app.playback.advance(1)
            override fun seekToPreviousMediaItem() = app.playback.advance(-1)
            override fun seekToNext() = app.playback.advance(1)
            override fun seekToPrevious() = app.playback.advance(-1)
        }
        val intent=Intent(this,MainActivity::class.java)
        val pending=android.app.PendingIntent.getActivity(this,0,intent,android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        session=MediaSession.Builder(this,forwarding).setSessionActivity(pending).build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return if(controllerInfo.packageName==packageName || controllerInfo.isTrusted) session else null
    }
    override fun onTaskRemoved(rootIntent: Intent?) { if(!player.playWhenReady) stopSelf() }
    override fun onDestroy() { (application as MusikApplication).playback.detach(); session?.release(); player.release(); super.onDestroy() }
}
