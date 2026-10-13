package tv.musik

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.*
import android.content.Context
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs yt-dlp's verified EJS code in a separate WebView, with no credential bridge. */
class ChallengeSolver(private val context:Context) {
    private val main=Handler(Looper.getMainLooper())
    @SuppressLint("SetJavaScriptEnabled")
    fun run(script:String):String {
        check(Looper.myLooper()!=Looper.getMainLooper()) { "Challenge solving must run on a background thread." }
        require(script.length<15_000_000) { "Challenge payload exceeds its size limit." }
        val done=CountDownLatch(1);var output:String?=null;var web:WebView?=null
        val bridge=object {
            @JavascriptInterface fun complete(value:String) { if(output==null) { output=value;done.countDown() } }
        }
        main.post {
            try {
                web=WebView(context).apply {
                    settings.javaScriptEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false;settings.domStorageEnabled=false
                    addJavascriptInterface(bridge,"Solver")
                    webViewClient=object:WebViewClient() {
                        override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=true
                        override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest)=WebResourceResponse("text/plain","UTF-8",java.io.ByteArrayInputStream(ByteArray(0)))
                        override fun onPageFinished(view:WebView,url:String) {
                            val safe="(()=>{const console={log:v=>Solver.complete(String(v)),debug:()=>{},warn:()=>{},error:()=>{}};try { $script } catch(e) { Solver.complete(JSON.stringify({type:'error',error:'Android JavaScript challenge failed. Update Android System WebView or try another upload.'})); }})();"
                            view.evaluateJavascript(safe,null)
                        }
                    }
                    loadDataWithBaseURL("https://solver.androidplatform.net/","<!doctype html><html></html>","text/html","UTF-8",null)
                }
            } catch(_:Exception) { output="{\"type\":\"error\",\"error\":\"Android System WebView could not start.\"}";done.countDown() }
        }
        try {
            check(done.await(45,TimeUnit.SECONDS)) { "Android JavaScript challenge timed out." }
            return output ?: error("Challenge returned no result.")
        } finally { main.post { web?.removeJavascriptInterface("Solver");web?.destroy() } }
    }
}
