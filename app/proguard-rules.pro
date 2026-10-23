# ---- shrink / optimization ----
-allowaccessmodification

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions

-dontnote **

-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn okio.**

# Eclipse Paho MQTT talks to itself via reflection and ServiceLoader
-keep class org.eclipse.paho.client.mqttv3.** { *; }

# Mozilla Rhino 通过类名字符串解析标准内建对象，反射不到就报 Cannot call native method。
# 解释执行（optimizationLevel = -1）会用到 Parser、TokenStream、AstRoot 等解析类，
# 以及大量 Native*/内部类。这些类由 Rhino 内部按名字加载，R8 收缩/混淆掉会触发
# ClassNotFoundException -> NoClassDefFoundError -> ExceptionInInitializerError。
-keep class org.mozilla.javascript.** { *; }
-keepclassmembers class org.mozilla.javascript.ScriptableObject {
    boolean hasProperty(int, org.mozilla.javascript.Scriptable);
}
-keep class org.mozilla.javascript.IndicatingInputStream,
         org.mozilla.javascript.IndicatingReader { *; }
-keep class org.mozilla.javascript.IdenticalMap { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.ow2.asm.**


# WorkManager 按类名反射实例化 Worker，R8 不能收缩或改名这个类
-keep class com.evan.pulse.data.monitor.MonitorWorker { *; }
# 同上，自动备份 Worker 也由 WorkManager 反射实例化
-keep class com.evan.pulse.data.backup.AutoBackupWorker { *; }
