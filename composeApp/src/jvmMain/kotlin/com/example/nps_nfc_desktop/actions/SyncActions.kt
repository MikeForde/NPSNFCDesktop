package com.example.nps_nfc_desktop.actions

import com.example.nps_nfc_desktop.model.AppState
import com.example.nps_nfc_desktop.services.DesfireNdefService
import com.example.nps_nfc_desktop.model.MainTab
import com.example.nps_nfc_desktop.nfc.NfcReader
import com.example.nps_nfc_desktop.services.NpsApiService
import com.example.nps_nfc_desktop.util.countBundleEntries
import com.example.nps_nfc_desktop.util.parseBundleSummary
import com.example.nps_nfc_desktop.util.prettyPrintJson
import com.example.nps_nfc_desktop.util.protectLabelToValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.nps_nfc_desktop.util.LegacyIpsBundleUtils

class SyncActions(
    scope: CoroutineScope,
    state: AppState,
    private val nfcReader: NfcReader,
    private val service: DesfireNdefService,
    private val api: NpsApiService
) : ActionSupport(scope, state) {

    private data class ServerSyncResult(
        val baseUrl: String,
        val existedBefore: Boolean,
        val refreshedRoJson: String,
        val refreshedRwJson: String
    )

    fun syncCard() {
        state.startOperation("Sync Card", "Checking available sources...")

        val desktopRwJsonSnapshot = state.payloadEditable.trim().takeIf { it.isNotBlank() }
        val fetchedRoJsonSnapshot = state.fetchedRoJson.trim().takeIf { it.isNotBlank() }

        scope.launch(Dispatchers.IO) {
            try {
                val syncTargets = state.baseUrlOptions.distinct()
                val currentProtect = protectLabelToValue(state.selectedProtectLabel)
                val serverFailures = mutableListOf<String>()

                val inspection = readCardIfPresent()

                val legacySplit = inspection?.let {
                    LegacyIpsBundleUtils.splitLegacyBundleIfNeeded(
                        mimeType = it.nps?.mimeType,
                        decompressedText = it.nps?.decompressedText
                    )
                }

                val cardNpsJson = legacySplit?.roJson ?: inspection?.nps?.decompressedText
                val cardExtraJson = legacySplit?.rwJson ?: inspection?.extra?.decompressedText.orEmpty()
                val cardAvailable = !cardNpsJson.isNullOrBlank() || cardExtraJson.isNotBlank()

                var bundleId =
                    parseBundleSummary(cardNpsJson)?.bundleId
                        ?: parseBundleSummary(cardExtraJson)?.bundleId
                        ?: parseBundleSummary(fetchedRoJsonSnapshot)?.bundleId
                        ?: parseBundleSummary(desktopRwJsonSnapshot)?.bundleId
                        ?: state.selectedRecordId.takeIf { it.isNotBlank() }

                val initiallyFetchedServers = mutableListOf<ServerSyncResult>()

                if (bundleId.isNullOrBlank()) {
                    val selectedId = state.selectedRecordId.takeIf { it.isNotBlank() }
                        ?: error("Could not determine bundle id from card, desktop state, or selected record.")

                    for (targetBaseUrl in syncTargets) {
                        try {
                            state.log("Sync Card: probing server $targetBaseUrl with selected record id $selectedId")

                            val fetched = api.fetchRecord(
                                baseUrl = targetBaseUrl,
                                id = selectedId,
                                protect = currentProtect
                            )

                            val fetchedBundleId =
                                parseBundleSummary(fetched.roJson)?.bundleId
                                    ?: parseBundleSummary(fetched.rwJson)?.bundleId
                                    ?: selectedId

                            bundleId = fetchedBundleId

                            initiallyFetchedServers += ServerSyncResult(
                                baseUrl = targetBaseUrl,
                                existedBefore = true,
                                refreshedRoJson = fetched.roJson,
                                refreshedRwJson = fetched.rwJson
                            )

                            state.log("Sync Card: bundle id resolved from server $targetBaseUrl as $bundleId")
                            break
                        } catch (serverError: Exception) {
                            val msg = "${serverError::class.simpleName}: ${serverError.message}"
                            state.log("Sync Card: server probe failed for $targetBaseUrl - $msg")
                            serverFailures += "$targetBaseUrl -> $msg"
                        }
                    }
                }

                bundleId = bundleId ?: error("Could not determine bundle id from any available source.")

                state.log("Sync Card: bundle id = $bundleId")
                state.log("Sync Card: card available = $cardAvailable")
                state.log("Sync Card: syncing against ${syncTargets.size} base URL(s)")

                val createSourceRoJson =
                    cardNpsJson
                        ?: fetchedRoJsonSnapshot
                        ?: initiallyFetchedServers.firstOrNull()?.refreshedRoJson

                val mergeSourceRwJson = when {
                    !desktopRwJsonSnapshot.isNullOrBlank() -> desktopRwJsonSnapshot
                    cardExtraJson.isNotBlank() -> cardExtraJson
                    initiallyFetchedServers.firstOrNull()?.refreshedRwJson?.isNotBlank() == true ->
                        initiallyFetchedServers.first().refreshedRwJson
                    else -> ""
                }

                val serverResults = reconcileServers(
                    syncTargets = syncTargets,
                    bundleId = bundleId,
                    protect = currentProtect,
                    createSourceRoJson = createSourceRoJson,
                    mergeSourceRwJson = mergeSourceRwJson,
                    initiallyFetchedServers = initiallyFetchedServers,
                    serverFailures = serverFailures
                )

                val unionRwJson = chooseBestRw(
                    cardExtraJson = cardExtraJson,
                    desktopRwJson = desktopRwJsonSnapshot,
                    serverRwJsons = serverResults.map { it.refreshedRwJson }
                )
                val unionRwCount = countBundleEntries(unionRwJson)
                val cardRwCount = countBundleEntries(cardExtraJson)

                pushUnionToBehindServers(
                    serverResults = serverResults,
                    unionRwJson = unionRwJson,
                    unionRwCount = unionRwCount
                )

                val finalServerResults = fetchFinalServerStates(
                    syncTargets = syncTargets,
                    bundleId = bundleId,
                    protect = currentProtect
                )

                val finalRwJson = chooseBestRw(
                    cardExtraJson = cardExtraJson,
                    desktopRwJson = desktopRwJsonSnapshot,
                    serverRwJsons = finalServerResults.map { it.refreshedRwJson }
                )

                val finalRwCount = countBundleEntries(finalRwJson)
                val updateAvailableForCard = cardAvailable && finalRwCount > cardRwCount

                val bestRoJson =
                    cardNpsJson
                        ?: fetchedRoJsonSnapshot
                        ?: finalServerResults.firstOrNull()?.refreshedRoJson
                        ?: initiallyFetchedServers.firstOrNull()?.refreshedRoJson
                        ?: ""

                withContext(Dispatchers.Main) {
                    state.cardWasLegacy = legacySplit != null
                    state.currentCardRoJson = cardNpsJson ?: ""
                    state.cardInfoText = buildString {
                        appendLine("Sync complete for bundle: $bundleId")
                        appendLine("Card available: $cardAvailable")
                        appendLine("Card format: ${if (legacySplit != null) "Legacy single-record (normalized to RO/RW)" else "Standard split RO/RW"}")
                        appendLine("Reachable servers: ${finalServerResults.size}/${syncTargets.size}")
                        appendLine("Desktop RW entries: ${countBundleEntries(desktopRwJsonSnapshot)}")
                        appendLine("Card RW entries: $cardRwCount")
                        appendLine("Reconciled RW entries: $finalRwCount")
                        appendLine()
                        appendLine("Source states:")
                        appendLine("- Card RW entries: $cardRwCount")
                        appendLine("- Desktop RW entries: ${countBundleEntries(desktopRwJsonSnapshot)}")
                        appendLine()
                        appendLine("Server states:")
                        if (finalServerResults.isEmpty()) {
                            appendLine("- No servers reachable")
                        } else {
                            finalServerResults.forEach { result ->
                                appendLine("- ${result.baseUrl}")
                                appendLine("  RW entries: ${countBundleEntries(result.refreshedRwJson)}")
                            }
                        }
                        if (serverFailures.isNotEmpty()) {
                            appendLine()
                            appendLine("Unavailable servers:")
                            serverFailures.forEach { appendLine("- $it") }
                        }
                    }.trim()

                    state.payloadText = buildString {
                        appendLine("--- Best available RO / NPS source ---")
                        appendLine(if (bestRoJson.isNotBlank()) prettyPrintJson(bestRoJson) else "(not available)")
                        appendLine()
                        appendLine("--- Card EXTRA (E105) before sync ---")
                        appendLine(if (cardExtraJson.isNotBlank()) prettyPrintJson(cardExtraJson) else "(not available)")
                        appendLine()
                        appendLine("--- Reconciled RW after sync ---")
                        appendLine(if (finalRwJson.isNotBlank()) prettyPrintJson(finalRwJson) else "(not available)")
                    }.trim()

                    state.payloadRoText = prettyPrintJson(bestRoJson)


                    if (finalRwJson.isNotBlank()) {
                        state.fetchedRwJson = finalRwJson
                        state.payloadEditable = prettyPrintJson(finalRwJson)
                    }

                    if (bestRoJson.isNotBlank()) {
                        state.fetchedRoJson = bestRoJson
                    }

                    if (updateAvailableForCard) {
                        state.pendingServerUpdateRwJson = finalRwJson
                        state.pendingServerUpdateSummary =
                            "Card is behind the reconciled state. Offer write-back to card."
                        state.selectedTab = MainTab.EDIT_EXTRA
                        state.succeedOperation(
                            "Sync Card",
                            if (finalServerResults.isNotEmpty()) {
                                "Available sources reconciled. Card can now be updated."
                            } else {
                                "Desktop and available record state reconciled. Card can now be updated when presented."
                            }
                        )
                    } else {
                        state.pendingServerUpdateRwJson = ""
                        state.pendingServerUpdateSummary = ""
                        state.selectedTab = MainTab.CARD_INFO
                        state.succeedOperation(
                            "Sync Card",
                            when {
                                cardAvailable && finalServerResults.isNotEmpty() ->
                                    "Available card and server sources are aligned."
                                cardAvailable ->
                                    "Card and desktop state are aligned. No reachable servers."
                                finalServerResults.isNotEmpty() ->
                                    "Desktop and reachable server sources are aligned."
                                else ->
                                    "Only desktop data was available. Nothing else to sync."
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state.selectedTab = MainTab.LOG
                    state.failOperation("Sync Card", "${e::class.simpleName}: ${e.message}")
                }
            }
        }
    }

    private fun readCardIfPresent() = run {
        val cardPresent = nfcReader.isAnyCardPresent { msg -> log(msg) }

        if (cardPresent) {
            try {
                service.inspectCard { msg -> log(msg) }
            } catch (e: Exception) {
                state.log("Sync Card: card present but inspect failed - ${e::class.simpleName}: ${e.message}")
                null
            }
        } else {
            state.log("Sync Card: no card present, continuing without card")
            null
        }
    }

    private suspend fun reconcileServers(
        syncTargets: List<String>,
        bundleId: String,
        protect: Int,
        createSourceRoJson: String?,
        mergeSourceRwJson: String,
        initiallyFetchedServers: List<ServerSyncResult>,
        serverFailures: MutableList<String>
    ): MutableList<ServerSyncResult> {
        val serverResults = mutableListOf<ServerSyncResult>()
        serverResults += initiallyFetchedServers

        for (targetBaseUrl in syncTargets) {
            if (serverResults.any { it.baseUrl == targetBaseUrl }) continue

            try {
                state.log("Sync Card: checking server $targetBaseUrl")

                val existingServerRecord = api.tryFetchRecord(
                    baseUrl = targetBaseUrl,
                    id = bundleId,
                    protect = protect
                )

                if (existingServerRecord == null) {
                    if (!createSourceRoJson.isNullOrBlank()) {
                        state.log("Sync Card: record not found on $targetBaseUrl, creating from available RO/NPS source")
                        api.createOrMergeBundle(targetBaseUrl, createSourceRoJson)
                    } else {
                        state.log("Sync Card: record not found on $targetBaseUrl and no RO/NPS source available to create it")
                    }

                    if (mergeSourceRwJson.isNotBlank()) {
                        state.log("Sync Card: merging available RW source into $targetBaseUrl")
                        api.createOrMergeBundle(targetBaseUrl, mergeSourceRwJson)
                    }
                } else {
                    state.log("Sync Card: record exists on $targetBaseUrl")
                    if (mergeSourceRwJson.isNotBlank()) {
                        state.log("Sync Card: merging available RW source into $targetBaseUrl")
                        api.createOrMergeBundle(targetBaseUrl, mergeSourceRwJson)
                    }
                }

                val refreshed = api.fetchRecord(
                    baseUrl = targetBaseUrl,
                    id = bundleId,
                    protect = protect
                )

                serverResults += ServerSyncResult(
                    baseUrl = targetBaseUrl,
                    existedBefore = existingServerRecord != null,
                    refreshedRoJson = refreshed.roJson,
                    refreshedRwJson = refreshed.rwJson
                )
            } catch (serverError: Exception) {
                val msg = "${serverError::class.simpleName}: ${serverError.message}"
                state.log("Sync Card: server $targetBaseUrl unavailable - $msg")
                if (serverFailures.none { it.startsWith("$targetBaseUrl ->") }) {
                    serverFailures += "$targetBaseUrl -> $msg"
                }
            }
        }

        return serverResults
    }

    private fun chooseBestRw(
        cardExtraJson: String,
        desktopRwJson: String?,
        serverRwJsons: List<String>
    ): String {
        return buildList {
            if (cardExtraJson.isNotBlank()) add(cardExtraJson)
            if (!desktopRwJson.isNullOrBlank()) add(desktopRwJson)
            addAll(serverRwJsons.filter { it.isNotBlank() })
        }.maxByOrNull { countBundleEntries(it) } ?: ""
    }

    private fun pushUnionToBehindServers(
        serverResults: List<ServerSyncResult>,
        unionRwJson: String,
        unionRwCount: Int
    ) {
        for (server in serverResults) {
            try {
                val serverRwCount = countBundleEntries(server.refreshedRwJson)
                if (unionRwCount > serverRwCount && unionRwJson.isNotBlank()) {
                    state.log("Sync Card: server ${server.baseUrl} is behind ($serverRwCount < $unionRwCount), pushing reconciled RW")
                    api.createOrMergeBundle(server.baseUrl, unionRwJson)
                }
            } catch (serverPushError: Exception) {
                state.log("Sync Card: could not push reconciled RW to ${server.baseUrl} - ${serverPushError::class.simpleName}: ${serverPushError.message}")
            }
        }
    }

    private suspend fun fetchFinalServerStates(
        syncTargets: List<String>,
        bundleId: String,
        protect: Int
    ): MutableList<ServerSyncResult> {
        val finalServerResults = mutableListOf<ServerSyncResult>()
        for (targetBaseUrl in syncTargets) {
            try {
                val refreshed = api.fetchRecord(
                    baseUrl = targetBaseUrl,
                    id = bundleId,
                    protect = protect
                )
                finalServerResults += ServerSyncResult(
                    baseUrl = targetBaseUrl,
                    existedBefore = true,
                    refreshedRoJson = refreshed.roJson,
                    refreshedRwJson = refreshed.rwJson
                )
            } catch (serverError: Exception) {
                state.log("Sync Card: final fetch skipped for $targetBaseUrl - ${serverError::class.simpleName}: ${serverError.message}")
            }
        }
        return finalServerResults
    }
}