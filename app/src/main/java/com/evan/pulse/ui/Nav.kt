package com.evan.pulse.ui

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.NavController

/**
 * 应用导航路由：单一扁平栈，栈底恒为 [Tabs]。
 *
 * 全部标注 @Serializable，返回栈经 rememberNavBackStack 持久化，
 * 配置变更与进程死亡后均可恢复。
 */
@Serializable
sealed interface PulseRoute : NavKey {
    /** 主标签页：首页 / 测试 / 资源 / 拨测 / 设置 */
    @Serializable data object Tabs : PulseRoute

    /** 设置二级页：外观与主题 */
    @Serializable data object Appearance : PulseRoute

    /** 设置三级页：关于应用，展示版本与开源信息 */
    @Serializable data object About : PulseRoute

    /** 关于三级页：用户协议全文 */
    @Serializable data object Agreement : PulseRoute

    /** 关于三级页：隐私政策全文 */
    @Serializable data object Privacy : PulseRoute

    /** 历史二级页：单条记录的详情 */
    @Serializable data class HistoryDetail(val entryId: Long) : PulseRoute

    /** 拨测二级页：当前项目的全部未通记录 */
    @Serializable data object MonitorRecords : PulseRoute

    /** HTTP 三级页：脚本代码编辑器，target 0 为前置脚本、1 为后置脚本 */
    @Serializable data class ScriptEditor(val target: Int) : PulseRoute

    /** HTTP 三级页：脚本内置 API 文档，与编辑器共用 target 语义 */
    @Serializable data class ScriptDocs(val target: Int) : PulseRoute

    @Serializable data class EnvVars(val envId: Long) : PulseRoute

    /** 历史列表二级页：从 HTTP 页顶部历史图标进入 */
    @Serializable data object History : PulseRoute

    /** 工具二级页：信息码转换（Base64/进制/URL/Unicode/ASCII） */
    @Serializable data object EncodingTool : PulseRoute

    /** 工具二级页：图片与 Base64 互转 */
    @Serializable data object ImageBase64Tool : PulseRoute

    /** 工具二级页：JSON 格式化/压缩 */
    @Serializable data object JsonTool : PulseRoute

    /** 工具二级页：时间戳转换 */
    @Serializable data object TimestampTool : PulseRoute
}

/**
 * push 前检查栈顶是否已是同一路由：miuix-nav 拒绝重复的 contentKey 入栈，
 * 快速连点导航按钮会被 IllegalArgumentException 中断，这里做应用层幂等。
 */
fun NavController.pushRoute(key: PulseRoute) {
    if (backStack.lastOrNull() == key) return
    push(key)
}
