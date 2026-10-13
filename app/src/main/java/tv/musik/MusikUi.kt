package tv.musik

import android.app.Application
import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

private val Ink=Color(0xFF0B1018)
private val Panel=Color(0xFF171F2A)
private val Lime=Color(0xFFC6F580)
private val Muted=Color(0xFF9DA9B9)
private val Aqua=Color(0xFF76D8CF)
@Composable fun MusikTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Lime, onPrimary=Ink, secondary=Aqua, background=Ink, surface=Panel, onSurface=Color(0xFFF1F4F8), surfaceVariant=Color(0xFF26303E)),content=content)
}
data class CatalogPage(val title: String="", val rows: List<Track> = emptyList(), val busy: Boolean=false, val error: String?=null, val browsed: Boolean=false)
class MusikViewModel(application: Application): AndroidViewModel(application) {
    val app=application as MusikApplication
    val catalogPage=MutableStateFlow(CatalogPage())
    var query by mutableStateOf("")
    var category by mutableStateOf("song")
    var lyric by mutableStateOf<String?>(null)
    var lyricsBusy by mutableStateOf(false)
    private val history=mutableListOf<CatalogPage>()
    private var request: Job?=null
    private var serial=0
    private var retryAction: (() -> Unit)? = null
    private fun load(title: String, browsed: Boolean, block: suspend () -> List<Track>) {
        retryAction = { load(title, browsed, block) }
        request?.cancel(); val id=++serial
        catalogPage.value=CatalogPage(title=title,busy=true,browsed=browsed)
        request=viewModelScope.launch {
            try { val rows=block(); if(id==serial) catalogPage.value=CatalogPage(title,rows,browsed=browsed) }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(id==serial) catalogPage.value=CatalogPage(title=title,error=e.message ?: "Unable to load music.",browsed=browsed) }
        }
    }
    fun retry() { retryAction?.invoke() }
    fun search() { if(query.isBlank()) return; history.clear(); val q=query; val c=category; load("Results for “$q”",false) { app.catalog.tracks("search",q,c) } }
    fun browse(t: Track) { history.add(catalogPage.value); load(t.title,true) { app.catalog.tracks("browse",t.id,t.kind) } }
    fun back(): Boolean { if(history.isEmpty()) return false; request?.cancel(); serial++; catalogPage.value=history.removeAt(history.lastIndex); return true }
    fun radio(t: Track) { load("Radio · ${t.title}",true) { app.catalog.tracks("radio",t.id) } }
    fun lyrics(t: Track) { lyric=null; lyricsBusy=true; viewModelScope.launch { try { lyric=app.catalog.call("lyrics",t.id) as String } catch(e:Exception) { lyric=e.message ?: "Lyrics unavailable." } finally { lyricsBusy=false } } }
}

