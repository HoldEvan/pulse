package com.evan.pulse.data.persistence

import com.evan.pulse.data.model.CollectionInfo
import com.evan.pulse.data.model.EnvironmentInfo
import com.evan.pulse.data.model.HistoryEntry
import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.data.model.MonitorAssertion
import com.evan.pulse.data.model.MonitorHistoryEntry
import com.evan.pulse.data.model.MonitorRecord
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.model.MonitorType
import com.evan.pulse.data.model.MqttSubscriptionEntry
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.SavedRequest
import com.evan.pulse.data.model.WsMessageTemplate
import com.evan.pulse.data.model.WsSessionEntry
import com.evan.pulse.di.StoredCookie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 测试用加密：可逆且带明显前缀，方便断言表里没有明文 */
private object FakeCipher : FieldCipher {
    private const val PREFIX = "enc:"

    override fun seal(plain: String): String =
        if (plain.isEmpty()) plain else PREFIX + plain.reversed()

    override fun open(value: String): String =
        if (!value.startsWith(PREFIX)) value else value.removePrefix(PREFIX).reversed()
}

class StateMapperTest {

    private val mapper = StateMapper(FakeCipher)

    @Test
    fun `环境变量写入实体后不含明文且可还原`() {
        val env = EnvironmentInfo(
            id = 3,
            projectId = 1,
            name = "staging",
            isActive = true,
            variables = listOf("token" to "s3cr3t", "baseUrl" to "https://api.example.com")
        )
        val entity = mapper.environmentToEntity(env)
        val vars = mapper.environmentVarsToEntity(env)

        assertEquals(3L, entity.id)
        assertFalse(vars.any { it.value.contains("s3cr3t") })
        assertEquals(env, mapper.environmentFromEntity(entity, vars))
    }

    @Test
    fun `文件夹变量按键值往返并保留顺序`() {
        val entries = listOf(
            KeyValueEntry("a", "1", true),
            KeyValueEntry("b", "2", false),
            KeyValueEntry("c", "3", true)
        )
        val entities = mapper.collectionVarsToEntity(7, entries)
        val restored = mapper.collectionVarsFromEntity(entities)

        assertEquals(entries, restored[7])
        assertFalse(entities.any { it.value.contains("1") && it.value.length == 1 })
    }

    @Test
    fun `请求快照整体加密且类型还原`() {
        val request = SavedRequest(
            id = 9,
            collectionId = 2,
            type = RequestType.WS,
            name = "chat",
            data = """{"url":"wss://example.com","auth":"Bearer abc"}""",
            createdAt = 1_700_000_000_000L
        )
        val entity = mapper.requestToEntity(request, position = 4)

        assertEquals(4, entity.position)
        assertFalse(entity.data.contains("Bearer abc"))
        assertEquals(request, mapper.requestFromEntity(entity))
    }

    @Test
    fun `历史记录的头与正文加密且完整还原`() {
        val entry = HistoryEntry(
            id = 11,
            projectId = 1,
            method = "POST",
            url = "https://api.example.com/login",
            statusCode = 200,
            statusText = "OK",
            durationMs = 210,
            success = true,
            createdAt = 1_700_000_000_001L,
            responseSizeBytes = 88,
            requestHeaders = listOf("Authorization: Bearer t0ken", "Content-Type: application/json"),
            requestBody = """{"password":"p@ss"}""",
            responseHeaders = listOf("Set-Cookie: sid=abc"),
            responseBody = """{"token":"jwt.value"}""",
            favorite = true
        )
        val entity = mapper.historyToEntity(entry)

        assertFalse(entity.requestHeaders.contains("t0ken"))
        assertFalse(entity.requestBody.contains("p@ss"))
        assertFalse(entity.responseBody.contains("jwt.value"))
        assertEquals(entry, mapper.historyFromEntity(entity))
    }

    @Test
    fun `Cookie 值加密且按项目还原`() {
        val cookie = StoredCookie(
            name = "sid",
            value = "secret-cookie",
            domain = "example.com",
            path = "/api",
            expiresAtMs = 1_800_000_000_000L
        )
        val entity = mapper.cookieToEntity(projectId = 5, item = cookie)

        assertEquals(5L, entity.projectId)
        assertFalse(entity.value.contains("secret-cookie"))
        assertEquals(cookie, mapper.cookieFromEntity(entity))
    }

