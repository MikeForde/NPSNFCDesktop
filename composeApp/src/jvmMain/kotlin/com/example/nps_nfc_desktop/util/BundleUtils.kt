package com.example.nps_nfc_desktop.util

import com.example.nps_nfc_desktop.model.ApiUpdateCheckResult
import com.example.nps_nfc_desktop.model.BundleSummary
import kotlinx.serialization.json.*

fun parseBundleSummary(json: String?): BundleSummary? {
    val root = parseJsonObjectOrNull(json) ?: return null

    val bundleId = root["id"]?.jsonPrimitive?.contentOrNull
    val total = root["total"]?.jsonPrimitive?.intOrNull

    val entries = root["entry"] as? JsonArray
    val entryKeys = entries
        ?.mapNotNull { entryEl ->
            val entryObj = entryEl as? JsonObject ?: return@mapNotNull null
            val resource = entryObj["resource"] as? JsonObject ?: return@mapNotNull null

            val type = resource["resourceType"]?.jsonPrimitive?.contentOrNull
            val id = resource["id"]?.jsonPrimitive?.contentOrNull

            if (type != null && id != null) "$type/$id" else null
        }
        ?.toSet()
        ?: emptySet()

    return BundleSummary(
        bundleId = bundleId,
        total = total,
        entryKeys = entryKeys
    )
}

fun countBundleEntries(json: String?): Int =
    parseBundleSummary(json)?.entryKeys?.size ?: 0

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