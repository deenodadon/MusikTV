package tv.musik

import android.app.Application
import android.content.Intent
import androidx.media3.common.*
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

class MusikApplication : Application() {
    lateinit var library: LibraryStore
    lateinit var catalog: Catalog
    lateinit var playback: PlaybackEngine
    override fun onCreate() { super.onCreate(); library = LibraryStore(this); catalog = Catalog(this); playback = PlaybackEngine(this) }
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackEngine(private val app: MusikApplication) {
    val state = MutableStateFlow(app.library.loadQueue())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var resolving: Job? = null
    private var generation = 0
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
                state.value = state.value.copy(active=false, loading=false, error="Playback failed. Check your connection or try another upload.")
            }
        })
        scope.launch { while (player === p) { state.value = state.value.copy(position=p.currentPosition.coerceAtLeast(0), duration=p.duration.takeIf { it > 0 } ?: 0); delay(500) } }
    }
    fun detach() { generation++; resolving?.cancel(); player = null; state.value = state.value.copy(active=false, loading=false) }
    private fun change(p: Playing) { state.value = p; app.library.saveQueue(p) }
    fun startQueue(ts: List<Track>, index: Int = 0) {
        val playable = ts.filter { it.playable }
        if (playable.isEmpty()) return
        change(state.value.copy(queue=playable, index=index.coerceIn(playable.indices), error=null)); playCurrent()
    }
    fun select(index: Int) { if (index !in state.value.queue.indices) return; change(state.value.copy(index=index)); playCurrent() }
    fun playCurrent() {
        val t = state.value.current ?: return
        val p = player ?: run { state.value=state.value.copy(error="The player is starting. Try Play again."); return }
        val request = ++generation
        resolving?.cancel(); p.stop(); state.value = state.value.copy(loading=true, error=null, position=0, duration=0)
        resolving = scope.launch {
            try {
                var headers = emptyMap<String,String>()
                val url = if (t.kind in listOf("local", "stream")) t.id else {
                    val data = app.catalog.call("resolve", t.id) as JSONObject
                    val jsonHeaders = data.optJSONObject("headers") ?: JSONObject()
                    headers = jsonHeaders.keys().asSequence().associateWith { jsonHeaders.getString(it) }
                    data.getString("url")
                }
                if (request != generation || player !== p) return@launch
                val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers).setConnectTimeoutMs(20000).setReadTimeoutMs(20000)
                val source = DefaultMediaSourceFactory(DefaultDataSource.Factory(app,http)).createMediaSource(
                    MediaItem.Builder().setMediaId(t.key).setUri(url).setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setArtworkUri(t.artwork.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) }).build()).build())
                p.setMediaSource(source); p.prepare(); p.play(); app.library.remember(t)
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
        player=ExoPlayer.Builder(this).setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true).setHandleAudioBecomingNoisy(true).build()
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
