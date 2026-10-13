package tv.musik

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Native PCM path: actual loudness history plus Musik's flat/bass/vocal presets. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MusikAudioProcessor(private val measured: (Float) -> Unit): BaseAudioProcessor() {
    @Volatile var preset="flat"
    private var applied=""
    private var filters=emptyArray<Biquad>()
    private var meterAt=0L
    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if(inputAudioFormat.encoding!=C.ENCODING_PCM_16BIT) throw UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }
    override fun onFlush() { applied=""; filters=emptyArray() }
    override fun queueInput(inputBuffer: ByteBuffer) {
        if(applied!=preset || filters.size!=inputAudioFormat.channelCount) {
            applied=preset
            filters=Array(inputAudioFormat.channelCount) { Biquad.create(applied,inputAudioFormat.sampleRate.toDouble()) }
        }
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val output=replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.LITTLE_ENDIAN)
        var power=0.0; var count=0
        while(inputBuffer.remaining()>=2) {
            val sample=inputBuffer.short.toDouble()/32768.0
            val filtered=filters[count%filters.size].process(sample).coerceIn(-1.0,1.0)
            output.putShort((filtered*32767).roundToInt().toShort())
            power+=filtered*filtered; count++
        }
        output.flip()
        val now=System.nanoTime()
        if(count>0 && now-meterAt>80_000_000) { meterAt=now; measured(sqrt(power/count).toFloat()) }
    }
}
/** RBJ Audio EQ Cookbook biquad, with separate state per channel. */
class Biquad(private val b0:Double,private val b1:Double,private val b2:Double,private val a1:Double,private val a2:Double) {
    private var z1=0.0; private var z2=0.0
    fun process(x:Double):Double { val y=b0*x+z1; z1=b1*x-a1*y+z2; z2=b2*x-a2*y; return y }
    companion object {
        fun create(preset:String, rate:Double):Biquad {
            if(preset=="flat" || rate<=0) return Biquad(1.0,0.0,0.0,0.0,0.0)
            val gain=if(preset=="bass") 6.0 else 4.0
            val freq=(if(preset=="bass") 100.0 else 3000.0).coerceAtMost(rate*.45)
            val a=10.0.pow(gain/40.0); val w=2*PI*freq/rate; val c=cos(w)
            val alpha=sin(w)/2
            if(preset=="vocal") {
                val norm=1+alpha/a
                return Biquad((1+alpha*a)/norm,-2*c/norm,(1-alpha*a)/norm,-2*c/norm,(1-alpha/a)/norm)
            }
            val alphaShelf=sin(w)*sqrt(2.0)/2; val q=2*sqrt(a)*alphaShelf
            val norm=(a+1)+(a-1)*c+q
            return Biquad(a*((a+1)-(a-1)*c+q)/norm,2*a*((a-1)-(a+1)*c)/norm,a*((a+1)-(a-1)*c-q)/norm,-2*((a-1)+(a+1)*c)/norm,((a+1)+(a-1)*c-q)/norm)
        }
    }
}
