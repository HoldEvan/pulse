package com.evan.pulse.domain.net

import com.evan.pulse.domain.model.ProxyConfig
import com.evan.pulse.domain.model.ProxyType
import java.io.BufferedInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * 代理隧道握手：把一条已连到代理的 Socket 变成通往目标的隧道。
 *
 * 从 [ProxySocketFactory] 里抽出，供明文（tcp://）与 TLS（ssl://）两条路径共用，
 * 避免 HTTP CONNECT / SOCKS5 的实现出现两份。
 */
internal object ProxyTunnel {

    /** 新建一条到代理的连接并完成握手，返回可直接与目标通信的 Socket */
    fun open(proxy: ProxyConfig, host: String, port: Int, timeoutMs: Int): Socket {
        val socket = Socket()
        socket.connect(InetSocketAddress(proxy.host.trim(), proxy.port), timeoutMs)
        try {
            handshake(socket, proxy, host, port, timeoutMs)
            return socket
        } catch (t: Throwable) {
            runCatching { socket.close() }
            throw t
        }
    }

    /** 在已连到代理的 [socket] 上完成握手；失败时由调用方负责关闭 */
    fun handshake(socket: Socket, proxy: ProxyConfig, host: String, port: Int, timeoutMs: Int) {
        if (timeoutMs > 0) socket.soTimeout = timeoutMs
        when (proxy.type) {
            ProxyType.SOCKS5 -> socks5(socket, proxy, host, port)
            else -> httpConnect(socket, proxy, host, port)
        }
    }

    /** HTTP CONNECT：请求代理建立到目标的隧道，可选 Proxy-Authorization 认证 */
    private fun httpConnect(socket: Socket, proxy: ProxyConfig, host: String, port: Int) {
        val out = socket.getOutputStream()
        val req = buildString {
            append("CONNECT $host:$port HTTP/1.1\r\n")
            append("Host: $host:$port\r\n")
            append("Proxy-Connection: Keep-Alive\r\n")
            proxy.username?.takeIf { it.isNotBlank() }?.let { user ->
                val pass = proxy.password.orEmpty()
                val token = java.util.Base64.getEncoder()
                    .encodeToString("$user:$pass".toByteArray(StandardCharsets.UTF_8))
                append("Proxy-Authorization: Basic $token\r\n")
            }
            append("\r\n")
        }
        out.write(req.toByteArray(StandardCharsets.UTF_8))
        out.flush()
        val input = BufferedInputStream(socket.getInputStream())
        val statusLine = readLine(input)
        val parts = statusLine.split(" ")
        if (parts.size < 2 || parts[1].toIntOrNull() != 200) {
            throw IOException("HTTP 代理 CONNECT 失败: ${statusLine.trim()}")
        }
        // 读完代理返回的剩余响应头，直到空行
        while (readLine(input).isNotEmpty()) {
            // 逐行丢弃
        }
    }

    /** SOCKS5 握手：协商认证并建立到目标的连接 */
    private fun socks5(socket: Socket, proxy: ProxyConfig, host: String, port: Int) {
        val out: OutputStream = socket.getOutputStream()
        val input = BufferedInputStream(socket.getInputStream())
        val authEnabled = !proxy.username.isNullOrBlank()
        // 协商：无认证(0x00) 或 用户名密码(0x02)
        out.write(byteArrayOf(0x05, if (authEnabled) 2 else 1, 0x00, 0x02))
        out.flush()
        val methodReply = readN(input, 2)
        if (methodReply[0] != 0x05.toByte()) throw IOException("SOCKS5 协议版本错误")
        when (methodReply[1]) {
            0x00.toByte() -> Unit
            0x02.toByte() -> {
                val user = proxy.username.orEmpty().toByteArray(StandardCharsets.UTF_8)
                val pass = proxy.password.orEmpty().toByteArray(StandardCharsets.UTF_8)
                out.write(byteArrayOf(0x01, user.size.toByte()))
                out.write(user)
                out.write(byteArrayOf(pass.size.toByte()))
                out.write(pass)
                out.flush()
                val auth = readN(input, 2)
                if (auth[1] != 0x00.toByte()) throw IOException("SOCKS5 代理认证失败")
            }
            else -> throw IOException("SOCKS5 代理不支持所选认证方式")
        }
        // CONNECT 请求：0x05 0x01 0x00 + 域名地址
        val hostBytes = host.toByteArray(StandardCharsets.UTF_8)
        out.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, hostBytes.size.toByte()))
        out.write(hostBytes)
        out.write(byteArrayOf(((port shr 8) and 0xFF).toByte(), (port and 0xFF).toByte()))
        out.flush()
        val connectReply = readN(input, 4)
        if (connectReply[0] != 0x05.toByte() || connectReply[1] != 0x00.toByte()) {
            throw IOException("SOCKS5 代理 CONNECT 失败")
        }
        // 跳过 BND.ADDR + BND.PORT
        when (connectReply[3]) {
            0x01.toByte() -> readN(input, 4)
            0x03.toByte() -> readN(input, readN(input, 1)[0].toInt() and 0xFF)
            0x04.toByte() -> readN(input, 16)
        }
        readN(input, 2)
    }

    private fun readLine(input: BufferedInputStream): String {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) throw IOException("代理响应读取失败")
            if (b == '\n'.code) {
                if (sb.isNotEmpty() && sb.last() == '\r') sb.deleteCharAt(sb.length - 1)
                return sb.toString()
            }
            sb.append(b.toChar())
        }
    }

    private fun readN(input: BufferedInputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var offset = 0
        while (offset < n) {
            val read = input.read(buf, offset, n - offset)
            if (read < 0) throw IOException("代理响应不完整")
            offset += read
        }
        return buf
    }
}
