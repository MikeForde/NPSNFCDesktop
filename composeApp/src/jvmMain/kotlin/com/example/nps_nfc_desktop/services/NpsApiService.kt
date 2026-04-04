package com.example.nps_nfc_desktop.services

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun createOrMergeBundle(baseUrl: String, bundleJson: String): String {
        return httpPostJson(
            url = normalizeBaseUrl(baseUrl) + "/ipsbundle",
            jsonBody = bundleJson
        )
    }

    fun tryFetchRecord(baseUrl: String, id: String, protect: Int): LoadedApiRecord? {
        return try {
            fetchRecord(baseUrl, id, protect)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("HTTP 404") || msg.contains("HTTP 400")) {
                null
            } else {
                throw e
            }
        }
    }

    fun listRecords(baseUrl: String): List<IpsListItem> {
        val body = httpGet(normalizeBaseUrl(baseUrl) + "/ips/list")

        val root = json.parseToJsonElement(body)
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
        val body = httpGet("${normalizeBaseUrl(baseUrl)}/npsnfc/$encodedId?protect=$protect")

        val root = json.parseToJsonElement(body)
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

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Connection", "close")
            instanceFollowRedirects = true
        }

        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: error("HTTP $status with no response body")
            }

            val text = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            require(status in 200..299) {
                "Request failed: HTTP $status${if (text.isNotBlank()) " - $text" else ""}"
            }

            text
        } finally {
            connection.disconnect()
        }
    }

    private fun httpPostJson(url: String, jsonBody: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Connection", "close")
            instanceFollowRedirects = true
        }

        return try {
            connection.outputStream.use { out ->
                out.write(jsonBody.toByteArray(StandardCharsets.UTF_8))
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: error("HTTP $status with no response body")
            }

            val text = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            require(status in 200..299) {
                "Request failed: HTTP $status${if (text.isNotBlank()) " - $text" else ""}"
            }

            text
        } finally {
            connection.disconnect()
        }
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
    this[name]?.jsonPrimitive?.content

private fun JsonObject.int(name: String): Int =
    this[name]?.jsonPrimitive?.content?.toIntOrNull() ?: error("Missing int field: $name")