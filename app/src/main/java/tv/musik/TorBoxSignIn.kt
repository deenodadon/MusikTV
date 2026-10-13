package tv.musik

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable fun TorBoxSignIn(app:MusikApplication,onConnected:()->Unit,onDisconnected:()->Unit) {
    val scope=rememberCoroutineScope()
    var connected by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<JSONObject?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try { connected=withContext(Dispatchers.IO) { app.vault.read().optString("torbox_token").isNotBlank() } }
        catch(_:Exception) { status="Could not read your TorBox connection. Connect again." }
        ready=true
    }
    fun run(block:suspend ()->Unit) { if(busy) return;scope.launch { busy=true;status=null;try { block() } catch(e:CancellationException) { throw e } catch(e:Exception) { status=e.message ?: "TorBox connection failed." } finally { busy=false } } }
    LaunchedEffect(session) {
        val info=session ?: return@LaunchedEffect
        val interval=info.optLong("interval",5).coerceIn(1,3600)*1000
        val deadline=android.os.SystemClock.elapsedRealtime()+info.optLong("expires_in",600).coerceIn(1,3600)*1000
        try {
            while(android.os.SystemClock.elapsedRealtime()<deadline) {
                delay(interval)
                if(android.os.SystemClock.elapsedRealtime()>=deadline) break
                val reply=app.catalog.call("torbox_auth_poll",info.getString("device_code")) as JSONObject
                if(reply.getString("status")=="connected") {
                    withContext(Dispatchers.IO) { app.vault.put("torbox_token",reply.getString("token")) }
                    app.playback.clearPrepared();connected=true;session=null
                    status="TorBox connected.";onConnected();return@LaunchedEffect
                }
            }
            session=null;status="This TorBox QR code expired. Connect again for a new code."
        } catch(e:CancellationException) { throw e }
          catch(e:Exception) { session=null;status=e.message ?: "TorBox connection failed. Generate a new QR code." }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Action(if(connected) "Reconnect TorBox · QR code" else "Connect TorBox · QR code",Icons.Rounded.QrCode,enabled=ready && !busy && session==null) {
                run { session=app.catalog.call("torbox_auth_start") as JSONObject }
            }
            if(connected) Action("Disconnect",Icons.Rounded.LinkOff,enabled=!busy) { session=null;run {
                withContext(Dispatchers.IO) { app.vault.put("torbox_token",null) }
                if(app.playback.state.value.current?.kind=="torbox") app.playback.stop()
                app.playback.clearPrepared();connected=false;key="";onDisconnected();status="Disconnected. Remote torrents were kept."
            } }
            Action(if(manual) "Hide API key" else "Use API key instead",Icons.Rounded.Key,enabled=!busy && session==null) { manual=!manual }
        }
        if(manual) Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(key,{key=it},label={Text("TorBox API key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.weight(1f),enabled=!busy)
            Action("Connect",Icons.Rounded.Link,enabled=!busy && key.isNotBlank()) { run {
                withContext(Dispatchers.IO) { app.vault.put("torbox_token",key.trim()) }
                app.catalog.call("torbox_list");connected=true;key="";manual=false;onConnected();status="TorBox connected."
            } }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { Text(it,color=MaterialTheme.colorScheme.secondary) }
    }
    session?.let { info ->
        var qr by remember(info) { mutableStateOf<ImageBitmap?>(null) }
        var qrError by remember(info) { mutableStateOf(false) }
        LaunchedEffect(info) {
            try { qr=withContext(Dispatchers.Default) {
                val matrix=PairingQr.matrix(info.getString("verification_url_complete"))
                val pixels=IntArray(matrix.width*matrix.height) { i -> if(matrix[i%matrix.width,i/matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                Bitmap.createBitmap(pixels,matrix.width,matrix.height,Bitmap.Config.ARGB_8888).asImageBitmap()
            } } catch(e:CancellationException) { throw e } catch(_:Exception) { qrError=true }
        }
        AlertDialog(onDismissRequest={session=null;status="TorBox pairing cancelled."},title={Text("Connect TorBox")},text={
            Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                qr?.let { Image(it,"Scan to approve Musik TV on TorBox",Modifier.size(210.dp)) }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Scan with your phone, sign in to TorBox and approve Musik TV.")
                    Text("The TV connects automatically after approval.")
                    Text("Or visit torbox.app/oauth/device and enter:")
                    Text(info.getString("code"),fontSize=26.sp)
                    if(qr==null) Text(if(qrError) "QR could not be drawn. Use the address and code." else "Preparing QR code…")
                }
            }
        },confirmButton={Action("Cancel") { session=null;status="TorBox pairing cancelled." }})
    }
}
