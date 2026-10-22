package com.evan.pulse.data.monitor

import androidx.annotation.StringRes
import com.evan.pulse.R
import com.evan.pulse.data.model.MONITOR_IP_PROBE_PORTS
import com.evan.pulse.data.model.MonitorRun
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.model.MonitorType
import com.evan.pulse.di.ServiceLocator
import com.evan.pulse.domain.PlaceholderResolver
import com.evan.pulse.domain.model.HttpCallConfig
import com.evan.pulse.domain.model.HttpCallOutcome
import com.evan.pulse.domain.model.HttpMethod
import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.net.HttpEngine
import com.evan.pulse.ui.l10n.L10n
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 创建一次拨测连接用的 Socket；注入以便单元测试替换 */
typealias SocketFactory = (proxy: Proxy?) -> Socket

/** 目标配置校验结果 */
sealed interface TargetValidation {
    data object Valid : TargetValidation

    data class Invalid(@StringRes val message: Int) : TargetValidation
}

/**
 * 拨测执行器。
 *
 * 三种检查共用一套超时语义：
 * - IP 连通：先解析主机，再依次尝试常用端口，任一可连即视为互通；
 * - 端口：对指定端口做一次 TCP 连接，等价于 telnet；
 * - 接口：发一个不带参数的 GET 请求，用期望状态码区间判定。
 *
 * Android 无 root 时拿不到 raw ICMP socket，所以 IP 连通用 TCP 探测代替 ping。
 */
