package com.evan.pulse.ui.mqtt

import com.evan.pulse.data.model.MqttSubscriptionEntry
import com.evan.pulse.domain.mqtt.MqttTopicFilters

/**
 * MQTT topic filter 校验：非空；+ 必须独占一层；# 只能独占最后一层。
 * 空层级（如 a//b）在 MQTT 主题里合法，允许通过。
 * 实现下沉到 [MqttTopicFilters]，批量订阅解析与单条校验共用同一份规则。
 */
fun isValidMqttFilter(topic: String): Boolean = MqttTopicFilters.isValid(topic)

/** Topic 树节点：segment 为单层名，path 为根到该节点的完整路径（折叠状态键），叶项挂原始 filter */
data class MqttTopicNode(
    val segment: String,
    val path: String,
    val children: List<MqttTopicNode>,
    val leaves: List<MqttSubscriptionEntry>
)

/** 把保存的订阅投影成分段树；相同 topic 只保留一条，保持输入顺序 */
fun buildMqttTopicTree(subscriptions: List<MqttSubscriptionEntry>): List<MqttTopicNode> {
    val roots = LinkedHashMap<String, NodeBuilder>()
    subscriptions.distinctBy { it.topic }.forEach { sub ->
        insert(roots, "", sub.topic.trim().split('/'), sub)
    }
    return roots.values.map { it.build() }
}

private fun insert(
    level: LinkedHashMap<String, NodeBuilder>,
    parentPath: String,
    levels: List<String>,
    sub: MqttSubscriptionEntry
) {
    val segment = levels.first()
    val path = if (parentPath.isEmpty()) segment else "$parentPath/$segment"
    val node = level.getOrPut(segment) { NodeBuilder(segment, path) }
    if (levels.size == 1) {
        node.leaves.add(sub)
    } else {
        insert(node.children, path, levels.drop(1), sub)
    }
}

private class NodeBuilder(val segment: String, val path: String) {
    val children = LinkedHashMap<String, NodeBuilder>()
    val leaves = mutableListOf<MqttSubscriptionEntry>()

    fun build(): MqttTopicNode = MqttTopicNode(segment, path, children.values.map { it.build() }, leaves.toList())
}
