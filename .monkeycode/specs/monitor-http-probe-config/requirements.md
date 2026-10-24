# 需求：拨测 HTTP 探测补全（#17）

## 背景

路线图 P1 #17。拨测的接口（HTTP）类型目前只发一个不带参数的 GET，用状态码区间判定。线上接口多为 POST + 请求头 + 请求体 + 鉴权，监控这类接口必须先补齐请求配置能力。

## EARS 需求

### 请求配置

- WHEN 用户在拨测编辑器选择 HTTP 类型，THE 系统 SHALL 提供：请求方法（含自定义方法）、请求头键值对、请求体类型（无 / JSON / Raw）与请求体文本、鉴权方式（无 / Bearer / Basic / API Key）及其凭据字段。
- WHEN 目标类型为 IP 或端口，THE 系统 SHALL 隐藏上述 HTTP 配置项。

### 探测执行

- WHEN 执行 HTTP 拨测，THE 系统 SHALL 使用目标配置的方法、请求头、请求体与鉴权构造请求；GET 与 HEAD 请求忽略请求体。
- WHEN URL、请求头键值或请求体中含 `{{变量}}`，THE 系统 SHALL 用项目变量解析后发送；存在未定义变量时 SHALL 判定本次检查失败并在详情中列出缺失变量名。
- WHEN 鉴权方式为 Bearer/Basic/API Key，THE 系统 SHALL 按 HTTP 页一致的规则生成 Authorization 或自定义头，且用户头与鉴权头共存时 SHALL 同时发送。
- WHEN 状态判定，THE 系统 SHALL 沿用期望状态码区间与延迟阈值逻辑，语义与既有行为一致。

### 持久化

- WHEN 保存拨测目标，THE 系统 SHALL 将新增字段（method、headers、bodyType、bodyText、authType 及凭据）写入应用数据文件并在重启后恢复。
- WHEN 数据文件缺少新增字段（旧版本数据），THE 系统 SHALL 按默认值恢复（GET、无头、无请求体、无鉴权）。

## 非目标

- MQTT 拨测的 TLS/代理能力（路线图 #25）。
- 拨测响应体断言（路线图 #18）。
