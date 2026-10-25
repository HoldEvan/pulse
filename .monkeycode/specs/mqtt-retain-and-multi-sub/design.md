# 技术设计：MQTT retain 与多订阅（#24）

## 方案

### 1. 纯逻辑

新文件 `domain/mqtt/MqttSubscriptionInput.kt`：

- `TopicQos(topic, qos)`。
- `ParsedTopics(topics, invalid)`，`hasInvalid` 便捷属性。
- `MqttTopicFilters.isValid(topic)`：从 `ui/mqtt/MqttTopicTree.kt` 下沉，保持规则不变（`+` 独占一层、`#` 仅末层）。
- `MqttSubscriptionInput.parse(raw, defaultQos)`：
  - 分隔符为 `\n`、`\r`、`,`、`;`；
  - 末尾 `@n` 后缀解析独立 QoS，`n` 必须 0..2，否则整条计入 invalid；
  - 含 `@` 的条目一律按 QoS 后缀解析，后缀不是 0..2 时整条计入 invalid，避免拼写错误被静默当成新 topic；
  - 用 `LinkedHashMap` 去重，重复 topic 后者覆盖前者，保持首次出现顺序；
  - 默认 QoS 先 `coerceIn(0,2)`。

`ui/mqtt/MqttTopicTree.kt` 的 `isValidMqttFilter` 改为委托 `MqttTopicFilters.isValid`，保留原公开函数避免影响既有测试与调用点。

### 2. 引擎

- `MqttEngine.publish(topic, payload, qos, retain = false)`；`PahoMqttEngine` 透传给 `MqttAsyncClient.publish`。
- `MqttEvent.MessageReceived` 增加 `retained: Boolean = false`，由 `MqttMessage.isRetained` 填充。

### 3. 模型

`MqttSnapshot` 增加 `pubRetain: Boolean = false`，旧快照缺字段默认关闭。

### 4. ViewModel

`MqttViewModel`：

- 新增 `pubRetain`，随 `snapshot()`/`restore()` 往返。
- `invalidTopics: List<String>` 取代布尔 `subFilterInvalid`。
- `addSubscription()` 走 `MqttSubscriptionInput.parse`：先过滤已存在 topic 再追加新条目；已连接时按各自 QoS 批量 `engine.subscribe`。
- 新增 `cycleSubscriptionQos(topic)`：QoS 取模 +1 落库，已连接时重新订阅。
- 收到保留消息时用 `mqtt_message_retained` 文案。

### 5. UI

`MqttScreen`：

- 发布卡片新增「保留消息（Retain）」开关行。
- 订阅错误提示改为列出被跳过的条目（`mqtt_filter_invalid_list`）。
- 订阅条目文本可点击，点击循环切换 QoS。

### 6. 文案

三语更新/新增 4 条：mqtt_topic_label（说明多主题与 @QoS 语法）、mqtt_filter_invalid_list、mqtt_pub_retain_label、mqtt_message_retained；mqtt_filter_invalid 保留供单条校验场景。

## 测试

新增 `MqttSubscriptionInputTest` 10 项：单条默认 QoS、三种分隔符、后缀覆盖、重复取后者、非法 filter 跳过、非法 QoS 后缀、空白输入、默认 QoS 收敛、含 `@` 但后缀非法的条目、`MqttTopicFilters` 校验。
