# 需求实施计划

- [x] 1. 跳过校验的工具（需求 1.2-1.3）
  - `InsecureTls`：信任管理器、SSLSocketFactory、主机名校验器
  - `OkHttpClient.Builder.applyInsecureTls(enabled)` 扩展
  - 补 3 项单测
- [x] 2. 缓存隔离（需求 1.4、4.1-4.2）
  - `OkHttpEngine` / `KtorWsEngine` / `KtorSseEngine` 缓存键改为 `ClientKey(proxy, insecureTls)`
  - 淘汰策略沿用最早创建
- [x] 3. 配置与快照（需求 3.1-3.3）
  - `HttpCallConfig` / `WsConfig` / `SseConfig` 增加 `insecureTls`
  - 三个快照同步增加字段，默认关闭
- [x] 4. 界面（需求 1.1、2.1-2.3）
  - 新增 `InsecureTlsRow` 共用组件
  - HTTP 网络标签、WS 连接卡片、SSE 网络标签各接入
  - 开启时以警示色显示风险说明
  - 三语文案
- [x] 5. 检查点 - 确保所有测试通过
  - 全量单测 314 项
  - Release 打包并覆盖根目录 APK
