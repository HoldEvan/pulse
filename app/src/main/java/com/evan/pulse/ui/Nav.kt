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

    /** 历史三级页：以某条记录为基准与另一条记录对比响应差异 */
    @Serializable data class ResponseDiff(val entryId: Long) : PulseRoute

    /** 拨测二级页：当前项目的全部未通记录 */
    @Serializable data object MonitorRecords : PulseRoute

    /** 拨测二级页：某目标的趋势与可用率 */
    @Serializable data class MonitorTrend(val targetId: Long) : PulseRoute

    /** HTTP 三级页：脚本代码编辑器，target 0 为前置脚本、1 为后置脚本 */
    @Serializable data class ScriptEditor(val target: Int) : PulseRoute

    /** HTTP 三级页：脚本内置 API 文档，与编辑器共用 target 语义 */
    @Serializable data class ScriptDocs(val target: Int) : PulseRoute

    @Serializable data class EnvVars(val envId: Long) : PulseRoute

    /** 历史列表二级页：从 HTTP 页顶部历史图标进入 */
    @Serializable data object History : PulseRoute

    /** HTTP 三级页：当前项目的 Cookie 管理（自动收集 + 手动添加） */
    @Serializable data object Cookies : PulseRoute

    /** 工具二级页：信息码转换（Base64/进制/URL/Unicode/ASCII） */
    @Serializable data object EncodingTool : PulseRoute

    /** 工具二级页：图片与 Base64 互转 */
    @Serializable data object ImageBase64Tool : PulseRoute

    /** 工具二级页：JSON 格式化/压缩 */
    @Serializable data object JsonTool : PulseRoute

    /** 工具二级页：JSON Schema 结构校验 */
    @Serializable data object JsonSchemaTool : PulseRoute

    /** 工具二级页：时间转换 */
    @Serializable data object TimestampTool : PulseRoute

    /** 工具二级页：JWT 解码 */
    @Serializable data object JwtTool : PulseRoute

    /** 工具二级页：Hash（MD5/SHA） */
    @Serializable data object HashTool : PulseRoute

    /** 工具二级页：UUID 生成/校验 */
    @Serializable data object UuidTool : PulseRoute

    /** 工具二级页：正则测试 */
    @Serializable data object RegexTool : PulseRoute

    /** 工具二级页：cron 解析 */
    @Serializable data object CronTool : PulseRoute

    /** 工具二级页：局域网扫描 */
    @Serializable data object LanScanTool : PulseRoute

    /** 集合二级页：顺序运行一个集合（或其下全部子集合）的 HTTP 请求，collectionId 为空表示当前项目全部 */
    @Serializable data class CollectionRunner(val collectionId: Long? = null) : PulseRoute
}

/**
 * push 前检查栈顶是否已是同一路由：miuix-nav 拒绝重复的 contentKey 入栈，
 * 快速连点导航按钮会被 IllegalArgumentException 中断，这里做应用层幂等。
 */
fun NavController.pushRoute(key: PulseRoute) {
    if (backStack.lastOrNull() == key) return
    push(key)
}
