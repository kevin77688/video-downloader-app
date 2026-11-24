# App code uses no reflection; the rules below are for NewPipeExtractor (YouTube) and its dependencies,
# following https://github.com/TeamNewPipe/NewPipe/blob/dev/app/proguard-rules.pro

-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }

# Rhino (JavaScript engine used to decipher YouTube stream URLs) relies on reflection.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**

# Protobuf-lite reads message fields reflectively.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}
