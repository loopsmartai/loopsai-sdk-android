-keep class com.loopsai.chat.internal.JavaScriptInterfaceBridge {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.loopsai.chat.LoopsAIChatConfig { *; }
-keep class com.loopsai.chat.LoopsAIChatContext { *; }

-keepclassmembers class com.loopsai.chat.LoopsAIChatConfig {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class com.loopsai.chat.LoopsAIChatContext {
    public static final android.os.Parcelable$Creator CREATOR;
}