class MonitorChecker(
    private val httpEngine: HttpEngine = ServiceLocator.httpEngine,
    private val resolver: PlaceholderResolver = ServiceLocator.placeholderResolver,
    private val socketFactory: SocketFactory = { proxy ->
        if (proxy == null) Socket() else Socket(proxy)
    },
) {

    /** 执行一次拨测，永不抛异常 */
    suspend fun check(
        target: MonitorTarget,
        vars: Map<String, String>,
        proxy: ProxyConfig,
    ): MonitorRun {
        val result = validation(target)
        if (result is TargetValidation.Invalid) {
            return MonitorRun(false, L10n.t(result.message), 0L, clock())
        }
        val run = runCheck(target, vars, proxy)
        return if (run.ok && target.latencyThresholdMs > 0L && run.durationMs > target.latencyThresholdMs) {
            run.copy(
                slow = true,
                detail = L10n.t(
                    R.string.monitor_slow_detail,
                    run.detail,
                    run.durationMs,
                    target.latencyThresholdMs
                )
            )
        } else {
            run
        }
    }

    private suspend fun runCheck(
        target: MonitorTarget,
        vars: Map<String, String>,
        proxy: ProxyConfig,
    ): MonitorRun {
        val start = clock()
        return when (target.type) {
            MonitorType.IP -> probeIp(target, proxy, start)
            MonitorType.PORT -> probePort(target, proxy, start)
            MonitorType.HTTP -> probeHttp(target, vars, proxy, start)
        }
    }

    private suspend fun probeIp(
        target: MonitorTarget,
        proxy: ProxyConfig,
        start: Long,
    ): MonitorRun {
        val host = target.host.trim()
        val address = resolveHost(host) ?: return failed(
            L10n.t(R.string.monitor_unresolved, host),
            start
        )
        MONITOR_IP_PROBE_PORTS.forEach { port ->
            if (connect(address, port, target, proxy).open) {
                return MonitorRun(true, L10n.t(R.string.monitor_ip_ok, port), elapsed(start), start)
            }
        }
        return failed(
            L10n.t(R.string.monitor_ip_unreachable, MONITOR_IP_PROBE_PORTS.joinToString("/")),
            start
        )
    }

    private suspend fun probePort(
        target: MonitorTarget,
        proxy: ProxyConfig,
        start: Long,
    ): MonitorRun {
        val host = target.host.trim()
        val address = resolveHost(host) ?: return failed(
            L10n.t(R.string.monitor_unresolved, host),
            start
        )
        val result = connect(address, target.port, target, proxy)
        return MonitorRun(
            ok = result.open,
            detail = L10n.t(
                if (result.open) R.string.monitor_port_ok else R.string.monitor_port_closed,
                host,
                target.port
            ),
            durationMs = elapsed(start),
            checkedAt = start
        )
    }

    private suspend fun probeHttp(
        target: MonitorTarget,
        vars: Map<String, String>,
        proxy: ProxyConfig,
        start: Long,
    ): MonitorRun {
        val probeUrl = when (val resolved = resolveUrl(target.url, vars)) {
            is ResolvedUrl.Done -> resolved.url
            is ResolvedUrl.Missing -> return failed(
                L10n.t(R.string.monitor_missing_vars, resolved.keys.joinToString(", ")),
                start
            )
        }
        val outcome = httpEngine.execute(
            HttpCallConfig(
                method = HttpMethod.GET,
                url = probeUrl,
                timeoutMs = clampTimeout(target.timeoutMs),
                proxy = proxy
            )
        )
        return when (outcome) {
            is HttpCallOutcome.Success -> MonitorRun(
                ok = statusPasses(outcome.statusCode, target.statusMin, target.statusMax),
                detail = L10n.t(R.string.monitor_http_result, outcome.statusCode),
                durationMs = outcome.durationMs,
                checkedAt = start
            )
            is HttpCallOutcome.Failure -> failed(
                L10n.t(R.string.monitor_http_error, outcome.message),
                start
            )
        }
    }

    private suspend fun resolveHost(host: String): InetAddress? {
        if (host.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            try {
                InetAddress.getByName(host)
            } catch (_: UnknownHostException) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun connect(
        address: InetAddress,
        port: Int,
        target: MonitorTarget,
        proxy: ProxyConfig,
    ): ConnectResult {
        if (!isValidPort(port)) return ConnectResult(false, 0L, L10n.t(R.string.monitor_port_invalid))
        val socket = try {
            socketFactory(toProxy(proxy))
        } catch (_: Exception) {
            return ConnectResult(false, 0L, L10n.t(R.string.monitor_connect_failed))
        }
        val start = clock()
        return try {
            socket.connect(InetSocketAddress(address, port), clampTimeout(target.timeoutMs).toInt().coerceAtLeast(1))
            ConnectResult(true, elapsed(start), "")
        } catch (_: SocketTimeoutException) {
            ConnectResult(false, elapsed(start), L10n.t(R.string.monitor_timeout))
        } catch (_: Exception) {
            ConnectResult(false, elapsed(start), L10n.t(R.string.monitor_connect_failed))
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun failed(detail: String, start: Long) =
        MonitorRun(false, detail, elapsed(start), start)

    private fun resolveUrl(url: String, vars: Map<String, String>): ResolvedUrl =
        when (val result = resolver.resolve(url, vars)) {
            is PlaceholderResolver.ResolveResult.Ok -> ResolvedUrl.Done(result.resolved)
            is PlaceholderResolver.ResolveResult.Missing -> ResolvedUrl.Missing(result.keys)
        }

    private fun toProxy(config: ProxyConfig): Proxy? {
        if (!config.enabled) return null
        val type = if (config.type == com.evan.pulse.domain.model.ProxyType.SOCKS5) {
            Proxy.Type.SOCKS
        } else {
            Proxy.Type.HTTP
        }
        return Proxy(type, InetSocketAddress(config.host.trim(), config.port))
    }

    private fun clampTimeout(timeoutMs: Long) = timeoutMs.coerceIn(1L, MAX_TIMEOUT_MS)

    private fun elapsed(start: Long) = clock() - start

    private fun clock() = System.currentTimeMillis()

    companion object {
        const val MAX_TIMEOUT_MS = 120_000L

        /** 端口合法范围，与 ProxyConfig.enabled 的判定口径一致 */
        fun isValidPort(port: Int): Boolean = port in 1..65535

        /** 状态码是否落在期望区间 */
        fun statusPasses(code: Int, min: Int, max: Int): Boolean = min <= code && code <= max

        /** 状态码区间是否合法：闭区间且落在 100..599 */
        fun statusRangeValid(min: Int, max: Int): Boolean =
            min in 100..599 && max in 100..599 && min <= max

        /** 校验目标配置是否完整可测 */
        fun validation(target: MonitorTarget): TargetValidation = when {
            target.name.isBlank() -> TargetValidation.Invalid(R.string.monitor_name_required)
            else -> when (target.type) {
                MonitorType.IP -> if (target.host.isBlank()) {
                    TargetValidation.Invalid(R.string.monitor_host_required)
                } else {
                    TargetValidation.Valid
                }
                MonitorType.PORT -> when {
                    target.host.isBlank() -> TargetValidation.Invalid(R.string.monitor_host_required)
                    !isValidPort(target.port) -> TargetValidation.Invalid(R.string.monitor_port_invalid)
                    else -> TargetValidation.Valid
                }
                MonitorType.HTTP -> {
                    val url = target.url.trim().lowercase()
                    when {
                        target.url.isBlank() -> TargetValidation.Invalid(R.string.monitor_url_required)
                        !url.startsWith("http://") && !url.startsWith("https://") ->
                            TargetValidation.Invalid(R.string.monitor_url_scheme)
                        !statusRangeValid(target.statusMin, target.statusMax) ->
                            TargetValidation.Invalid(R.string.monitor_status_range)
                        else -> TargetValidation.Valid
                    }
                }
            }
        }
    }
}

/** 一次 TCP 连接的结果 */
internal data class ConnectResult(val open: Boolean, val durationMs: Long, val reason: String)

/** 占位符替换结果 */
internal sealed interface ResolvedUrl {
    data class Done(val url: String) : ResolvedUrl
    data class Missing(val keys: List<String>) : ResolvedUrl
}
