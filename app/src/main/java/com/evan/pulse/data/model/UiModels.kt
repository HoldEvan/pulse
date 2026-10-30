package com.evan.pulse.data.model

import kotlinx.serialization.Serializable

data class ProjectInfo(
    val id: Long,
    val name: String
)

data class EnvironmentInfo(
    val id: Long,
    val projectId: Long,
    val name: String,
    val isActive: Boolean,
    val variables: List<Pair<String, String>>
)

/**
 * 历史记录里的断言快照：发送时把断言类型、表达式与求值结论一起存下来，
 * 详情页无需重新求值就能复盘当时为什么判成功或失败。
 */
@Serializable
data class HistoryAssertion(
    val type: String,
    val expression: String = "",
    val expected: String = "",
    val passed: Boolean = false,
    val detail: String = ""
)

data class HistoryEntry(
    val id: Long,
    val projectId: Long,
    val method: String,
    val url: String,
    val statusCode: Int,
    val statusText: String = "",
    val durationMs: Long,
    val success: Boolean,
    val createdAt: Long,
    val responseSizeBytes: Long = 0L,
    /** "key: value" 形式的已发送请求头，含鉴权头 */
    val requestHeaders: List<String> = emptyList(),
    val requestBody: String = "",
    val requestBodyTruncated: Boolean = false,
    /** "key: value" 形式的响应头 */
    val responseHeaders: List<String> = emptyList(),
    val responseBody: String = "",
    val responseBodyTruncated: Boolean = false,
    /** 本次请求配置的断言与求值结果，早期记录为空 */
    val assertions: List<HistoryAssertion> = emptyList(),
    /** 收藏项在历史列表中置顶 */
    val favorite: Boolean = false
)
