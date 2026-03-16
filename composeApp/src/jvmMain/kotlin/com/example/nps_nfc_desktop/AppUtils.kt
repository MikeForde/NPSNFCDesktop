package com.example.nps_nfc_desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

fun protectLabelToValue(label: String): Int =
    label.substringBefore(" ").toIntOrNull() ?: 0

fun prettyPrintJson(input: String): String {
    val text = input.trim()
    if (text.isEmpty()) return text

    val out = StringBuilder()
    var indent = 0
    var inString = false
    var escaping = false

    fun appendIndent(level: Int) {
        repeat(level) { out.append("  ") }
    }

    for (ch in text) {
        when {
            escaping -> {
                out.append(ch)
                escaping = false
            }

            ch == '\\' && inString -> {
                out.append(ch)
                escaping = true
            }

            ch == '"' -> {
                out.append(ch)
                inString = !inString
            }

            inString -> {
                out.append(ch)
            }

            ch == '{' || ch == '[' -> {
                out.append(ch)
                out.append('\n')
                indent++
                appendIndent(indent)
            }

            ch == '}' || ch == ']' -> {
                out.append('\n')
                indent--
                appendIndent(indent)
                out.append(ch)
            }

            ch == ',' -> {
                out.append(ch)
                out.append('\n')
                appendIndent(indent)
            }

            ch == ':' -> {
                out.append(": ")
            }

            ch.isWhitespace() -> {
            }

            else -> {
                out.append(ch)
            }
        }
    }

    return out.toString()
}

fun parseBundleSummary(jsonText: String?): BundleSummary? {
    if (jsonText.isNullOrBlank()) return null

    return try {
        val root = Json.parseToJsonElement(jsonText)
        val obj = root as? JsonObject ?: return null

        val bundleId = obj["id"]?.jsonPrimitive?.content
        val total = obj["total"]?.jsonPrimitive?.content?.toIntOrNull()

        val entries = obj["entry"] as? JsonArray
        val keys = entries
            ?.mapNotNull { entryEl ->
                val entryObj = entryEl as? JsonObject ?: return@mapNotNull null
                val resourceObj = entryObj["resource"] as? JsonObject ?: return@mapNotNull null
                val resourceType = resourceObj["resourceType"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val resourceId = resourceObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                "$resourceType/$resourceId"
            }
            ?.toSet()
            ?: emptySet()

        BundleSummary(
            bundleId = bundleId,
            total = total,
            entryKeys = keys
        )
    } catch (_: Exception) {
        null
    }
}

fun buildApiUpdateCheckResult(
    fetchedRoJson: String,
    fetchedRwJson: String,
    cardExtraJson: String?
): ApiUpdateCheckResult? {
    val fetchedRoSummary = parseBundleSummary(fetchedRoJson) ?: return null
    val fetchedRwSummary = parseBundleSummary(fetchedRwJson) ?: return null
    val cardExtraSummary = parseBundleSummary(cardExtraJson)

    val newEntryKeys = fetchedRwSummary.entryKeys - (cardExtraSummary?.entryKeys ?: emptySet())

    return ApiUpdateCheckResult(
        bundleId = fetchedRoSummary.bundleId ?: "",
        fetchedRoJson = fetchedRoJson,
        fetchedRwJson = fetchedRwJson,
        cardExtraSummary = cardExtraSummary,
        fetchedRwSummary = fetchedRwSummary,
        newEntryKeys = newEntryKeys
    )
}

fun countBundleEntries(jsonText: String?): Int {
    val summary = parseBundleSummary(jsonText)
    return summary?.entryKeys?.size ?: 0
}