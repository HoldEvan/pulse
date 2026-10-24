package com.evan.pulse.domain.spec

import com.evan.pulse.data.model.CollectionInfo
import com.evan.pulse.data.model.HttpSnapshot
import com.evan.pulse.data.model.KeyValueEntry
import com.evan.pulse.data.model.RequestType
import com.evan.pulse.data.model.SavedRequest
import com.evan.pulse.data.model.WsSnapshot
import com.evan.pulse.data.model.snapshotJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionExporterTest {

    private val root = CollectionInfo(1, 1, null, "订单")
    private val child = CollectionInfo(2, 1, 1, "支付")
    private val folders = listOf(root, child)

    private fun http(id: Long, collectionId: Long, name: String, snapshot: HttpSnapshot) =
        SavedRequest(
            id = id,
            collectionId = collectionId,
            type = RequestType.HTTP,
            name = name,
            data = snapshotJson.encodeToString(HttpSnapshot.serializer(), snapshot),
            createdAt = 0L
        )

    private val requests = listOf(
        http(
            1, 1, "列表",
            HttpSnapshot(
                method = "GET",
                url = "https://api.example.com/users",
                params = listOf(KeyValueEntry("page", "2"))
            )
        ),
        http(
            2, 2, "创建",
            HttpSnapshot(
                method = "POST",
                url = "https://api.example.com/users",
                headers = listOf(KeyValueEntry("Content-Type", "application/json")),
                bodyType = "JSON",
                bodyText = """{"name":"ann"}""",
                authType = "BEARER",
                bearerToken = "t"
            )
        ),
        SavedRequest(
            id = 3,
            collectionId = 1,
            type = RequestType.WS,
            name = "聊天",
            data = snapshotJson.encodeToString(WsSnapshot.serializer(), WsSnapshot(url = "wss://h/ws")),
            createdAt = 0L
        )
    )

    @Test
    fun `postman export keeps folders, variables and requests`() {
        val result = CollectionExporter.export(
            root, folders, requests,
            mapOf("host" to "api.example.com"),
            CollectionExportFormat.POSTMAN
        )
        val json = Json.parseToJsonElement(result.text).jsonObject

        assertEquals("订单", json["info"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("host", json["variable"]!!.jsonArray[0].jsonObject["key"]!!.jsonPrimitive.content)

        val items = json["item"]!!.jsonArray
        assertEquals("支付", items[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("列表", items[1].jsonObject["name"]!!.jsonPrimitive.content)

        val get = items[1].jsonObject["request"]!!.jsonObject
        assertEquals("GET", get["method"]!!.jsonPrimitive.content)
        assertTrue(get["url"]!!.jsonObject["raw"]!!.jsonPrimitive.content.contains("page=2"))

        val post = items[0].jsonObject["item"]!!.jsonArray[0].jsonObject
        val request = post["request"]!!.jsonObject
        assertEquals("POST", request["method"]!!.jsonPrimitive.content)
        assertEquals("raw", request["body"]!!.jsonObject["mode"]!!.jsonPrimitive.content)
        assertEquals("bearer", request["auth"]!!.jsonObject["type"]!!.jsonPrimitive.content)

        assertEquals(2, result.exported)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `openapi export groups paths and methods`() {
        val result = CollectionExporter.export(
            root, folders, requests,
            emptyMap(),
            CollectionExportFormat.OPENAPI
        )
        val json = Json.parseToJsonElement(result.text).jsonObject

        assertEquals("3.0.3", json["openapi"]!!.jsonPrimitive.content)
        val path = json["paths"]!!.jsonObject["/users"]!!.jsonObject
        assertTrue(path.containsKey("get"))
        assertTrue(path.containsKey("post"))

        val get = path["get"]!!.jsonObject
        assertEquals("列表", get["summary"]!!.jsonPrimitive.content)
        val query = get["parameters"]!!.jsonArray[0].jsonObject
        assertEquals("page", query["name"]!!.jsonPrimitive.content)
        assertEquals("query", query["in"]!!.jsonPrimitive.content)

        val post = path["post"]!!.jsonObject
        assertTrue(post.containsKey("requestBody"))
        assertEquals(2, result.exported)
    }

    @Test
    fun `file name strips unsafe characters`() {
        val odd = CollectionInfo(9, 1, null, " my/group: api ")
        assertEquals("my_group_api.postman.json", CollectionExporter.fileName(odd, CollectionExportFormat.POSTMAN))
        assertEquals("my_group_api.openapi.json", CollectionExporter.fileName(odd, CollectionExportFormat.OPENAPI))
    }
}
