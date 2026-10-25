# 需求：MQTT retain 与多订阅（#24）

## 背景

路线图 P1 #24。当前发布不带 retain 标志，订阅输入只接受单条 topic 且所有订阅共用界面上选的 QoS，批量配置需要反复操作。

## EARS 需求

### 发布

- WHEN 用户打开「保留消息（Retain）」开关并发布，THE 系统 SHALL 以 retain=true 发布该消息。
- WHEN 开关关闭（默认），THE 系统 SHALL 保持原行为，以 retain=false 发布。
- WHEN 用户收到 Broker 保留的消息，THE 系统 SHALL 在事件流中以「[保留]」前缀标注该条记录。
- WHEN 用户保存或从集合加载 MQTT 请求，THE 系统 SHALL 一并保存与恢复 retain 开关。

### 多订阅

- WHEN 用户在 Topic 输入框写入以换行、逗号或分号分隔的多个 topic 并点击订阅，THE 系统 SHALL 逐条解析并一次性保存所有合法条目。
- WHEN 某条 topic 后带 `@0`/`@1`/`@2` 后缀，THE 系统 SHALL 以该值作为该条目的独立 QoS；未带后缀的沿用界面当前 QoS。
- WHEN 同一 topic 在输入中重复出现，THE 系统 SHALL 只保留最后一条的 QoS。
- WHEN 已保存的同名 topic 再次订阅，THE 系统 SHALL 用新的 QoS 覆盖原条目，不产生重复。
- WHEN 输入含非法 topic filter 或非法 QoS 后缀，THE 系统 SHALL 跳过该条并在输入框下方列出被跳过的原文，其余条目照常订阅。
- WHEN topic 中含 `@` 但后缀不是 0..2，THE 系统 SHALL 按非法条目跳过，避免把拼写错误静默当成新 topic。
- WHEN 已连接状态下批量订阅成功，THE 系统 SHALL 按各自的 QoS 立即向 Broker 订阅。
- WHEN 输入为空或只含分隔符，THE 系统 SHALL 不写入任何订阅，也不显示错误。
- WHEN 界面默认 QoS 超出 0..2，THE 系统 SHALL 收敛到 2 后使用。

### 独立 QoS 调整

- WHEN 用户点击已订阅条目行，THE 系统 SHALL 把该条目的 QoS 循环切换为 0→1→2→0 并持久化。
- WHEN 切换 QoS 时处于已连接状态，THE 系统 SHALL 按新 QoS 重新订阅该 topic。
- WHEN 用户删除某条订阅，THE 系统 SHALL 只移除该条，其余订阅保持不变。

### 兼容

- WHEN 旧快照不含 retain 字段，THE 系统 SHALL 以关闭状态启动。
- WHEN 旧数据中的订阅条目缺少 QoS，THE 系统 SHALL 保持既有校验与展示行为。

## 非目标

- 清除 Broker 上的保留消息（retain 空负载）。
- 订阅通配符层级树的重构。
- 发布二进制负载。
