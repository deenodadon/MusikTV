package tv.musik

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

@Composable fun AccountScreen(app:MusikApplication,open:(Track)->Unit) {
    val context=LocalContext.current; val scope=rememberCoroutineScope()
    val stored=remember { app.vault.read() }
    var client by remember { mutableStateOf(stored.optString("oauth_id")) }
    var secret by remember { mutableStateOf(stored.optString("oauth_secret")) }
    var code by remember { mutableStateOf<JSONObject?>(null) }
    var rows by remember { mutableStateOf<List<Track>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var lastPoll by remember { mutableStateOf(0L) }
    fun run(block:suspend ()->Unit) { if(busy) return; scope.launch { busy=true; status=null; try { block() } catch(e:Exception) { status=e.message ?: "Account request failed." } finally { busy=false } } }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) run {
        val contents=withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { val b=it.readBytesLimited(2_000_000); b.toString(Charsets.UTF_8) } }
        val cookies=app.catalog.call("parse_cookies",contents) as String
        app.vault.put("cookies",cookies); app.vault.put("oauth",null)
        rows=app.catalog.tracks("account_playlists"); status="Account connected. Your playlists are below."
    } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Your YouTube account",fontSize=28.sp)
        Text("Approve sign-in on Google’s verification page using your TV/device OAuth client. Musik never asks for your Google password.")
        OutlinedTextField(client,{client=it},label={Text("Google TV/device OAuth client ID")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(secret,{secret=it},label={Text("OAuth client secret")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        Action("Begin Google sign-in",Icons.Rounded.Login,enabled=!busy) { run {
            require(client.isNotBlank() && secret.isNotBlank()) { "Enter a configured Google TV/device OAuth client first." }
            app.vault.put("oauth_id",client.trim()); app.vault.put("oauth_secret",secret.trim())
            code=app.catalog.call("oauth_start") as JSONObject
        } }
        code?.let { info ->
            Text("Visit ${info.optString("verification_url", "https://www.google.com/device")}",fontSize=17.sp)
            Text(info.getString("user_code"),fontSize=32.sp)
            Action("Open verification page",Icons.Rounded.OpenInBrowser) { try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(info.optString("verification_url","https://www.google.com/device")))) } catch(_:Exception) { status="Open the displayed address on your phone to approve this code." } }
            Action("I approved · check account",Icons.Rounded.Check,enabled=!busy) { run {
                val now=android.os.SystemClock.elapsedRealtime(); require(now-lastPoll>=info.optLong("interval",5)*1000) { "Wait a few seconds before checking again." }; lastPoll=now
                val token=app.catalog.call("oauth_finish",info.getString("device_code")) as JSONObject
                app.vault.put("oauth",token); app.vault.put("cookies",null)
                rows=app.catalog.tracks("account_playlists"); code=null; status="Account connected."
            } }
        }
        HorizontalDivider()
        Text("Existing YouTube session",fontSize=20.sp)
        Text("You can alternatively import your own Netscape-format YouTube cookie export. Imported session data is encrypted with Android Keystore. OAuth signs in to the catalog; eligible restricted playback may still need cookies.")
        Action("Import my YouTube cookies",Icons.Rounded.FileOpen,enabled=!busy) { try { import.launch(arrayOf("text/*","application/octet-stream")) } catch(_:Exception) { status="A Storage Access Framework file provider is required on this TV." } }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Action("Load my playlists",Icons.Rounded.PlaylistPlay,enabled=!busy) { run { rows=app.catalog.tracks("account_playlists") } }
            Action("Sign out",Icons.Rounded.Logout,enabled=!busy) { app.playback.stop(); app.playback.clearPrepared(); app.vault.put("cookies",null); app.vault.put("oauth",null); rows=emptyList(); code=null; status="Signed out. Saved local playlists and music were kept." }
        }
        if(busy) CircularProgressIndicator()
        status?.let { Text(it,color=MaterialTheme.colorScheme.secondary) }
        rows.forEach { t -> Action(t.title,Icons.Rounded.PlaylistPlay,modifier=Modifier.fillMaxWidth()) { open(t) } }
    }
}
internal fun java.io.InputStream.readBytesLimited(limit:Int):ByteArray {
    val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
    while(true) { val count=read(buffer); if(count<0) break; require(output.size()+count<=limit) { "File exceeds the import size limit." }; output.write(buffer,0,count) }
    return output.toByteArray()
}
@Composable fun TorrentScreen(app:MusikApplication,open:(Track)->Unit) {
    val scope=rememberCoroutineScope()
    var token by remember { mutableStateOf(app.vault.read().optString("torbox_token")) }
    var query by remember { mutableStateOf("") }
    var engineUrl by remember { mutableStateOf("") }
    var engines by remember { mutableStateOf<List<String>>(emptyList()) }
    var enabled by remember { mutableStateOf<List<String>>(emptyList()) }
    var rows by remember { mutableStateOf<List<Track>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var install by remember { mutableStateOf(false) }
    fun run(block:suspend ()->Unit) { if(busy) return; scope.launch { busy=true; status=null; try { block() } catch(e:Exception) { status=e.message ?: "Torrent request failed." } finally { busy=false } } }
    suspend fun refresh() { val list=app.catalog.call("nova_engines") as JSONArray; engines=(0 until list.length()).map { list.getString(it) }; enabled=engines }
    LaunchedEffect(Unit) { run { refresh() } }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Torrent audio",fontSize=28.sp)
        Text("Search with standalone Nova engines or open a magnet. TorBox prepares the files; this TV streams audio without downloading the collection.",fontSize=13.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(token,{token=it},label={Text("TorBox API key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.weight(1f))
            Action("Connect",Icons.Rounded.Link,enabled=!busy) { run { app.vault.put("torbox_token",token.trim()); rows=app.catalog.tracks("torbox_list"); status="TorBox connected." } }
            Action("Disconnect",Icons.Rounded.LinkOff) { app.vault.put("torbox_token",null); token=""; rows=emptyList(); status="Disconnected. Remote torrents were kept." }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(query,{query=it},label={Text("Search music or paste a magnet / HTTPS .torrent URL")},singleLine=true,modifier=Modifier.weight(1f))
            Action("Search / open",Icons.Rounded.Search,enabled=!busy) { run {
                rows=if(query.startsWith("magnet:") || query.startsWith("https://")) app.catalog.tracks("torbox_files",JSONObject().put("url",query.trim()).toString()) else { require(enabled.isNotEmpty()) { "Select at least one engine." }; app.catalog.tracks("nova_search",query,JSONArray(enabled).toString()) }
            } }
        }
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(6.dp)) {
            items(engines) { name -> Action(name,selected=name in enabled) { enabled=if(name in enabled) enabled-name else enabled+name } }
            item { Action("Install Nova engine",Icons.Rounded.Extension) { install=true } }
            item { Action("My TorBox torrents",Icons.Rounded.CloudQueue,enabled=!busy) { run { rows=app.catalog.tracks("torbox_list") } } }
        }
        if(busy) CircularProgressIndicator()
        status?.let { Text(it,color=MaterialTheme.colorScheme.secondary) }
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(6.dp)) {
            itemsIndexed(rows,key={i,t -> "$i:${t.key}"}) { _,t -> Action("${t.title} · ${t.artist}",Icons.Rounded.MusicNote,modifier=Modifier.fillMaxWidth(),enabled=!busy) { if(t.kind=="torrent") run { rows=app.catalog.tracks("torbox_files",t.id) } else open(t) } }
        }
    }
    if(install) AlertDialog(onDismissRequest={install=false},title={Text("Install a trusted Nova engine")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Nova engines execute Python with this app’s permissions. Install only code you trust. Enter a direct HTTPS .py URL.")
        OutlinedTextField(engineUrl,{engineUrl=it},label={Text("Engine URL")},singleLine=true)
        engines.filter { it !in listOf("solidtorrents","torrentscsv") }.forEach { name -> Action("Uninstall $name",Icons.Rounded.Delete) { run { app.catalog.call("nova_remove",name); refresh() } } }
    }},confirmButton={Action("Trust and install",enabled=!busy) { install=false; run { app.catalog.call("nova_install",engineUrl.trim()); refresh(); status="Nova engine installed." } }},dismissButton={Action("Cancel") { install=false }})
}

