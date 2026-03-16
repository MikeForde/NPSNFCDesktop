package com.example.nps_nfc_desktop

import kotlinx.serialization.json.*
import java.time.OffsetDateTime
import java.time.ZoneOffset

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

private val appJson = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
}

private fun parseJsonObjectOrNull(jsonText: String?): JsonObject? {
    if (jsonText.isNullOrBlank()) return null
    return try {
        appJson.parseToJsonElement(jsonText) as? JsonObject
    } catch (_: Exception) {
        null
    }
}

private fun nextObservationNumber(vararg bundleJsons: String?): Int {
    val regex = Regex("^ob(\\d+)$", RegexOption.IGNORE_CASE)

    val maxFound = bundleJsons
        .mapNotNull(::parseJsonObjectOrNull)
        .flatMap { root ->
            val entries = root["entry"] as? JsonArray ?: JsonArray(emptyList())
            entries.mapNotNull { entryEl ->
                val entryObj = entryEl as? JsonObject ?: return@mapNotNull null
                val resourceObj = entryObj["resource"] as? JsonObject ?: return@mapNotNull null

                val resourceType = resourceObj["resourceType"]?.jsonPrimitive?.contentOrNull
                val id = resourceObj["id"]?.jsonPrimitive?.contentOrNull

                if (resourceType != "Observation" || id == null) return@mapNotNull null

                regex.matchEntire(id)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
        }
        .maxOrNull()

    return (maxFound ?: 0) + 1
}

fun buildBloodPressureEntry(
    observationId: String,
    systolic: Int,
    diastolic: Int,
    effectiveDateTime: String = OffsetDateTime.now(ZoneOffset.UTC).withNano(0).toString()
): JsonObject {
    return buildJsonObject {
        put(
            "resource",
            buildJsonObject {
                put("resourceType", JsonPrimitive("Observation"))
                put("id", JsonPrimitive(observationId))
                put("status", JsonPrimitive("final"))

                put(
                    "code",
                    buildJsonObject {
                        put(
                            "coding",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("display", JsonPrimitive("Blood Pressure"))
                                    }
                                )
                            }
                        )
                    }
                )

                put(
                    "subject",
                    buildJsonObject {
                        put("reference", JsonPrimitive("Patient/pt1"))
                    }
                )

                put("effectiveDateTime", JsonPrimitive(effectiveDateTime))

                put(
                    "component",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put(
                                    "code",
                                    buildJsonObject {
                                        put(
                                            "coding",
                                            buildJsonArray {
                                                add(
                                                    buildJsonObject {
                                                        put("system", JsonPrimitive("http://snomed.info/sct"))
                                                        put("code", JsonPrimitive("271649006"))
                                                        put("display", JsonPrimitive("Systolic blood pressure"))
                                                    }
                                                )
                                            }
                                        )
                                    }
                                )
                                put(
                                    "valueQuantity",
                                    buildJsonObject {
                                        put("value", JsonPrimitive(systolic))
                                        put("unit", JsonPrimitive("mm[Hg]"))
                                        put("system", JsonPrimitive("http://unitsofmeasure.org"))
                                        put("code", JsonPrimitive("mm[Hg]"))
                                    }
                                )
                            }
                        )

                        add(
                            buildJsonObject {
                                put(
                                    "code",
                                    buildJsonObject {
                                        put(
                                            "coding",
                                            buildJsonArray {
                                                add(
                                                    buildJsonObject {
                                                        put("system", JsonPrimitive("http://snomed.info/sct"))
                                                        put("code", JsonPrimitive("271650006"))
                                                        put("display", JsonPrimitive("Diastolic blood pressure"))
                                                    }
                                                )
                                            }
                                        )
                                    }
                                )
                                put(
                                    "valueQuantity",
                                    buildJsonObject {
                                        put("value", JsonPrimitive(diastolic))
                                        put("unit", JsonPrimitive("mm[Hg]"))
                                        put("system", JsonPrimitive("http://unitsofmeasure.org"))
                                        put("code", JsonPrimitive("mm[Hg]"))
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
    }
}

fun appendBloodPressureToExtraBundle(
    roJson: String?,
    rwJson: String?,
    systolic: Int,
    diastolic: Int
): String {
    val rwRoot = parseJsonObjectOrNull(rwJson)
        ?: error("RW / EXTRA JSON is empty or invalid.")

    val nextObId = "ob${nextObservationNumber(roJson, rwJson)}"

    val existingEntries = (rwRoot["entry"] as? JsonArray)?.toMutableList() ?: mutableListOf()
    existingEntries.add(
        buildBloodPressureEntry(
            observationId = nextObId,
            systolic = systolic,
            diastolic = diastolic
        )
    )

    val currentTotal = rwRoot["total"]?.jsonPrimitive?.intOrNull
    val updatedRoot = buildJsonObject {
        rwRoot.forEach { (key, value) ->
            when (key) {
                "entry" -> put("entry", JsonArray(existingEntries))
                "total" -> put("total", JsonPrimitive((currentTotal ?: existingEntries.size - 1) + 1))
                else -> put(key, value)
            }
        }

        if ("entry" !in rwRoot) {
            put("entry", JsonArray(existingEntries))
        }
        if ("total" !in rwRoot) {
            put("total", JsonPrimitive(existingEntries.size))
        }
    }

    return appJson.encodeToString(JsonObject.serializer(), updatedRoot)
}