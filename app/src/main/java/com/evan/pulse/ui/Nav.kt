package com.evan.pulse.ui

/** 主标签页层级：首页 / 资源 / 拨测 / 历史 / 设置 */
const val LEVEL_TABS = 0

/** 设置二级页：外观与主题 */
const val LEVEL_APPEARANCE = 2

/** 历史二级页：单条记录的详情 */
const val LEVEL_HISTORY_DETAIL = 3

/** 拨测二级页：当前项目的全部未通记录 */
const val LEVEL_MONITOR_RECORDS = 4

/** HTTP 三级页：脚本代码编辑器 */
const val LEVEL_SCRIPT_EDITOR = 5

/** HTTP 三级页：脚本内置 API 文档 */
const val LEVEL_SCRIPT_DOCS = 6

/**
 * 页面栈状态。
 *
 * 栈底固定为标签页层级，向栈顶推入的层级按顺序打开，返回时逐个弹出。
 * [historyEntryId] 只在栈顶是 [LEVEL_HISTORY_DETAIL] 时有效，随 push 写入、随 goBack 清空；
 * [scriptTarget] 只在栈顶是 [LEVEL_SCRIPT_EDITOR] 或 [LEVEL_SCRIPT_DOCS] 时有效，0 表示前置脚本、1 表示后置脚本。
 */
data class NavState(
    val stack: List<Int> = listOf(LEVEL_TABS),
    val selectedTab: Int = 0,
    val historyEntryId: Long? = null,
    val scriptTarget: Int? = null,
) {

    val current: Int get() = stack.last()

    val previous: Int? get() = stack.getOrNull(stack.lastIndex - 1)

    val canGoBack: Boolean get() = stack.size > 1
}
