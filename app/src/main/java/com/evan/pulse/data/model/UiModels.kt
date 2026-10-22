package com.evan.pulse.data.model

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
    /** 收藏项在历史列表中置顶 */
    val favorite: Boolean = false
)
