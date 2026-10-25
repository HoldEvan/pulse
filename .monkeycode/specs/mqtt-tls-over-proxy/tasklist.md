# 任务列表：MQTT over TLS 走代理（#25）

- [x] T1 ProxyTunnel 抽取共用握手并改造 ProxySocket（需求 1.1、1.2、4.2）
- [x] T2 MqttTls scheme 判定、端点解析与工厂选择（需求 1.4、1.5、1.6、2.1、2.2）
- [x] T3 ProxyTlsSocketFactory 在代理隧道之上叠加 TLS（需求 1.1、1.2、1.3）
- [x] T4 PahoMqttEngine 支持 ssl 走代理与跳过校验（需求 1.1、2.1、2.3）
- [x] T5 MqttConfig / MqttSnapshot / ViewModel / 界面接入 insecureTls（需求 2.4、2.5、3.1）
- [x] T6 单测 13 项（需求 1.1、1.3、1.4、1.5、1.6、2.1、2.2）
