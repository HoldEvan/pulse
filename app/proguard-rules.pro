# ---- shrink / optimization ----
-allowaccessmodification

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions

-dontnote **

-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn okio.**

# HiveMQ MQTT Client (Netty)：按官方 Android 说明保留字段名。
# Netty / JCTools 用 AtomicXxxFieldUpdater 与 Unsafe 按字段名字符串反射访问，
# R8 改名后运行时才会抛 NoSuchFieldException，编译期发现不了。
-keepclassmembernames class io.netty.** { *; }
-keepclassmembers class org.jctools.** { *; }
# 以下都是 Netty 运行时探测的可选集成（epoll、tcnative、log4j、Jetty ALPN、BlockHound），
# 类不存在时自动回退到 NIO 与 JDK TLS，Android 上本就不可用
-dontwarn io.netty.channel.epoll.**
-dontwarn io.netty.internal.tcnative.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.eclipse.jetty.alpn.**
-dontwarn org.eclipse.jetty.npn.**
-dontwarn reactor.blockhound.**

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

# sora-editor 的 TextMate 引擎用 Gson 反射解析语法/主题 JSON，
# 模型类字段名参与反序列化，混淆后字段名对不上会解析失败（高亮全部丢失）
-keep class org.eclipse.tm4e.** { *; }
-keep class io.github.rosemoe.sora.langs.textmate.** { *; }
-keep class io.github.rosemoe.sora.widget.CodeEditor { *; }
-keep class org.joni.** { *; }
-dontwarn org.eclipse.tm4e.**
-dontwarn org.joni.**
-dontwarn org.jcodings.**
