# 需求实施计划 — API Tester

- [x] 1. 工程初始化与核心接口
  - 初始化 Android 单模块工程骨架：Gradle Kotlin DSL、AGP + Kotlin + Compose 插件、minSdk 26 / targetSdk 最新稳定
  - 接入依赖：Miuix `top.yukonga.miuix.kmp:miuix-ui-android` 0.9.x、Ktor Client(okhttp 引擎)、Eclipse Paho `org.eclipse.paho.client.mqttv3`、Room、DataStore、kotlinx.serialization、Compose BOM
  - 创建包结构：`ui/{http,ws,mqtt,env,history,project}`、`domain`、`data/{local,remote,repo}`
  - 定义核心接口：PlaceholderResolver、HttpEngine、WsEngine、MqttEngine、Repository 层接口（对应 design.md 组件接口定义）
  - 配置测试框架依赖（JUnit、kotlinx-coroutines-test、Room-testing）

- [ ] 2. 数据模型与 Room 持久化（design.md Data Models，需求 R3/R4/R7）
  - [ ] 2.1 定义 Room 实体：Project、Environment、EnvironmentVariable、RequestCollection、SavedRequest、HttpHistory、WsSessionConfig、WsMessage、MqttSessionConfig、MqttMessage，含外键级联删除
  - [ ] 2.2 实现 DAO：各实体 CRUD、按项目过滤查询、历史 500 条清理查询（R7.2）、单会话消息 1000 条清理查询（R7.3）
  - [ ] 2.3 实现 AppDatabase、kotlinx.serialization TypeConverter、DataStore（当前项目 ID、UI 偏好）、首启种子数据（默认项目 + default 环境）
  - [ ] 2.4* 编写 DAO 测试：历史/消息清理上限、每项目激活环境唯一性、项目删除级联（正确性属性 2/3/4）

- [ ] 3. Domain 层组件（design.md Domain，需求 R2/R3/R4）
  - [ ] 3.1 实现 PlaceholderResolver 占位符引擎：正则匹配、Missing 结果，覆盖 URL/请求头/查询参数/请求体/WS 消息/MQTT 负载（R3.6）
  - [ ] 3.2 实现 AuthApplier：Bearer 与 Basic 认证头生成（R2.2，正确性属性 5）
  - [ ] 3.3 实现 PostmanConverter v2.1：item 递归导入为项目→集合→请求、导出当前项目集合（R4.6/R4.7，正确性属性 8）
  - [ ] 3.4* PlaceholderResolver 属性测试：任意文本占位符全部替换或显式 Missing（正确性属性 1，R3.5）
  - [ ] 3.5* PostmanConverter 导入→导出 round-trip 属性测试（正确性属性 8）

- [ ] 4. 检查点 — 确保所有测试通过,如有疑问请询问用户

- [ ] 5. 网络层实现（design.md 组件接口，需求 R1/R5/R6）
  - [ ] 5.1 Ktor HttpClient 单例：连接/读取超时、Cookie 存储（R2.3）、请求取消支持（R1.3）
  - [ ] 5.2 实现 HttpEngine：GET/POST/PUT/DELETE/PATCH/HEAD 六方法、JSON/纯文本/urlencoded/form-data（含文件）四种 body、认证应用（R1.1/R1.4）
  - [ ] 5.3 实现 WsEngine：ws/wss、子协议与自定义头、events SharedFlow（Connected/TextReceived/Sent/Closed）、断开后可重连（R5.1-R5.5，正确性属性 7）
  - [ ] 5.4 实现 MqttEngine：Paho MqttAsyncClient 协程封装、tcp/ssl、clientId/凭据/KeepAlive、订阅与发布（QoS 0/1/2、通配符）、Paho 错误码到语义描述映射（R6.1-R6.7）
  - [ ] 5.5* ViewModel 状态机测试：HTTP 发送 Idle→Loading→Success/Error 与缺失变量分支（R1.2/R3.5）

