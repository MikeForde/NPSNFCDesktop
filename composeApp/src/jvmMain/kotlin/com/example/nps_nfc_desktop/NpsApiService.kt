package com.example.nps_nfc_desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64
import java.util.zip.GZIPInputStream

data class IpsListItem(
    val packageUUID: String,
    val given: String? = null,
    val name: String? = null
) {
    fun displayLabel(): String {
        val family = name?.trim().orEmpty()
        val givenName = given?.trim().orEmpty()
        val person = listOf(givenName, family).filter { it.isNotBlank() }.joinToString(" ")
        return if (person.isNotBlank()) "$person — $packageUUID" else packageUUID
    }
}

data class NpsNfcApiResponse(
    val id: String,
    val cutoff: String,
    val protect: String,
    val encoding: String,
    val roBytesJson: Int,
    val rwBytesJson: Int,
    val roBytesGz: Int,
    val rwBytesGz: Int,
    val roGzB64: String,
    val rwGzB64: String
)

data class LoadedApiRecord(
    val meta: NpsNfcApiResponse,
    val roJson: String,
    val rwJson: String
)

class NpsApiService {

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun listRecords(baseUrl: String): List<IpsListItem> {
        val url = normalizeBaseUrl(baseUrl) + "/ips/list"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("accept", "application/json")
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) {
            "List request failed: HTTP ${response.statusCode()}"
        }

        val root = json.parseToJsonElement(response.body())
        require(root is JsonArray) { "Expected JSON array from /ips/list" }

        return root.map { item ->
            val obj = item as? JsonObject ?: error("Expected object in /ips/list array")
            IpsListItem(
                packageUUID = obj.string("packageUUID"),
                given = obj.stringOrNull("given"),
                name = obj.stringOrNull("name")
            )
        }
    }

    fun fetchRecord(baseUrl: String, id: String, protect: Int): LoadedApiRecord {
        val encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8)
        val url = normalizeBaseUrl(baseUrl) + "/npsnfc/$encodedId?protect=$protect"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("accept", "application/json")
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) {
            "Fetch request failed: HTTP ${response.statusCode()}"
        }

        val root = json.parseToJsonElement(response.body())
        val obj = root as? JsonObject ?: error("Expected JSON object from /npsnfc/{id}")

        val parsed = NpsNfcApiResponse(
            id = obj.string("id"),
            cutoff = obj.string("cutoff"),
            protect = obj.string("protect"),
            encoding = obj.string("encoding"),
            roBytesJson = obj.int("roBytesJson"),
            rwBytesJson = obj.int("rwBytesJson"),
            roBytesGz = obj.int("roBytesGz"),
            rwBytesGz = obj.int("rwBytesGz"),
            roGzB64 = obj.string("roGzB64"),
            rwGzB64 = obj.string("rwGzB64")
        )

        return LoadedApiRecord(
            meta = parsed,
            roJson = gunzipBase64ToUtf8(parsed.roGzB64),
            rwJson = gunzipBase64ToUtf8(parsed.rwGzB64)
        )
    }

    private fun normalizeBaseUrl(baseUrl: String): String =
        baseUrl.trim().removeSuffix("/")

    private fun gunzipBase64ToUtf8(value: String): String {
        val gz = Base64.getDecoder().decode(value)
        return GZIPInputStream(ByteArrayInputStream(gz))
            .bufferedReader(StandardCharsets.UTF_8)
            .use { it.readText() }
    }
}

private fun JsonObject.string(name: String): String =
    this[name]?.jsonPrimitive?.content ?: error("Missing string field: $name")

private fun JsonObject.stringOrNull(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.int(name: String): Int =
    this[name]?.jsonPrimitive?.content?.toIntOrNull() ?: error("Missing int field: $name")