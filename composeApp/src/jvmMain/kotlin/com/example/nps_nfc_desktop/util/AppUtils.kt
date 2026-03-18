package com.example.nps_nfc_desktop.util

import com.example.nps_nfc_desktop.model.ObservationEntryType
import kotlinx.serialization.json.*
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val appJson = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
}

//private fun parseJsonObjectOrNull(jsonText: String?): JsonObject? {
//    if (jsonText.isNullOrBlank()) return null
//    return try {
//        appJson.parseToJsonElement(jsonText) as? JsonObject
//    } catch (_: Exception) {
//        null
//    }
//}

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

private fun buildQuantityValue(
    value: Double,
    unit: String,
    ucumCode: String
): JsonObject = buildJsonObject {
    put("value", JsonPrimitive(value))
    put("unit", JsonPrimitive(unit))
    put("system", JsonPrimitive("http://unitsofmeasure.org"))
    put("code", JsonPrimitive(ucumCode))
}

private fun buildSingleValueObservationEntry(
    observationId: String,
    type: ObservationEntryType,
    value: Double,
    effectiveDateTime: String
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
                                        put("display", JsonPrimitive(type.display))
                                        put("system", JsonPrimitive("http://snomed.info/sct"))
                                        put("code", JsonPrimitive(type.snomedCode))
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
                put("valueQuantity", buildQuantityValue(value, type.unit, type.ucumCode))
            }
        )
    }
}

private fun buildBloodPressureObservationEntry(
    observationId: String,
    systolic: Double,
    diastolic: Double,
    effectiveDateTime: String
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
                                put("valueQuantity", buildQuantityValue(systolic, "mm[Hg]", "mm[Hg]"))
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
                                put("valueQuantity", buildQuantityValue(diastolic, "mm[Hg]", "mm[Hg]"))
                            }
                        )
                    }
                )
            }
        )
    }
}

fun appendObservationToExtraBundle(
    roJson: String?,
    rwJson: String?,
    type: ObservationEntryType,
    primaryValue: Double,
    secondaryValue: Double? = null,
    effectiveDateTime: String = OffsetDateTime.now(ZoneOffset.UTC).withNano(0).toString()
): String {
    val rwRoot = parseJsonObjectOrNull(rwJson)
        ?: error("RW / EXTRA JSON is empty or invalid.")

    val nextObId = "ob${nextObservationNumber(roJson, rwJson)}"

    val newEntry = if (type.isBloodPressure) {
        val diastolic = secondaryValue ?: error("Blood pressure requires two values.")
        buildBloodPressureObservationEntry(
            observationId = nextObId,
            systolic = primaryValue,
            diastolic = diastolic,
            effectiveDateTime = effectiveDateTime
        )
    } else {
        buildSingleValueObservationEntry(
            observationId = nextObId,
            type = type,
            value = primaryValue,
            effectiveDateTime = effectiveDateTime
        )
    }

    val existingEntries = (rwRoot["entry"] as? JsonArray)?.toMutableList() ?: mutableListOf()
    existingEntries.add(newEntry)

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