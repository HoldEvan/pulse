# 技术设计：拨测 HTTP 探测补全（#17）

## 方案

沿用既有分层：纯逻辑放 domain/data 可测层，UI 走 Compose + 现有设计组件。

### 1. 模型扩展

`data/model/MonitorModels.kt` — `MonitorTarget` 新增（全部带默认值，旧快照无需迁移）：

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| method | String | "GET" | 存大写方法文本，支持任意自定义方法 |
| headers | List\<KeyValueEntry\> | 空 | 复用集合键值条目（key/value/enabled） |
| bodyType | String | "NONE" | NONE / JSON / RAW |
| bodyText | String | "" | 请求体文本，支持 {{变量}} |
| authType | String | "NONE" | NONE / BEARER / BASIC / APIKEY |
| bearerToken / basicUsername / basicPassword / apiKeyName / apiKeyValue | String | "" | 鉴权凭据 |

方法与类型存 String 而非枚举：与 HttpMethod 值类（`parse` 接受任意方法）对齐，避免枚举扩张。

### 2. 探测执行

`data/monitor/MonitorChecker.kt` — `probeHttp` 重写：

- 新增 `resolveText`（占位符解析）与内部 `ResolvedText` sealed interface；URL、头键、头值、请求体逐项解析，缺失变量立即 `failed(monitor_missing_vars)`。
- 请求头：跳过禁用与空键条目，解析后转 `KeyValue`；再经 `ServiceLocator.authApplier.apply(AuthConfig(...))` 追加鉴权头（Bearer→Authorization: Bearer x；Basic→Base64；API Key→自定义头，空名回退 X-API-Key）。
- 请求体：`bodyType != NONE && bodyText 非空 && 方法非 GET/HEAD` 时发送；`parseBodyType`/`parseAuthType` 大写归一化，非法值回退 NONE。
- `HttpCallConfig(method, url, headers, bodyType, body, timeoutMs, proxy)`；状态码判定与慢响应标记逻辑不变。
- 删除不再使用的 `resolveUrl` 与 `ResolvedUrl`。

### 3. 持久化

`data/persistence/AppStatePersistence.kt`：

- `parseMonitorTarget`：读取新字段，全部 `stringValue(longValue, 默认值)` 兜底；headers 数组逐项解析 key/value/enabled。
- 保存：`monitors` 数组写入 method/headers/bodyType/bodyText/authType/凭据字段。

### 4. ViewModel

`ui/monitor/MonitorViewModel.kt`：

- 新增 `method/headers/bodyType/bodyText/authType/bearerToken/basicUsername/basicPassword/apiKeyName/apiKeyValue` mutableStateOf；headers 用 domain `KeyValue` 便于复用 KeyValueEditor。
- `openCreate` 重置默认；`openEdit` 从目标回填（headers 映射 KeyValueEntry→KeyValue）。
- `draft()`：method 大写归一、过滤空键头并映射回 KeyValueEntry。

### 5. UI

`ui/monitor/MonitorScreen.kt` — HTTP 分支内状态码行之后追加：

- `MethodPicker`（含自定义方法入口与校验）。
- `CollapsibleSection` + `KeyValueEditor`（请求头，显示有效头数量）。
- `AppDropdown` 请求体类型（无/JSON/Raw），非 NONE 时显示请求体输入框。
- `AppDropdown` 鉴权（无/Bearer/Basic/API Key），按选择条件渲染 Token、用户名+密码、Header 名+Key 值输入。
- IP / 端口类型不渲染以上区块（沿用现有分支结构）。

### 6. 文案

三语 strings 新增 4 条（monitor_method_label / monitor_headers_label / monitor_body_label / monitor_auth_label），复用 auth_*、body_*、kv_header_*、username/password、apikey_* 既有条目；monitor_url_label 描述去掉「只支持 GET」并标注支持变量。

## 测试

`MonitorCheckerTest` 新增 CapturingEngine 与替换式 resolver：

1. check 发送方法/头（含变量替换、跳过空键与禁用项）/鉴权头/JSON 体。
2. GET 忽略请求体。
3. 请求体存在未定义变量时检查失败且不发请求。
