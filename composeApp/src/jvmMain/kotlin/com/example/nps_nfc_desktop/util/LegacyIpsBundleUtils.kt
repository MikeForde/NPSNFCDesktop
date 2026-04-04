package com.example.nps_nfc_desktop.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

data class LegacySplitResult(
    val roJson: String,
    val rwJson: String,
    val cutoffIso: String,
    val wasLegacy: Boolean
)

object LegacyIpsBundleUtils {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun splitLegacyBundleIfNeeded(
        mimeType: String?,
        decompressedText: String?
    ): LegacySplitResult? {
        if (mimeType != "application/x.ips.gzip.v1-0") return null
        if (decompressedText.isNullOrBlank()) return null

        val bundle = json.parseToJsonElement(decompressedText).jsonObject
        val cutoffIso = bundle.stringOrNull("timestamp")
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Legacy IPS bundle has no timestamp")

        val (roBundle, rwBundle) = splitBundleByTimestamp(bundle, cutoffIso)

        return LegacySplitResult(
            roJson = json.encodeToString(JsonObject.serializer(), roBundle),
            rwJson = json.encodeToString(JsonObject.serializer(), rwBundle),
            cutoffIso = cutoffIso,
            wasLegacy = true
        )
    }

    fun splitBundleByTimestamp(
        bundle: JsonObject,
        cutoffIso: String
    ): Pair<JsonObject, JsonObject> {
        val cutoffMs = parseIso(cutoffIso)
            ?: throw IllegalArgumentException("Invalid cutoff timestamp: $cutoffIso")

        val entries = bundle["entry"]?.jsonArray ?: JsonArray(emptyList())

        val patientEntries = mutableListOf<JsonObject>()
        val orgEntries = mutableListOf<JsonObject>()
        val practitionerEntries = mutableListOf<JsonObject>()
        val coverageEntries = mutableListOf<JsonObject>()
        val otherRoEntries = mutableListOf<JsonObject>()
        val rwEntries = mutableListOf<JsonObject>()

        val medTimeByMedicationId = mutableMapOf<String, Long>()

        // First pass: MedicationRequest -> Medication authoredOn map
        for (entryEl in entries) {
            val entry = entryEl as? JsonObject ?: continue
            val resource = entry.objOrNull("resource") ?: continue
            if (resource.stringOrNull("resourceType") != "MedicationRequest") continue

            val authoredOnMs = parseIso(resource.stringOrNull("authoredOn")) ?: continue
            val ref = resource.objOrNull("medicationReference")
                ?.stringOrNull("reference")
                ?.takeIf { it.isNotBlank() }
                ?: continue

            val parts = ref.split("/")
            if (parts.size == 2 && parts[0] == "Medication") {
                medTimeByMedicationId[parts[1]] = authoredOnMs
            }
        }

        fun placeEntry(entry: JsonObject, dateMs: Long?) {
            val isAfter = dateMs != null && dateMs > cutoffMs
            if (isAfter) rwEntries += entry else otherRoEntries += entry
        }

        for (entryEl in entries) {
            val entry = entryEl as? JsonObject ?: continue
            val resource = entry.objOrNull("resource") ?: continue
            val resourceType = resource.stringOrNull("resourceType")

            when (resourceType) {
                "Patient" -> patientEntries += entry
                "Organization" -> orgEntries += entry
                "Practitioner" -> practitionerEntries += entry
                "Coverage" -> coverageEntries += entry

                "Medication" -> {
                    val medId = resource.stringOrNull("id")
                    val dateMs = medId?.let { medTimeByMedicationId[it] }
                    placeEntry(entry, dateMs)
                }

                else -> {
                    val dateMs = getResourceDateMillis(resource)
                    placeEntry(entry, dateMs)
                }
            }
        }

        val roEntries = mutableListOf<JsonObject>().apply {
            addAll(patientEntries)
            addAll(orgEntries)
            addAll(practitionerEntries)
            addAll(coverageEntries)
            addAll(otherRoEntries)
        }

        val roBundle = makeBundleLike(bundle, roEntries)
        val rwBundle = makeBundleLike(bundle, rwEntries)

        return roBundle to rwBundle
    }

    fun joinBundles(roJson: String, rwJson: String): String {
        val ro = json.parseToJsonElement(roJson).jsonObject
        val rw = json.parseToJsonElement(rwJson).jsonObject

        val roEntries = ro["entry"]?.jsonArray ?: JsonArray(emptyList())
        val rwEntries = rw["entry"]?.jsonArray ?: JsonArray(emptyList())

        val mergedEntries = buildJsonArray {
            roEntries.forEach { add(it) }
            rwEntries.forEach { add(it) }
        }

        val merged = buildJsonObject {
            put("resourceType", JsonPrimitive(ro.stringOrNull("resourceType") ?: "Bundle"))
            put("id", JsonPrimitive(ro.stringOrNull("id") ?: ""))
            put("timestamp", JsonPrimitive(ro.stringOrNull("timestamp") ?: ""))
            put("type", JsonPrimitive(ro.stringOrNull("type") ?: "document"))
            put("total", JsonPrimitive(mergedEntries.size))
            put("entry", mergedEntries)
        }

        return json.encodeToString(JsonObject.serializer(), merged)
    }

    private fun makeBundleLike(source: JsonObject, entries: List<JsonObject>): JsonObject {
        val arr = buildJsonArray {
            entries.forEach { add(it) }
        }

        return buildJsonObject {
            put("resourceType", JsonPrimitive(source.stringOrNull("resourceType") ?: "Bundle"))
            put("id", JsonPrimitive(source.stringOrNull("id") ?: ""))
            put("timestamp", JsonPrimitive(source.stringOrNull("timestamp") ?: ""))
            put("type", JsonPrimitive(source.stringOrNull("type") ?: "document"))
            put("total", JsonPrimitive(entries.size))
            put("entry", arr)
        }
    }

    private fun parseIso(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    private fun getResourceDateMillis(resource: JsonObject): Long? {
        return when (resource.stringOrNull("resourceType")) {
            "AllergyIntolerance" -> parseIso(resource.stringOrNull("onsetDateTime"))
            "Condition" -> parseIso(resource.stringOrNull("onsetDateTime"))
            "Observation" -> parseIso(resource.stringOrNull("effectiveDateTime"))
            "Procedure" -> parseIso(resource.stringOrNull("performedDateTime"))
            "MedicationRequest" -> parseIso(resource.stringOrNull("authoredOn"))
            else -> null
        }
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.objOrNull(key: String): JsonObject? =
        this[key] as? JsonObject

    private val JsonPrimitive.contentOrNull: String?
        get() = try {
            content
        } catch (_: Exception) {
            null
        }
}