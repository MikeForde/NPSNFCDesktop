package com.example.nps_nfc_desktop.util

import kotlinx.serialization.json.*

private val jsonPretty = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    ignoreUnknownKeys = true
}

private val jsonCompact = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
}

/**
 * Pretty print JSON safely.
 * Falls back to original string if parsing fails.
 */
fun prettyPrintJson(jsonText: String?): String {
    if (jsonText.isNullOrBlank()) return ""

    return try {
        val element = jsonCompact.parseToJsonElement(jsonText)
        jsonPretty.encodeToString(JsonElement.serializer(), element)
    } catch (_: Exception) {
        jsonText // fallback (important for resilience)
    }
}

/**
 * Parse JSON into JsonObject safely.
 */
fun parseJsonObjectOrNull(jsonText: String?): JsonObject? {
    if (jsonText.isNullOrBlank()) return null

    return try {
        jsonCompact.parseToJsonElement(jsonText) as? JsonObject
    } catch (_: Exception) {
        null
    }
}

/**
 * Safe helper to extract string field.
 */
fun JsonObject.getStringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

/**
 * Safe helper to extract int field.
 */
fun JsonObject.getIntOrNull(key: String): Int? =
    this[key]?.jsonPrimitive?.intOrNull

/**
 * Safe helper to extract array.
 */
fun JsonObject.getArrayOrEmpty(key: String): JsonArray =
    this[key] as? JsonArray ?: JsonArray(emptyList())