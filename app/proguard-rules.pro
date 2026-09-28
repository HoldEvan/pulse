# Keep Paho MQTT client internals used via reflection
-keep class org.eclipse.paho.client.mqttv3.** { *; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
