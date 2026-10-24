package com.evan.pulse.data.monitor

import com.evan.pulse.data.model.MonitorTarget

/**
 * 按分组批量改开关的纯逻辑。
 *
 * [group] 为 null 表示全部目标；空串表示未分组。返回新列表，不做原地修改。
 */
fun applyEnabledByGroup(
    targets: List<MonitorTarget>,
    group: String?,
    enabled: Boolean
): List<MonitorTarget> = targets.map { target ->
    if (group == null || target.group == group) target.copy(enabled = enabled) else target
}

/** 分组内是否全部启用；空分组视为未全部启用 */
fun isGroupFullyEnabled(targets: List<MonitorTarget>, group: String): Boolean {
    val members = targets.filter { it.group == group }
    return members.isNotEmpty() && members.all { it.enabled }
}

/** 是否全部目标都启用；无目标时返回 false */
fun isAllEnabled(targets: List<MonitorTarget>): Boolean =
    targets.isNotEmpty() && targets.all { it.enabled }
