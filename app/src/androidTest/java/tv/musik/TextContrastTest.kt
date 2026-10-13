package tv.musik

import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextContrastTest {
    @get:Rule val compose=createComposeRule()
    @Test fun defaultUiTextInheritsLightContentColorOnTheDarkBackground() {
        var layout:TextLayoutResult?=null
        compose.setContent { MusikTheme { Text("Readable UI",onTextLayout={layout=it}) } }
        compose.waitForIdle()
        assertEquals(Color(0xFFF1F4F8),layout!!.layoutInput.style.color)
    }
}