    @Test
    fun `拨测目标的请求头与断言经 JSON 列往返`() {
        val target = MonitorTarget(
            id = 2,
            projectId = 1,
            name = "订单服务",
            type = MonitorType.HTTP,
            host = "api.example.com",
            port = 443,
            url = "https://api.example.com/health",
            timeoutMs = 3000,
            intervalSec = 60,
            statusMin = 200,
            statusMax = 299,
            latencyThresholdMs = 800,
            enabled = true,
            group = "核心",
            createdAt = 1_700_000_000_000L,
            method = "POST",
            headers = listOf(
                KeyValueEntry("Authorization", "Bearer abc", true),
                KeyValueEntry("Content-Type", "application/json", true)
            ),
            bodyType = "JSON",
            bodyText = """{"ping":true}""",
            authType = "BEARER",
            bearerToken = "abc",
            basicUsername = "user",
            basicPassword = "pass",
            apiKeyName = "X-Key",
            apiKeyValue = "k-1",
            assertions = listOf(
                MonitorAssertion("CONTAINS", "ok", ""),
                MonitorAssertion("JSON_PATH", "$.status", "up")
            )
        )
        val entity = mapper.monitorTargetToEntity(target)

        assertFalse(entity.headers.contains("Bearer abc"))
        assertFalse(entity.bearerToken.contains("abc"))
        assertFalse(entity.basicPassword.contains("pass"))
        assertFalse(entity.apiKeyValue.contains("k-1"))
        assertEquals(target, mapper.monitorTargetFromEntity(entity))
    }

    @Test
    fun `拨测记录与趋势样本可还原`() {
        val record = MonitorRecord(
            id = 1,
            projectId = 1,
            targetId = 2,
            targetName = "订单服务",
            type = MonitorType.PORT,
            message = "连接超时 Bearer abc",
            durationMs = 5000,
            checkedAt = 1_700_000_000_000L,
            ok = false
        )
        assertFalse(mapper.monitorRecordToEntity(record).message.contains("Bearer abc"))
        assertEquals(record, mapper.monitorRecordFromEntity(mapper.monitorRecordToEntity(record)))

        val sample = MonitorHistoryEntry(1, 2, false, true, 5000, 1_700_000_000_000L)
        assertEquals(
            sample,
            mapper.monitorHistoryFromEntity(mapper.monitorHistoryToEntity(sample))
        )
    }

    @Test
    fun `协议会话与模板还原并加密内容`() {
        val session = WsSessionEntry(1, "wss://example.com", "hello 世界", false, 1_700_000_000_000L)
        val sessionEntity = mapper.wsSessionToEntity(session)
        assertFalse(sessionEntity.text.contains("hello"))
        assertEquals(session, mapper.wsSessionFromEntity(sessionEntity))

        val template = WsMessageTemplate(2, "ping", """{"t":"ping"}""", false, "HEX", 1L)
        val templateEntity = mapper.wsTemplateToEntity(template, position = 0)
        assertFalse(templateEntity.content.contains("ping"))
        assertEquals(template, mapper.wsTemplateFromEntity(templateEntity))
    }

    @Test
    fun `MQTT 订阅保留位置顺序`() {
        val entity = mapper.mqttSubscriptionToEntity(MqttSubscriptionEntry("a/b", 1), position = 3)
        assertEquals(3, entity.position)
        assertEquals(MqttSubscriptionEntry("a/b", 1), mapper.mqttSubscriptionFromEntity(entity))
    }

    @Test
    fun `集合映射保留父子关系`() {
        val info = CollectionInfo(4, 1, 2, "子目录")
        val entity = mapper.collectionToEntity(info, position = 1)
        assertEquals(1, entity.position)
        assertEquals(info, mapper.collectionFromEntity(entity))
    }

    @Test
    fun `加密字段为空时保持空串`() {
        val entity = mapper.environmentVarsToEntity(
            EnvironmentInfo(1, 1, "empty", false, listOf("k" to ""))
        )
        assertTrue(entity.single().value.isEmpty())
    }

    @Test
    fun `直通实现保证无密钥环境下数据仍可读写`() {
        val passthrough = StateMapper(PlainCipher)
        val env = EnvironmentInfo(1, 1, "dev", false, listOf("k" to "v"))
        val restored = passthrough.environmentFromEntity(
            passthrough.environmentToEntity(env),
            passthrough.environmentVarsToEntity(env)
        )
        assertEquals(env, restored)
    }
}