@Composable fun Action(label: String, icon: ImageVector?=null, selected: Boolean=false, modifier: Modifier=Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val foreground = if(focused) Ink else if(selected) Lime else Color.White
    TvButton(onClick=onClick, modifier=modifier.onFocusChanged { focused=it.isFocused }, colors=TvButtonDefaults.colors(containerColor=if(selected) Color(0xFF31412C) else Panel, contentColor=if(selected) Lime else Color.White, focusedContainerColor=Lime, focusedContentColor=Ink), contentPadding=PaddingValues(horizontal=16.dp,vertical=10.dp)) {
        if(icon!=null) { Icon(icon,contentDescription=null,tint=foreground,modifier=Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)) }
        Text(label,color=foreground,fontSize=14.sp,fontWeight=FontWeight.Medium,maxLines=1)
    }
}
@Composable private fun SectionTitle(title:String, subtitle:String?=null) {
    Text(title,fontSize=28.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-0.5).sp)
    if(subtitle!=null) Text(subtitle,color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=6.dp,bottom=14.dp))
}
@Composable private fun Empty(title:String,detail:String, icon: ImageVector=Icons.Rounded.LibraryMusic) {
    Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(icon,null,tint=Aqua,modifier=Modifier.size(42.dp)); Text(title,fontSize=20.sp,fontWeight=FontWeight.SemiBold); Text(detail,color=Muted,fontSize=14.sp)
    }
}
@Composable private fun Artwork(t: Track?, modifier: Modifier=Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF36494B),Color(0xFF1D263C),Color(0xFF29233B)))),contentAlignment=Alignment.Center) {
        Icon(Icons.Rounded.GraphicEq,null,tint=Aqua.copy(alpha=.5f),modifier=Modifier.fillMaxSize(.5f))
        if(t?.artwork?.isNotBlank()==true) AsyncImage(model=t.artwork,contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
    }
}
@Composable private fun TrackCard(t:Track, current:Boolean=false, onClick:()->Unit) {
    var focused by remember { mutableStateOf(false) }
    TvButton(onClick=onClick,modifier=Modifier.fillMaxWidth().onFocusChanged { focused=it.isFocused }, shape=TvButtonDefaults.shape(shape=RoundedCornerShape(16.dp)),colors=TvButtonDefaults.colors(containerColor=Panel,focusedContainerColor=Lime,focusedContentColor=Ink),contentPadding=PaddingValues(12.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Artwork(t,Modifier.fillMaxWidth().aspectRatio(1.6f))
            Spacer(Modifier.height(10.dp))
            Text(if(current) "▶ ${t.title}" else t.title,color=if(focused) Ink else Color.White,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
            Text(t.artist,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp,color=if(focused) Ink.copy(alpha=.7f) else Muted)
            Text(t.kind.uppercase(),fontSize=9.sp,letterSpacing=1.5.sp,color=if(focused) Ink.copy(alpha=.6f) else Aqua,modifier=Modifier.padding(top=8.dp))
        }
    }
}
@Composable private fun TrackGrid(rows:List<Track>,current:Track?,onClick:(Track)->Unit,modifier:Modifier=Modifier) {
    LazyVerticalGrid(columns=GridCells.Adaptive(170.dp),modifier=modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(4.dp,4.dp,4.dp,28.dp)) {
        itemsIndexed(rows,key={i,t -> "$i:${t.key}"}) { _,t -> TrackCard(t,t.key==current?.key) { onClick(t) } }
    }
}
@Composable fun MusikApp(vm:MusikViewModel=viewModel()) {
    val context=LocalContext.current
    val engine=vm.app.playback
    val playing by engine.state.collectAsStateWithLifecycle()
    val library by vm.app.library.state.collectAsStateWithLifecycle()
    val catalog by vm.catalogPage.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("Home") }
    var selected by remember { mutableStateOf<Track?>(null) }
    var selectedPlaylist by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryTab by rememberSaveable { mutableStateOf("Favorites") }
    var createPlaylist by remember { mutableStateOf(false) }
    var addPlaylist by remember { mutableStateOf<Track?>(null) }
    var showLyrics by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val focus=remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val importAudio=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val tracks=uris.mapNotNull { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val name=context.contentResolver.query(uri,null,null,null,null)?.use { cursor -> if(cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null } ?: "Local audio"
                Track(uri.toString(),name.substringBeforeLast('.'),"On this TV","local")
            } catch(_:Exception) { message="Some files could not be imported. Choose files from a provider that grants persistent access."; null }
        }
        vm.app.library.import(tracks)
    }
    BackHandler(enabled=page!="Home") {
        if(page=="Search" && vm.back()) Unit else if(selectedPlaylist!=null) selectedPlaylist=null else page="Home"
    }
    fun open(t:Track) { if(t.playable) selected=t else { vm.browse(t); page="Search" } }
    Row(Modifier.fillMaxSize().background(Ink).padding(28.dp,22.dp,28.dp,20.dp)) {
        Column(Modifier.width(164.dp).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(12.dp,8.dp,0.dp,22.dp)) { Icon(Icons.Rounded.GraphicEq,null,tint=Lime,modifier=Modifier.size(28.dp)); Spacer(Modifier.width(9.dp)); Text("musik",fontSize=27.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp) }
            val destinations=listOf("Home" to Icons.Rounded.Home,"Search" to Icons.Rounded.Search,"Library" to Icons.Rounded.LibraryMusic,"Queue" to Icons.AutoMirrored.Rounded.QueueMusic,"Now playing" to Icons.Rounded.Album,"Settings" to Icons.Rounded.Settings)
            destinations.forEachIndexed { i,(name,icon) -> Action(name,icon,page==name,Modifier.fillMaxWidth().then(if(i==0) Modifier.focusRequester(focus) else Modifier)) { page=name; selectedPlaylist=null } }
            Spacer(Modifier.weight(1f))
            Text("YOUR MUSIC.\nYOUR SPACE.",color=Muted,fontSize=10.sp,letterSpacing=1.5.sp,lineHeight=17.sp,modifier=Modifier.padding(12.dp))
            Text("STANDALONE • ANDROID TV",color=Aqua,fontSize=8.sp,letterSpacing=1.sp,modifier=Modifier.padding(start=12.dp))
        }
        Spacer(Modifier.width(30.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.weight(1f)) {
                when(page) {
                    "Home" -> LazyColumn(verticalArrangement=Arrangement.spacedBy(20.dp),contentPadding=PaddingValues(bottom=20.dp)) {
                        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Text("LISTENING ROOM",color=Muted,fontSize=11.sp,letterSpacing=2.sp); Text("Musik TV",color=Aqua,fontSize=12.sp) } }
                        item {
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xFF293D39),Color(0xFF18283A),Color(0xFF24223A)))).padding(30.dp)) {
                                Icon(Icons.Rounded.GraphicEq,null,tint=Aqua.copy(alpha=.12f),modifier=Modifier.align(Alignment.CenterEnd).size(170.dp))
                                Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                                    Text("SET THE MOOD",color=Lime,fontSize=11.sp,letterSpacing=2.sp)
                                    Text("A bigger screen.\nA deeper listen.",fontSize=36.sp,lineHeight=42.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp)
                                    Text("Find your next favorite. Make the room yours.",color=Muted,fontSize=14.sp)
                                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { Action("Find music",Icons.Rounded.Search,true) { page="Search" }; Action("Your library",Icons.Rounded.LibraryMusic) { page="Library" } }
                                }
                            }
                        }
                        item { Text("Pick a mood",fontSize=21.sp,fontWeight=FontWeight.SemiBold) }
                        item { LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(4.dp)) { items(listOf("Chill", "Jazz", "Focus", "Soul", "Electronic", "Classical")) { mood -> Action(mood,Icons.Rounded.Headphones) { vm.query=mood; vm.category="song"; vm.search(); page="Search" } } } }
                        if(library.history.isNotEmpty()) {
                            item { Text("Recently played",fontSize=21.sp,fontWeight=FontWeight.SemiBold) }
                            item { LazyRow(horizontalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(4.dp)) { items(library.history.take(12),key={it.key}) { t -> Box(Modifier.width(185.dp)) { TrackCard(t,t.key==playing.current?.key) { selected=t } } } } }
                        } else item { Text("Start with a search, or import audio from USB in your library.",color=Muted,fontSize=14.sp) }
                    }
                    "Search" -> Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        SectionTitle(if(catalog.browsed) catalog.title else "Find your sound")
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            OutlinedTextField(value=vm.query,onValueChange={vm.query=it},label={Text("Songs, artists, albums, or a YouTube link")},singleLine=true,modifier=Modifier.weight(1f),shape=RoundedCornerShape(14.dp),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Search),keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={vm.search()}))
                            Action("Search",Icons.Rounded.Search,true) { vm.search() }
                        }
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(4.dp)) {
                            items(listOf("song" to "Songs","album" to "Albums","artist" to "Artists","video" to "Videos")) { (key,label) -> Action(label,selected=vm.category==key) { vm.category=key; if(vm.query.isNotBlank()) vm.search() } }
                        }
                        if(catalog.browsed) Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { Action("Back",Icons.AutoMirrored.Rounded.ArrowBack) { vm.back() }; if(catalog.rows.any { it.playable }) Action("Play all",Icons.Rounded.PlayArrow,true) { engine.startQueue(catalog.rows); page="Now playing" } }
                        when {
                            catalog.busy -> Column(Modifier.fillMaxWidth().padding(40.dp),horizontalAlignment=Alignment.CenterHorizontally) { CircularProgressIndicator(color=Lime); Text("Finding your music…",color=Muted,modifier=Modifier.padding(16.dp)) }
                            catalog.error!=null -> { Empty("Couldn’t load music",catalog.error!!,Icons.Rounded.WifiOff); Action("Retry",Icons.Rounded.Refresh) { vm.retry() } }
                            catalog.rows.isEmpty() -> Empty(if(catalog.title.isBlank()) "Explore something new" else "No results",if(catalog.title.isBlank()) "Search by title, artist, or album using your remote." else "Try another title or switch categories.",Icons.Rounded.Search)
                            else -> TrackGrid(catalog.rows,playing.current,{ open(it) },Modifier.weight(1f))
                        }
                    }
                    "Library" -> Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        SectionTitle(selectedPlaylist ?: "Your collection", "Saved on this TV. Always within reach.")
                        if(selectedPlaylist!=null) {
                            val playlist=library.playlists.firstOrNull { it.name==selectedPlaylist }
                            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                Action("Back",Icons.AutoMirrored.Rounded.ArrowBack) { selectedPlaylist=null }
                                if(!playlist?.tracks.isNullOrEmpty()) Action("Play all",Icons.Rounded.PlayArrow,true) { engine.startQueue(playlist!!.tracks); page="Now playing" }
                                Action("Delete playlist",Icons.Rounded.DeleteOutline) { vm.app.library.delete(selectedPlaylist!!); selectedPlaylist=null }
                            }
                            if(playlist?.tracks.isNullOrEmpty()) Empty("An empty canvas", "Open a song’s actions to add it to this playlist.") else TrackGrid(playlist!!.tracks,playing.current,{selected=it},Modifier.weight(1f))
                        } else {
                            LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(4.dp)) {
                                items(listOf("Favorites","History","Local audio","Playlists")) { tab -> Action(tab,selected=libraryTab==tab) { libraryTab=tab } }
                            }
                            when(libraryTab) {
                                "Playlists" -> {
                                    Action("New playlist",Icons.Rounded.Add,true) { createPlaylist=true }
                                    if(library.playlists.isEmpty()) Empty("Make it personal", "Create a playlist, then add songs from search or your library.")
                                    LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(4.dp)) { items(library.playlists,key={it.name}) { p -> Action("${p.name} · ${p.tracks.size} tracks",Icons.AutoMirrored.Rounded.PlaylistPlay,modifier=Modifier.fillMaxWidth()) { selectedPlaylist=p.name } } }
                                }
                                else -> {
                                    val rows=when(libraryTab) { "Favorites" -> library.favorites; "History" -> library.history; else -> library.local }
                                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                        if(libraryTab=="Local audio") Action("Import audio",Icons.Rounded.FolderOpen,true) { try { importAudio.launch(arrayOf("audio/*")) } catch(_:Exception) { message="This TV has no file picker. Install a Storage Access Framework file manager to import USB audio." } }
                                        if(rows.isNotEmpty()) Action("Play all",Icons.Rounded.PlayArrow) { engine.startQueue(rows); page="Now playing" }
                                    }
                                    if(rows.isEmpty()) Empty("${libraryTab} will appear here",when(libraryTab) { "Favorites" -> "Open a song and choose Favorite to save it."; "History" -> "Your recent listening is saved automatically."; else -> "Import audio files from USB or the TV’s storage." }) else TrackGrid(rows,playing.current,{selected=it},Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    "Queue" -> Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        SectionTitle("Up next", "${playing.queue.size} tracks · Your listening, in your order")
                        if(playing.queue.isEmpty()) Empty("Ready when you are", "Open a song and choose Add to queue.",Icons.AutoMirrored.Rounded.QueueMusic)
                        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(4.dp,4.dp,4.dp,24.dp)) {
                            itemsIndexed(playing.queue,key={i,t -> "$i:${t.key}"}) { index,t -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text(if(index==playing.index) "▶" else "${index+1}",color=if(index==playing.index) Lime else Muted,modifier=Modifier.width(26.dp),fontSize=13.sp)
                                Action("${t.title} · ${t.artist}",selected=index==playing.index,modifier=Modifier.weight(1f)) { engine.select(index) }
                                Action("↑") { engine.move(index,-1) }; Action("↓") { engine.move(index,1) }; Action("Remove",Icons.Rounded.Close) { engine.remove(index) }
                            } }
                        }
                    }
                    "Now playing" -> PlayerScreen(playing,library,engine,onFavorite={playing.current?.let { vm.app.library.favorite(it) }},onLyrics={playing.current?.let { vm.lyrics(it); showLyrics=true }},onQueue={page="Queue"})
                    "Settings" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                        SectionTitle("Made for your living room","Musik TV · 0.1.0 · GPL-3.0-or-later")
                        SettingsCard("Standalone listening","Search and stream resolution run on this TV. No companion server, subscription to Musik, or computer is needed. YouTube availability depends on the provider and your region.")
                        SettingsCard("Your remote is in control","Use the directional pad to move between controls, OK to select, and Back to return. Play/Pause, Next, Previous, Rewind, and Fast-forward media keys work throughout the app.")
                        SettingsCard("Local music","Import audio in Library → Local audio. File access is granted by Android’s picker; no broad storage permission is requested. USB access depends on your TV’s file provider.")
                        SettingsCard("Provider compatibility","This version supports public YouTube Music browsing and streams that do not require account sign-in or desktop challenge helpers. Google account libraries, desktop Nuclear plugins, TorBox, and the desktop equalizer are not ported in this release.")
                        SettingsCard("Privacy & credits","Favorites, history, queues, and playlists stay in this app’s private storage. Requests go directly to the music provider. Adapted from deenodadon/Musik; unaffiliated with Google or YouTube. See the repository for source and licenses.")
                    }
                }
            }
            if(playing.current!=null && page!="Now playing") {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Artwork(playing.current,Modifier.size(42.dp))
                    Column(Modifier.weight(1f)) { Text(playing.current!!.title,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold,fontSize=14.sp); Text(if(playing.loading) "Loading…" else playing.error ?: playing.current!!.artist,maxLines=1,overflow=TextOverflow.Ellipsis,color=if(playing.error!=null) MaterialTheme.colorScheme.error else Muted,fontSize=11.sp) }
                    Action(if(playing.active) "Pause" else if(playing.loading) "Cancel" else "Play",if(playing.active) Icons.Rounded.Pause else Icons.Rounded.PlayArrow) { engine.toggle() }
                    Action("Next",Icons.Rounded.SkipNext) { engine.advance(1) }
                    Action("Open",Icons.Rounded.OpenInFull) { page="Now playing" }
                }
            }
        }
    }
    selected?.let { t ->
        AlertDialog(onDismissRequest={selected=null},title={Text(t.title)},text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(t.artist,color=Muted)
                Action("Play now",Icons.Rounded.PlayArrow,true,Modifier.fillMaxWidth()) { engine.startQueue(listOf(t)); selected=null; page="Now playing" }
                Action("Add to queue",Icons.AutoMirrored.Rounded.QueueMusic,modifier=Modifier.fillMaxWidth()) { engine.enqueue(t); selected=null }
                Action(if(library.favorites.any { it.key==t.key }) "Remove favorite" else "Favorite",Icons.Rounded.FavoriteBorder,modifier=Modifier.fillMaxWidth()) { vm.app.library.favorite(t); selected=null }
                Action("Add to playlist",Icons.AutoMirrored.Rounded.PlaylistAdd,modifier=Modifier.fillMaxWidth()) { addPlaylist=t; selected=null }
                if(t.kind in listOf("song","video")) Action("Start radio",Icons.Rounded.Radio,modifier=Modifier.fillMaxWidth()) { vm.radio(t); selected=null; page="Search" }
                if(page=="Library" && selectedPlaylist!=null) Action("Remove from playlist",Icons.Rounded.RemoveCircleOutline,modifier=Modifier.fillMaxWidth()) { vm.app.library.removeFromPlaylist(selectedPlaylist!!,t); selected=null }
                if(t.kind=="local") Action("Remove from library",Icons.Rounded.DeleteOutline,modifier=Modifier.fillMaxWidth()) { vm.app.library.removeLocal(t); selected=null }
            }
        },confirmButton={Action("Close") { selected=null }})
    }
    if(createPlaylist) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest={createPlaylist=false},title={Text("New playlist")},text={OutlinedTextField(value=name,onValueChange={name=it.take(80)},label={Text("Playlist name")},singleLine=true)},confirmButton={Action("Create",selected=true) { if(name.isNotBlank()) { vm.app.library.create(name); createPlaylist=false } }},dismissButton={Action("Cancel") { createPlaylist=false }})
    }
    addPlaylist?.let { t -> AlertDialog(onDismissRequest={addPlaylist=null},title={Text("Add to playlist")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) { if(library.playlists.isEmpty()) Text("Create a playlist in Library first.",color=Muted); LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) { items(library.playlists,key={it.name}) { p -> Action(p.name,Icons.AutoMirrored.Rounded.PlaylistAdd,modifier=Modifier.fillMaxWidth()) { vm.app.library.add(p.name,t); addPlaylist=null } } } }},confirmButton={Action("Close") { addPlaylist=null }}) }
    if(showLyrics) AlertDialog(onDismissRequest={showLyrics=false},title={Text("Lyrics")},text={if(vm.lyricsBusy) CircularProgressIndicator() else Text(vm.lyric ?: "No lyrics available.",modifier=Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState()))},confirmButton={Action("Close") { showLyrics=false }})
    message?.let { text -> AlertDialog(onDismissRequest={message=null},title={Text("Import audio")},text={Text(text)},confirmButton={Action("OK") { message=null }}) }
}
@Composable private fun SettingsCard(title:String,body:String) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(22.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(title,fontSize=18.sp,fontWeight=FontWeight.SemiBold); Text(body,color=Muted,fontSize=14.sp,lineHeight=22.sp) } }
private fun time(ms:Long):String { val sec=ms/1000; return "%d:%02d".format(sec/60,sec%60) }
@Composable private fun PlayerScreen(p:Playing,library:Library,engine:PlaybackEngine,onFavorite:()->Unit,onLyrics:()->Unit,onQueue:()->Unit) {
    if(p.current==null) { Empty("Let’s put something on", "Find a song in Search or play your local audio.",Icons.Rounded.Headphones); return }
    val t=p.current!!
    Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(30.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(.8f),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text("NOW PLAYING",color=Aqua,fontSize=11.sp,letterSpacing=2.sp)
            Artwork(t,Modifier.fillMaxWidth().aspectRatio(1f))
            Text(if(p.loading) "LOADING" else if(p.error!=null) "UNAVAILABLE" else if(p.active) "PLAYING" else "PAUSED",color=if(p.error!=null) MaterialTheme.colorScheme.error else Lime,fontSize=11.sp,letterSpacing=2.sp)
        }
        Column(Modifier.weight(1.3f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text(t.title,fontSize=32.sp,fontWeight=FontWeight.Bold,lineHeight=38.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
            Text(t.artist,color=Muted,fontSize=18.sp)
            if(p.error!=null) { Text(p.error,color=MaterialTheme.colorScheme.error,fontSize=14.sp); Action("Retry",Icons.Rounded.Refresh) { engine.playCurrent() } }
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(progress={if(p.duration>0) (p.position.toFloat()/p.duration).coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxWidth().height(5.dp),color=Lime,trackColor=Panel)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(time(p.position),color=Muted,fontSize=12.sp); Text(time(p.duration),color=Muted,fontSize=12.sp) }
            }
            LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(5.dp)) {
                item { Action("Previous",Icons.Rounded.SkipPrevious) { engine.advance(-1) } }
                item { Action(if(p.active) "Pause" else if(p.loading) "Cancel" else "Play",if(p.active) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,true) { engine.toggle() } }
                item { Action("Next",Icons.Rounded.SkipNext) { engine.advance(1) } }
            }
            LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(5.dp)) {
                item { Action("−10 sec",Icons.Rounded.Replay10) { engine.seek(-10000) } }; item { Action("+10 sec",Icons.Rounded.Forward10) { engine.seek(10000) } }
                item { Action("Shuffle",Icons.Rounded.Shuffle,p.shuffle) { engine.shuffle() } }
                item { Action("Repeat: ${listOf("Off","Song","Queue")[p.repeat]}",Icons.Rounded.Repeat,p.repeat!=0) { engine.repeat() } }
            }
            LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(5.dp)) {
                item { Action(if(library.favorites.any { it.key==t.key }) "Favorited" else "Favorite",Icons.Rounded.Favorite,library.favorites.any { it.key==t.key },onClick=onFavorite) }
                if(t.kind in listOf("song","video")) item { Action("Lyrics",Icons.Rounded.Lyrics,onClick=onLyrics) }
                item { Action("Queue",Icons.AutoMirrored.Rounded.QueueMusic,onClick=onQueue) }
                item { Action("Speed ${p.speed}×",Icons.Rounded.Speed) { engine.speed() } }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                Action("Volume −",Icons.AutoMirrored.Rounded.VolumeDown) { engine.volume(-.1f) }; Text("${(p.volume*100).toInt()}%",color=Muted,fontSize=13.sp); Action("Volume +",Icons.AutoMirrored.Rounded.VolumeUp) { engine.volume(.1f) }
            }
        }
    }
}
