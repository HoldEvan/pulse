# ---- shrink / optimization ----
-allowaccessmodification

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions

-dontnote **

-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn okio.**

# Eclipse Paho MQTT talks to itself via reflection and ServiceLoader
-keep class org.eclipse.paho.client.mqttv3.** { *; }

# Mozilla Rhino 通过类名字符串解析标准内建对象，反射不到就报 Cannot call native method
-keep class org.mozilla.javascript.NativeObject,
         org.mozilla.javascript.NativeArray,
         org.mozilla.javascript.NativeArrayIterator,
         org.mozilla.javascript.NativeString,
         org.mozilla.javascript.NativeDate,
         org.mozilla.javascript.NativeError,
         org.mozilla.javascript.NativePrimitiveError,
         org.mozilla.javascript.NativeGlobal,
         org.mozilla.javascript.NativeJSON,
         org.mozilla.javascript.NativeMath,
         org.mozilla.javascript.NativeNumber,
         org.mozilla.javascript.NativeBoolean,
         org.mozilla.javascript.NativePrimitive,
         org.mozilla.javascript.NativeWith,
         org.mozilla.javascript.NativeFunction,
         org.mozilla.javascript.ScriptableObject,
         org.mozilla.javascript.Function,
         org.mozilla.javascript.NativeJavaObject,
         org.mozilla.javascript.NativeJavaClass,
         org.mozilla.javascript.NativeJavaMap,
         org.mozilla.javascript.NativeJavaList { *; }
-keepclassmembers class org.mozilla.javascript.ScriptableObject {
    boolean hasProperty(int, org.mozilla.javascript.Scriptable);
}
-keep class org.mozilla.javascript.IndicatingInputStream,
         org.mozilla.javascript.IndicatingReader { *; }
-keep class org.mozilla.javascript.IdenticalMap { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.ow2.asm.**

