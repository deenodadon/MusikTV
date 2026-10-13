package tv.musik

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import kotlinx.coroutines.flow.MutableStateFlow

/** Optional 15-second measurement on the TV, not a synthetic FPS estimate. */
class ResponsivenessMonitor(private val window:Window) {
    val running=MutableStateFlow(false)
    val result=MutableStateFlow("")
    private val main=Handler(Looper.getMainLooper())
    private var thread:HandlerThread?=null
    private var listener:Window.OnFrameMetricsAvailableListener?=null
    private var finish:Runnable?=null
    fun start() {
        close()
        val samples=FrameSamples()
        val refresh=(window.decorView.display?.refreshRate ?: 60f).coerceAtLeast(1f)
        val budget=(1_000_000_000.0/refresh).toLong()
        val worker=HandlerThread("musik-frame-samples").apply { start() }
        thread=worker
        val callback=Window.OnFrameMetricsAvailableListener { _,metrics,_ ->
            val deadline=if(Build.VERSION.SDK_INT>=31) metrics.getMetric(FrameMetrics.DEADLINE).takeIf { it>0 } ?: budget else budget
            samples.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION),deadline)
        }
        listener=callback
        window.addOnFrameMetricsAvailableListener(callback,Handler(worker.looper))
        running.value=true
        result.value="Measuring for 15 seconds. Move between buttons and scroll, then return here."
        finish=Runnable { close();result.value=samples.summary() }.also { main.postDelayed(it,15000) }
    }
    fun close() {
        finish?.let { main.removeCallbacks(it) };finish=null
        listener?.let { window.removeOnFrameMetricsAvailableListener(it) };listener=null
        thread?.quitSafely();thread=null
        running.value=false
    }
}
