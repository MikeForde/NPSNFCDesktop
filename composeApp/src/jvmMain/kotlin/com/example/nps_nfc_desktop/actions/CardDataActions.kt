package com.example.nps_nfc_desktop.actions

import com.example.nps_nfc_desktop.model.ApiUpdateCheckResult
import com.example.nps_nfc_desktop.model.AppState
import com.example.nps_nfc_desktop.services.CardStateKind
import com.example.nps_nfc_desktop.services.DesfireNdefService
import com.example.nps_nfc_desktop.model.MainTab
import com.example.nps_nfc_desktop.services.NpsApiService
import com.example.nps_nfc_desktop.util.buildApiUpdateCheckResult
import com.example.nps_nfc_desktop.util.parseBundleSummary
import com.example.nps_nfc_desktop.util.prettyPrintJson
import com.example.nps_nfc_desktop.util.protectLabelToValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CardDataActions(
    scope: CoroutineScope,
    state: AppState,
    private val service: DesfireNdefService,
    private val api: NpsApiService
) : ActionSupport(scope, state) {

    fun inspectCard() {
        state.startOperation("Inspect Card", "Waiting for card...")
        scope.launch(Dispatchers.IO) {
            try {
                val currentBaseUrl = state.baseUrl
                val currentProtect = protectLabelToValue(state.selectedProtectLabel)

                val inspection = service.inspectCard { msg -> log(msg) }

                val cardNpsJson = inspection.nps?.decompressedText
                val cardExtraJson = inspection.extra?.decompressedText
                val cardBundleId =
                    parseBundleSummary(cardNpsJson)?.bundleId
                        ?: parseBundleSummary(cardExtraJson)?.bundleId

                var updateCheck: ApiUpdateCheckResult? = null

                if (!cardBundleId.isNullOrBlank()) {
                    try {
                        state.log("Inspect Card: checking API for bundle $cardBundleId")
                        val loaded = api.fetchRecord(currentBaseUrl, cardBundleId, currentProtect)
                        updateCheck = buildApiUpdateCheckResult(
                            fetchedRoJson = loaded.roJson,
                            fetchedRwJson = loaded.rwJson,
                            cardExtraJson = cardExtraJson
                        )
                    } catch (apiError: Exception) {
                        state.log("Inspect Card: API update check skipped - ${apiError::class.simpleName}: ${apiError.message}")
                    }
                } else {
                    state.log("Inspect Card: no bundle id found on card, skipping API update check")
                }

                withContext(Dispatchers.Main) {
                    state.cardInfoText = inspection.toDisplayText()

                    val prettyNps = inspection.nps?.decompressedText
                        ?.let(::prettyPrintJson)
                        ?: "(not available)"

                    val prettyExtra = inspection.extra?.decompressedText
                        ?.let(::prettyPrintJson)
                        ?: "(not available)"

                    state.payloadText = buildString {
                        appendLine("--- NPS (E104) ---")
                        appendLine(prettyNps)
                        appendLine()
                        appendLine("--- EXTRA (E105) ---")
                        appendLine(prettyExtra)
                    }.trim()

                    state.payloadEditable = inspection.extra?.decompressedText
                        ?.let(::prettyPrintJson)
                        ?: ""

                    state.selectedTab = MainTab.CARD_INFO

                    val stateMessage: String = when {
                        updateCheck?.hasUpdate == true -> {
                            state.pendingApiUpdateRwJson = updateCheck.fetchedRwJson
                            state.pendingApiUpdateBundleId = updateCheck.bundleId
                            state.pendingApiUpdateSummary =
                                "API has ${updateCheck.newEntryKeys.size} additional RW entries not present on the card."
                            "Update available from API. ${updateCheck.newEntryKeys.size} additional RW entries detected."
                        }

                        updateCheck != null -> {
                            state.pendingApiUpdateRwJson = ""
                            state.pendingApiUpdateBundleId = ""
                            state.pendingApiUpdateSummary = "Card RW already matches the fetched API RW content."
                            "Card is up to date with the selected API source."
                        }

                        else -> {
                            state.pendingApiUpdateRwJson = ""
                            state.pendingApiUpdateBundleId = ""
                            state.pendingApiUpdateSummary = ""

                            when (inspection.state.kind) {
                                CardStateKind.NATO_FORMATTED ->
                                    "Existing NATO card detected. No API update result available"
                                CardStateKind.BLANK_OR_UNFORMATTED ->
                                    "Blank or unformatted card detected."
                                CardStateKind.PARTIAL_OR_UNEXPECTED ->
                                    "Card has partial/unexpected structure. No API update result available."
                                CardStateKind.ERROR ->
                                    "Card read completed, but classification is uncertain."
                            }
                        }
                    }

                    state.succeedOperation("Inspect Card", stateMessage)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state.selectedTab = MainTab.LOG
                    state.failOperation("Inspect Card", "${e::class.simpleName}: ${e.message}")
                }
            }
        }
    }

    fun readNps() {
        state.startOperation("Read NPS", "Waiting for card...")
        scope.launch(Dispatchers.IO) {
            try {
                val parsed = service.readNps { msg -> log(msg) }

                withContext(Dispatchers.Main) {
                    state.payloadText = parsed.decompressedText
                        ?.let(::prettyPrintJson)
                        ?: "(Payload read, but not decompressed)"

                    state.cardInfoText = buildString {
                        appendLine("NPS MIME: ${parsed.mimeType}")
                        appendLine("Compressed bytes: ${parsed.compressedPayload.size}")
                    }.trim()

                    state.selectedTab = MainTab.PAYLOAD
                    state.succeedOperation("Read NPS", "Historic NPS payload read successfully.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state.selectedTab = MainTab.LOG
                    state.failOperation("Read NPS", "${e::class.simpleName}: ${e.message}")
                }
            }
        }
    }

    fun readExtra() {
        state.startOperation("Read EXTRA", "Waiting for card...")
        scope.launch(Dispatchers.IO) {
            try {
                val parsed = service.readExtra { msg -> log(msg) }

                withContext(Dispatchers.Main) {
                    val pretty = parsed.decompressedText
                        ?.let(::prettyPrintJson)
                        ?: "(Payload read, but not decompressed)"

                    state.payloadText = pretty
                    state.payloadEditable = pretty
                    state.fetchedRwJson = parsed.decompressedText ?: ""

                    state.cardInfoText = buildString {
                        appendLine("EXTRA MIME: ${parsed.mimeType}")
                        appendLine("Compressed bytes: ${parsed.compressedPayload.size}")
                    }.trim()

                    state.selectedTab = MainTab.EDIT_EXTRA
                    state.succeedOperation("Read EXTRA", "Operational payload read successfully.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state.selectedTab = MainTab.LOG
                    state.failOperation("Read EXTRA", "${e::class.simpleName}: ${e.message}")
                }
            }
        }
    }

    fun writeExtra() {
        state.startOperation("Write EXTRA", "Waiting for card...")
        scope.launch(Dispatchers.IO) {
            try {
                val parsed = service.writeExtraJson(
                    jsonText = state.payloadEditable,
                    onStatus = { msg -> log(msg) }
                )

                withContext(Dispatchers.Main) {
                    val pretty = parsed.decompressedText
                        ?.let(::prettyPrintJson)
                        ?: "(Payload written, but not decompressed on verify)"

                    state.payloadText = pretty
                    state.payloadEditable = pretty
                    state.fetchedRwJson = parsed.decompressedText ?: state.fetchedRwJson

                    state.cardInfoText = buildString {
                        appendLine("EXTRA MIME: ${parsed.mimeType}")
                        appendLine("Compressed bytes: ${parsed.compressedPayload.size}")
                        appendLine("Write/verify: OK")
                    }.trim()

                    state.selectedTab = MainTab.PAYLOAD
                    state.succeedOperation("Write EXTRA", "Operational payload written and verified.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state.selectedTab = MainTab.LOG
                    state.failOperation("Write EXTRA", "${e::class.simpleName}: ${e.message}")
                }
            }
        }
    }

    fun applyApiUpdate() {
        state.startOperation("Apply API Update", "Waiting for card...")
        scope.launch(Dispatchers.IO) {
            try {
                val rwToWrite = when {
                    state.pendingServerUpdateRwJson.isNotBlank() -> state.pendingServerUpdateRwJson
                    state.pendingApiUpdateRwJson.isNotBlank() -> state.pendingApiUpdateRwJson
                    else -> error("No pending update available. Run Inspect Card or Sync Card first.")
                }

                val parsed = service.writeExtraJson(
                    jsonText = rwToWrite,
                    onStatus = { msg -> log(msg) }
                )

                withContext(Dispatchers.Main) {
                    val pretty = parsed.decompressedText
                        ?.let(::prettyPrintJson)
                        ?: "(Payload written, but not decompressed on verify)"

                    state.payloadText = pretty
                    state.payloadEditable = pretty
                    state.fetchedRwJson = parsed.decompressedText ?: state.fetchedRwJson

                    state.cardInfoText = buildString {
                        appendLine("API update applied to E105.")
                        appendLine("Bundle ID: ${state.pendingApiUpdateBundleId.ifBlank { "(unknown)" }}")
                        appendLine("EXTRA MIME: ${parsed.mimeType}")
                        appendLine("Compressed bytes: ${parsed.compressedPayload.size}")
                        appendLine("Write/verify: OK")
                    }.trim()

                    state.pendingApiUpdateRwJson = ""
                    state.pendingApiUpdateBundleId = ""
                    state.pendingApiUpdateSummary = ""
                    state.pendingServerUpdateRwJson = ""
                    state.pendingServerUpdateSummary = ""

                    state.selectedTab = MainTab.PAYLOAD
                    state.succeedOperation("Apply API Update", "Additional RW data written to card successfully.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state.selectedTab = MainTab.LOG
                    state.failOperation("Apply API Update", "${e::class.simpleName}: ${e.message}")
                }
            }
        }
    }
}