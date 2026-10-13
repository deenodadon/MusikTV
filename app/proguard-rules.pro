# Python invokes this method through Java reflection by its original name.
-keep class tv.musik.ChallengeSolver { public java.lang.String run(java.lang.String); }

# Preserve both isolated WebView bridges' JavaScript-visible method names.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes RuntimeVisibleAnnotations
