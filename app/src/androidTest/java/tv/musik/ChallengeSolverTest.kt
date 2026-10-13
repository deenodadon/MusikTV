package tv.musik

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChallengeSolverTest {
    @Test fun isolatedWebViewReturnsJavaScriptResultsToTheResolver() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = ChallengeSolver(context).run(
            "console.log(JSON.stringify({type:'result',responses:[{type:'result',data:'bridge-ok'}]}));"
        )
        assertEquals("bridge-ok", JSONObject(result).getJSONArray("responses").getJSONObject(0).getString("data"))
    }
}
