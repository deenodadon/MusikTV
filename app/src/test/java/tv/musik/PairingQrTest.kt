package tv.musik

import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test

class PairingQrTest {
    @Test fun cameraDecoderRecoversTheExactTorBoxPairingUrl() {
        val url="https://torbox.app/oauth/device?code=TEST-PAIRING"
        val matrix=PairingQr.matrix(url)
        val pixels=IntArray(matrix.width*matrix.height) { i -> if(matrix[i%matrix.width,i/matrix.width]) 0xff000000.toInt() else 0xffffffff.toInt() }
        val bitmap=BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width,matrix.height,pixels)))
        assertEquals(url,QRCodeReader().decode(bitmap).text)
    }
}