@Composable fun PluginsScreen(app:MusikApplication,open:(Track)->Unit) {
    val scope=rememberCoroutineScope()
    var registry by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var installed by remember { mutableStateOf(JSONObject()) }
    var host by remember { mutableStateOf(JSONObject().put("providers",JSONArray()).put("settings",JSONArray())) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var trust by remember { mutableStateOf<JSONObject?>(null) }
    var setting by remember { mutableStateOf<JSONObject?>(null) }
    var dashboard by remember { mutableStateOf<List<Track>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    fun run(block:suspend ()->Unit) { if(busy) return;scope.launch { busy=true;status=null;try { block() } catch(e:Exception) { status=e.message ?: "Plugin operation failed." } finally { busy=false } } }
    suspend fun refresh() { installed=(app.catalog.call("plugins_status") as JSONObject).getJSONObject("installed");host=app.plugins.reload() }
    LaunchedEffect(Unit) { run { refresh();val a=app.catalog.call("plugins_registry") as JSONArray;registry=(0 until a.length()).map { a.getJSONObject(it) } } }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Music providers & plugins",fontSize=26.sp)
        Text("Nuclear providers run on this TV. Releases and npm dependencies are verified before local bundling. Desktop-only Node APIs and React settings widgets require Android adapters.",fontSize=12.sp)
        if(busy) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
        status?.let { Text(it,color=MaterialTheme.colorScheme.secondary) }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Action("Refresh",Icons.Rounded.Refresh,enabled=!busy) { run { refresh();val a=app.catalog.call("plugins_registry") as JSONArray;registry=(0 until a.length()).map { a.getJSONObject(it) } } }
            Action("Charts / discovery",Icons.Rounded.TrendingUp,enabled=!busy) { run { dashboard=app.plugins.dashboard();if(dashboard.isEmpty()) status="No dashboard items returned." } }
        }
        OutlinedTextField(filter,{filter=it},label={Text("Filter plugins and providers")},singleLine=true,modifier=Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(6.dp)) {
            item {
                Text("Active providers",fontSize=19.sp)
                val providers=host.optJSONArray("providers") ?: JSONArray()
                for(kind in listOf("metadata","streaming","lyrics","dashboard","playlists","discovery")) {
                    Text(kind.replaceFirstChar { it.uppercase() },modifier=Modifier.padding(top=8.dp))
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(6.dp)) {
                        item { Action("Musik default",selected=app.plugins.selected(kind)==null,enabled=!busy) { run { app.plugins.select(kind,"");refresh() } } }
                        items((0 until providers.length()).map { providers.getJSONObject(it) }.filter { it.optString("kind")==kind }) { p -> Action(p.optString("name",p.getString("id")),selected=app.plugins.selected(kind)==p.getString("id"),enabled=!busy) { run { app.plugins.select(kind,p.getString("id"));refresh() } } }
                    }
                }
            }
            item {
                Text("Installed",fontSize=19.sp)
                installed.keys().asSequence().toList().forEach { id ->
                    val item=installed.getJSONObject(id)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(vertical=6.dp)) {
                        Text(item.optString("name",id),modifier=Modifier.weight(1f))
                        Action(if(item.optBoolean("enabled",true)) "Disable" else "Enable",enabled=!busy) { run { app.catalog.call("plugins_enable",id,(!item.optBoolean("enabled",true)).toString());refresh() } }
                        Action("Uninstall",Icons.Rounded.Delete,enabled=!busy) { run { app.catalog.call("plugins_uninstall",id);val values=app.vault.read().optJSONObject("plugin_settings") ?: JSONObject();values.keys().asSequence().filter { it.startsWith("$id:") }.toList().forEach { values.remove(it) };app.vault.put("plugin_settings",values);refresh() } }
                    }
                    val failure=host.optJSONObject("failures")?.optString(id).orEmpty()
                    if(failure.isNotBlank()) Text(failure,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
                }
            }
            item {
                val definitions=host.optJSONArray("settings") ?: JSONArray()
                if(definitions.length()>0) Text("Provider settings",fontSize=19.sp)
                for(i in 0 until definitions.length()) {
                    val d=definitions.getJSONObject(i)
                    Action(d.optString("title",d.optString("label",d.getString("id"))),Icons.Rounded.Tune,modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)) { setting=d }
                }
            }
            items(dashboard) { t -> Action(t.title,Icons.Rounded.MusicNote,modifier=Modifier.fillMaxWidth()) { open(t) } }
            item { Text("Nuclear registry",fontSize=19.sp) }
            items(registry.filter { it.toString().contains(filter,ignoreCase=true) },key={it.getString("id")}) { entry ->
                Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(entry.optString("name",entry.getString("id")),fontSize=17.sp)
                    Text(entry.optString("description"),fontSize=12.sp,maxLines=3)
                    Action(if(installed.has(entry.getString("id"))) "Reinstall / update" else "Install",Icons.Rounded.Extension,enabled=!busy) { trust=entry }
                }
            }
        }
    }
    trust?.let { entry -> AlertDialog(onDismissRequest={trust=null},title={Text("Trust ${entry.optString("name",entry.getString("id"))}?")},text={Text("Plugins execute with this app’s provider, playback, and stored setting permissions. Install only code you trust. This installs the exact registry release after checksum verification; it does not run npm install scripts.")},confirmButton={Action("Trust and install") { trust=null;run { app.catalog.call("plugins_install",entry.getString("id"));refresh();status="Plugin installed. Review its providers and settings above." } }},dismissButton={Action("Cancel") { trust=null }}) }
    setting?.let { d ->
        val key=d.getString("pluginId")+":"+d.getString("id")
        var value by remember(key) { mutableStateOf((app.vault.read().optJSONObject("plugin_settings")?.opt(key) ?: d.opt("default") ?: "").toString()) }
        AlertDialog(onDismissRequest={setting=null},title={Text(d.optString("title",d.getString("id")))},text={Column {
            Text(d.optString("description"))
            OutlinedTextField(value,{value=it},label={Text(d.optString("kind","string"))},visualTransformation=PasswordVisualTransformation(),singleLine=true)
            Text("All settings are encrypted. Boolean values use true/false; enums use the provider’s option value.",fontSize=12.sp)
            Text(d.optJSONArray("options")?.toString().orEmpty(),fontSize=12.sp)
        }},confirmButton={Action("Save",enabled=!busy) { run {
            val v:Any=when(d.optString("kind")) { "boolean"->value.toBooleanStrict();"number"->value.toDouble();else->value }
            app.plugins.call("setting",JSONObject().put("plugin",d.getString("pluginId")).put("id",d.getString("id")).put("value",v));setting=null;refresh()
        } }},dismissButton={Action("Cancel") { setting=null }})
    }
}
