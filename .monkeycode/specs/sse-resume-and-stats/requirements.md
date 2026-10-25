# 需求：SSE 断点续连与事件统计（#23）

## 背景

路线图 P1 #23。SSE 断线重连后服务端会从头推送或丢帧；页面也无法看出各类事件各收到多少。补 `Last-Event-ID` 透传、事件计数与按事件名统计。

## EARS 需求

### 断点

- WHEN 用户在 SSE 页面填写「断点 Last-Event-ID」并连接，THE 系统 SHALL 在请求头中携带该值。
- WHEN 本次连接已收到过带 id 的帧且触发自动重连，THE 系统 SHALL 用最后收到的 id 覆盖手动断点作为新的 `Last-Event-ID`。
- WHEN 收到的帧没有 id 字段，THE 系统 SHALL 保留此前的断点值不变。
- WHEN 用户已在请求头里手动配置了 `Last-Event-ID`（大小写不敏感），THE 系统 SHALL 以用户配置为准，不额外追加同名头。
- WHEN 用户点击「回填断点」，THE 系统 SHALL 把当前断点写入断点输入框，供下次连接使用。
- WHEN 断点值含首尾空格，THE 系统 SHALL 去除空格后发送。
- WHEN 断点输入为空且未收到过带 id 的帧，THE 系统 SHALL 不发送 `Last-Event-ID` 头。

### 统计

- WHEN 收到一个完整事件帧，THE 系统 SHALL 把总帧数加一，并按事件名计数加一。
- WHEN 帧的 event 字段为空，THE 系统 SHALL 按 SSE 默认类型 `message` 统计。
- WHEN 用户查看统计卡片，THE 系统 SHALL 按次数倒序展示各事件名与次数，次数相同时按事件名升序。
- WHEN 尚未收到任何事件，THE 系统 SHALL 显示空态文案。
- WHEN 用户点击「清空事件流」，THE 系统 SHALL 同时把统计与断点清零。
- WHEN 用户重新发起连接，THE 系统 SHALL 重置统计与断点，避免跨会话累加。

### 兼容

- WHEN 旧版本快照不含断点字段，THE 系统 SHALL 以空串启动，连接行为与之前一致。
- WHEN 事件名过滤生效，THE 系统 SHALL 只统计通过过滤的帧。

## 非目标

- 自动重试间隔的指数退避。
- 断点值的持久化历史（仅保留当前值随快照保存）。
- 统计结果导出。
