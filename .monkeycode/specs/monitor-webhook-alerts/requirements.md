# 需求：拨测 Webhook 告警（#20）

## 背景

路线图 P1 #20。当前拨测状态变化只有系统通知，无法推送到团队群。需要支持企业微信、钉钉、飞书、Slack 与通用 Webhook，并允许自定义消息模板。

## EARS 需求

### 配置

- WHEN 用户在设置页启用「告警 Webhook」，THE 系统 SHALL 展示 Webhook 地址、消息格式（通用/企业微信/钉钉/飞书/Slack）、自定义模板与「发送测试」按钮。
- WHEN 地址留空，THE 系统 SHALL 不发送任何 Webhook，且「发送测试」提示失败。
- WHEN 模板留空，THE 系统 SHALL 使用所选格式的默认模板：通用格式为 JSON 请求体默认值，其他格式为纯文本文案默认值。
- WHEN 用户点击「发送测试」，THE 系统 SHALL 立即发送一条测试消息并显示成功或失败提示，无需等待真实告警。

### 发送

- WHEN 拨测状态发生「通→断」「通/断→慢」「断/慢→通」变化且已启用 Webhook，THE 系统 SHALL 向配置地址 POST 一次 JSON 请求，状态分别为故障、缓慢、恢复。
- WHEN 持续异常或持续正常，THE 系统 SHALL 不重复发送，状态机与系统通知保持一致。
- WHEN 消息发送，THE 系统 SHALL 附带 `Content-Type: application/json; charset=utf-8`，超时 10 秒，并复用应用代理设置。
- WHEN 发送失败或异常，THE 系统 SHALL 静默忽略，不影响拨测记录与系统通知。
- WHEN 格式为企业微信或钉钉，请求体 SHALL 为 `{"msgtype":"text","text":{"content":"..."}}`；飞书为 `{"msg_type":"text","content":{"text":"..."}}`；Slack 为 `{"text":"..."}`；通用格式 SHALL 直接使用模板作为完整请求体。
- WHEN 模板含 `{{name}} {{status}} {{detail}} {{address}} {{duration}} {{time}} {{type}}` 占位符，THE 系统 SHALL 替换为实际值；未知占位符 SHALL 替换为空串；平台格式下 SHALL 对文本做 JSON 转义。

### 持久化

- WHEN 用户修改 Webhook 配置，THE 系统 SHALL 立即持久化并在重启后恢复；旧数据缺字段时 SHALL 按未启用、空地址、通用格式、空模板恢复。

## 非目标

- 告警重试队列与失败上报。
- 每个拨测目标单独的 Webhook 配置。
- Webhook 签名（钉钉加签、飞书签名校验）。
