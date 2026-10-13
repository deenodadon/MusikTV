package tv.musik

import android.content.ComponentName
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {
    lateinit var responsiveness: ResponsivenessMonitor
        private set
    private var controller: ListenableFuture<MediaController>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        responsiveness=ResponsivenessMonitor(window)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowInsetsControllerCompat(window,window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        controller=MediaController.Builder(this,SessionToken(this,ComponentName(this,PlaybackService::class.java))).buildAsync()
        setContent { MusikTheme { MusikApp() } }
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val engine=(application as MusikApplication).playback
        when(keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { engine.toggle(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { if(!engine.state.value.active) engine.toggle(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { if(engine.state.value.active) engine.toggle(); return true }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { engine.advance(1); return true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { engine.advance(-1); return true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { engine.seek(10000); return true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { engine.seek(-10000); return true }
        }
        return super.onKeyDown(keyCode,event)
    }
    override fun onDestroy() { responsiveness.close();controller?.let { MediaController.releaseFuture(it) }; super.onDestroy() }
}
