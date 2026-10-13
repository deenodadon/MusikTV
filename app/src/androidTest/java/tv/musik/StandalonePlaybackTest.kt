package tv.musik

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class StandalonePlaybackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun localAudioCanBePlayedFromTheTvLibraryAndSavedAsFavorite() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MusikApplication
        val health=runBlocking { app.catalog.call("diagnostics") as JSONObject }
        assertTrue(health.getBoolean("catalog_loaded"))
        assertTrue(health.getBoolean("resolver_loaded"))
        // Real PCM audio fixture: no server, no media URI stub, no fake player.
        val file=File(app.filesDir,"test-tone.wav")
        val sampleRate=8000; val samples=sampleRate*30; val bytes=samples*2
        val buffer=ByteBuffer.allocate(44+bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()); buffer.putInt(36+bytes); buffer.put("WAVEfmt ".toByteArray()); buffer.putInt(16)
        buffer.putShort(1); buffer.putShort(1); buffer.putInt(sampleRate); buffer.putInt(sampleRate*2); buffer.putShort(2); buffer.putShort(16)
        buffer.put("data".toByteArray()); buffer.putInt(bytes)
        repeat(samples) { i -> buffer.putShort((kotlin.math.sin(2*Math.PI*440*i/sampleRate)*1000).toInt().toShort()) }
        file.writeBytes(buffer.array())
        val track=Track(file.toURI().toString(),"Offline test tone","Device test","local")
        compose.runOnUiThread { app.library.import(listOf(track)) }
        compose.onNodeWithText("Library",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Local audio",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Offline test tone",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Play now",useUnmergedTree=true).performClick()
        compose.waitUntil(30000) { app.playback.state.value.active && app.playback.state.value.position > 1000 }
        assertNull(app.playback.state.value.error)
        assertEquals(track.key,app.playback.state.value.current?.key)
        val beforeBackground=app.playback.state.value.position
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitUntil(15000) { app.playback.state.value.active && app.playback.state.value.position > beforeBackground+1000 }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Favorite",useUnmergedTree=true).performClick()
        assertTrue(LibraryStore(app).state.value.favorites.any { it.key==track.key })
        compose.onNodeWithText("Pause",useUnmergedTree=true).performClick()
        compose.waitUntil(10000) { !app.playback.state.value.active }
        compose.runOnUiThread { app.library.favorite(track); app.library.removeLocal(track); app.playback.remove(0) }
        file.delete()
    }
}
