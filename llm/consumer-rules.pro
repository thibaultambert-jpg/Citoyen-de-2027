# JNI: names looked up from native code (src/main/cpp/citoyen_llm.cpp).
-keep class fr.citoyende2027.llm.engine.LlamaNative { *; }
-keep interface fr.citoyende2027.llm.engine.LlamaNative$TokenSink { *; }
-keepclassmembers class * implements fr.citoyende2027.llm.engine.LlamaNative$TokenSink {
    public void onPrompt(int);
    public boolean onPiece(byte[]);
}
