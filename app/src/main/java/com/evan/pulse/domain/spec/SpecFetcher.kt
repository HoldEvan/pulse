package com.evan.pulse.domain.spec

import com.evan.pulse.domain.model.HttpCallConfig
import com.evan.pulse.domain.model.HttpCallOutcome
import com.evan.pulse.domain.model.HttpMethod
import com.evan.pulse.domain.model.KeyValue
import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.net.HttpEngine

/**
 * 规范文件下载器：支持 http/https 文件 URL，也兼容直接粘贴的原始规范文本。
 *
 * 复用主链路引擎，代理、超时、错误分类都不另起一套实现。
 */
object SpecFetcher {

    private const val FETCH_TIMEOUT_MS = 15_000L
    private const val MAX_BYTES = 20L * 1024 * 1024

    suspend fun fetch(source: String, engine: HttpEngine, proxy: ProxyConfig): String {
        val trimmed = source.trim()
        if (!isHttpUrl(trimmed)) return trimmed

        val outcome = engine.execute(
            HttpCallConfig(
                method = HttpMethod.GET,
                url = trimmed,
                headers = listOf(KeyValue("Accept", "application/yaml, application/json, text/plain, */*")),
                timeoutMs = FETCH_TIMEOUT_MS,
                proxy = proxy
            )
        )

        return when (outcome) {
            is HttpCallOutcome.Success -> {
                if (outcome.responseSizeBytes > MAX_BYTES) {
                    throw SpecImportException(SpecError.FETCH_FAILED, "规范文件超过 ${MAX_BYTES / (1024 * 1024)}MB 上限")
                }
                if (outcome.statusCode !in 200..299) {
                    throw SpecImportException(SpecError.FETCH_FAILED, "HTTP ${outcome.statusCode}")
                }
                outcome.body
            }

            is HttpCallOutcome.Failure ->
                throw SpecImportException(SpecError.FETCH_FAILED, "下载失败：${outcome.message}")
        }
    }

    private fun isHttpUrl(source: String): Boolean {
        val lowered = source.lowercase()
        return lowered.startsWith("http://") || lowered.startsWith("https://")
    }
}
