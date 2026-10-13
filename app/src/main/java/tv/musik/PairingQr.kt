package tv.musik

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object PairingQr {
    fun matrix(value:String)=QRCodeWriter().encode(value,BarcodeFormat.QR_CODE,320,320,mapOf(EncodeHintType.MARGIN to 4,EncodeHintType.CHARACTER_SET to "UTF-8"))
}
