package com.evan.pulse.domain.mqtt

/** 一条订阅意图：主题过滤器与其独立 QoS */
data class TopicQos(val topic: String, val qos: Int)

/**
 * 批量订阅解析结果。
 *
 * [topics] 已去重并保持输入顺序；[invalid] 收集无法解析的出参原文，供界面提示。
 */
data class ParsedTopics(
    val topics: List<TopicQos> = emptyList(),
    val invalid: List<String> = emptyList()
) {
    val hasInvalid: Boolean get() = invalid.isNotEmpty()
}

/**
 * MQTT topic filter 校验：非空；+ 必须独占一层；# 只能独占最后一层。
 * 空层级（如 a//b）在 MQTT 主题里合法，允许通过。
 */
object MqttTopicFilters {

    fun isValid(topic: String): Boolean {
        val trimmed = topic.trim()
        if (trimmed.isEmpty()) return false
        val levels = trimmed.split('/')
        levels.forEachIndexed { index, level ->
            when {
                level.contains('#') -> if (level != "#" || index != levels.lastIndex) return false
                level.contains('+') -> if (level != "+") return false
            }
        }
        return true
    }
}

/**
 * 多主题订阅输入解析。
 *
 * 支持换行、逗号、分号分隔多个 topic；每个 topic 可用 `@0` / `@1` / `@2` 后缀指定
 * 独立 QoS，未指定时沿用界面当前选择的 QoS。非法 QoS 与非法 filter 都归入 invalid，
 * 合法的照常订阅，避免一条写错导致整批失败。
 */
object MqttSubscriptionInput {

    private val SEPARATORS = charArrayOf('\n', '\r', ',', ';')

    fun parse(raw: String, defaultQos: Int): ParsedTopics {
        val fallback = defaultQos.coerceIn(0, 2)
        val valid = LinkedHashMap<String, TopicQos>()
        val invalid = mutableListOf<String>()
        raw.split(*SEPARATORS)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { token ->
                val at = token.lastIndexOf('@')
                val topicPart: String
                val qos: Int
                if (at > 0) {
                    val parsed = token.substring(at + 1).trim().toIntOrNull()
                    if (parsed == null || parsed !in 0..2) {
                        invalid += token
                        return@forEach
                    }
                    topicPart = token.substring(0, at).trim()
                    qos = parsed
                } else {
                    topicPart = token
                    qos = fallback
                }
                if (!MqttTopicFilters.isValid(topicPart)) {
                    invalid += token
                } else {
                    valid[topicPart] = TopicQos(topicPart, qos)
                }
            }
        return ParsedTopics(valid.values.toList(), invalid)
    }
}
