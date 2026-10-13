package tv.musik
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.*
class AudioEffectsTest {
    private fun response(preset:String,freq:Double):Double {
        val filter=Biquad.create(preset,48000.0);var input=0.0;var output=0.0
        repeat(48000) { i -> val x=0.1*sin(2*PI*freq*i/48000);val y=filter.process(x);assertTrue(y.isFinite());if(i>24000) { input+=x*x;output+=y*y } }
        return sqrt(output/input)
    }
    @Test fun flatPreservesPcmAmplitude() { assertEquals(1.0,response("flat",1000.0),0.000001) }
    @Test fun bassBoostsLowFrequenciesAndPreservesTreble() { assertTrue(response("bass",30.0)>1.8);assertEquals(1.0,response("bass",8000.0),0.02) }
    @Test fun vocalBoostsTheThreeKhzBand() { assertEquals(10.0.pow(4.0/20),response("vocal",3000.0),0.01) }
}
