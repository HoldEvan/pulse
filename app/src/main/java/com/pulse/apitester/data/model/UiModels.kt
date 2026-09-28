package com.pulse.apitester.data.model

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
    val durationMs: Long,
    val success: Boolean,
    val createdAt: Long
)
