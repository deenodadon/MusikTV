"""Android JS runtime adapter. EJS version and hash checks remain enabled."""
from yt_dlp.extractor.youtube.jsc._builtin.ejs import EJSBaseJCP
from yt_dlp.extractor.youtube.pot._provider import BuiltinIEContentProvider
from yt_dlp.extractor.youtube.jsc.provider import register_provider,register_preference,JsChallengeProviderError
from yt_dlp.utils._jsruntime import JsRuntimeInfo

solver=None

@register_provider
class AndroidWebViewJCP(EJSBaseJCP,BuiltinIEContentProvider):
    PROVIDER_NAME='androidwebview'
    JS_RUNTIME_NAME='android-webview'
    @property
    def runtime_info(self):
        return JsRuntimeInfo(name='android-webview',path='in-process WebView',version='Android System WebView',version_tuple=()) if solver is not None else None
    def is_available(self):return solver is not None and self._available
    def _run_js_runtime(self,stdin):
        if solver is None:raise JsChallengeProviderError('Android WebView challenge runtime is unavailable')
        return str(solver.run(stdin))

@register_preference(AndroidWebViewJCP)
def preference(provider,requests):return 1000