- [ ] 6. Repository 层（design.md Architecture，需求 R3/R4/R7）
  - [ ] 6.1 ProjectRepository：项目 CRUD、当前项目切换（DataStore 持久化）、删除项目级联确认数据（R3.1/R3.7/R4.2）
  - [ ] 6.2 EnvironmentRepository：环境与变量 CRUD、激活环境切换事务（每项目唯一，正确性属性 2，R3.3）
  - [ ] 6.3 RequestRepository：集合与请求 CRUD、按项目过滤、Postman 导入导出入口（R4.1/R4.6/R4.7）
  - [ ] 6.4 HistoryRepository：写入+500 条自动清理、关键字搜索、清空（R4.3/R7.2/R7.4）
  - [ ] 6.5 SessionRepository：WS/MQTT 会话配置与消息流落库、1000 条清理（R7.1/R7.3）

- [x] 7. 应用骨架与导航（design.md 页面组件，需求 R8/R3）
  - [ ] 7.1 MainActivity + MiuixApp：MiuixTheme 跟随系统深色模式（R8.3）、Scaffold + NavigationBar 五页底部导航（R8.2）
  - [ ] 7.2 全局顶栏项目切换器与项目管理对话框：新建/重命名/删除二次确认（R3.1/R3.7/R4.2/R8.4）

- [ ] 8. HTTP 调试页（需求 R1/R2/R3）
  - [ ] 8.1 HttpViewModel：发送状态机、占位符解析与缺失变量分支、历史写入、取消请求
  - [ ] 8.2 请求区：方法选择 + URL 输入 + 发送按钮防重复禁用（正确性属性 6）、环境切换器（R3.3）
  - [ ] 8.3 Params/Headers 键值对编辑卡、Content-Type 快捷选择自动填充请求头（R2.1/R2.4）
  - [ ] 8.4 Body 编辑器：JSON/纯文本/urlencoded/form-data 含 SAF 文件选择（R1.4）、Bearer/Basic 认证编辑（R2.2）
  - [ ] 8.5 响应展示卡：状态码徽标/耗时/大小、JSON raw/pretty 双模式高亮（R1.5）、网络错误卡保留配置（R1.6）
  - [ ] 8.6 保存请求对话框：选择项目内集合保存命名请求（R4.1）

- [ ] 9. WebSocket 测试页（需求 R5）
  - [ ] 9.1 WsViewModel：连接状态流、收发消息合并写入消息流（占位符替换，R5.4/R3.6）
  - [ ] 9.2 WsScreen：连接配置卡（URL/子协议/头，R5.1）、状态徽标（R5.2）、连接/断开、消息输入行、收发分色时间戳消息流（R5.3）、JSON 格式化查看（R5.6）、断开原因系统行（R5.5）

- [ ] 10. MQTT 测试页（需求 R6）
  - [ ] 10.1 MqttViewModel：连接事件流、订阅状态、发布、连接耗时统计（R6.2）
  - [ ] 10.2 MqttScreen：连接配置卡（broker/clientId/凭据/KeepAlive，R6.1）、订阅列表卡（Topic+QoS+通配符，R6.3/R6.5）、发布卡（QoS 0/1/2，R6.4）、消息流（topic/qos/方向）、错误原因展示（R6.7）、断开保留消息流（R6.6）

- [ ] 11. 环境管理页（需求 R3）
  - [ ] 11.1 EnvViewModel：按当前项目过滤、变量编辑状态、激活环境切换
  - [ ] 11.2 EnvironmentScreen：环境列表卡（激活开关）、变量编辑卡、新建/删除二次确认（R3.2/R3.7/R8.4）

- [ ] 12. 历史页（需求 R4/R7）
  - [ ] 12.1 HistoryViewModel：按项目过滤、关键字搜索、点击载入编辑器
  - [ ] 12.2 HistoryScreen：搜索框、列表卡（方法徽标/状态码/耗时/时间）、载入 HttpScreen、按集合浏览、清空历史二次确认（R4.4/R4.5/R7.4）

- [ ] 13. Postman 导入导出 UI（需求 R4.6/R4.7）
  - [ ] 13.1 SAF 集成：CreateDocument/OpenDocument，导出当前项目集合、导入到指定项目
  - [ ] 13.2 结果反馈：成功计数提示、格式错误 Dialog 含首个出错字段（design.md Error Handling 表）

- [ ] 14. 检查点 — 确保所有测试通过,如有疑问请询问用户；并按 requirements.md R1-R8 与 design.md 正确性属性 1-8 逐条核对
