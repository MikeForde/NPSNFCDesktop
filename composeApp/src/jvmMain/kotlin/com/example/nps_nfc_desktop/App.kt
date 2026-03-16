package com.example.nps_nfc_desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun App() {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        val nfcReader = remember { NfcReader() }
        val service = remember { DesfireNdefService(nfcReader) }
        val api = remember { NpsApiService() }
        val adminService = remember { DesfireAdminService(nfcReader) }

        var cardInfoText by remember { mutableStateOf("(none)") }
        var payloadText by remember { mutableStateOf("(none)") }
        var payloadEditable by remember { mutableStateOf("") }
        var selectedTab by remember { mutableStateOf(MainTab.CARD_INFO) }

        var operationState by remember { mutableStateOf(OperationState.IDLE) }
        var operationTitle by remember { mutableStateOf("Ready") }
        var operationMessage by remember { mutableStateOf("Tap an action to begin.") }

        var baseUrl by remember { mutableStateOf("https://ipsmern-dep.azurewebsites.net") }
        val protectOptions = listOf(
            "0 - none",
            "1 - field-level encryption (JWE)",
            "2 - omit identifiers"
        )
        var selectedProtectLabel by remember { mutableStateOf(protectOptions.first()) }
        var protectMenuExpanded by remember { mutableStateOf(false) }

        var availableRecords by remember { mutableStateOf<List<IpsListItem>>(emptyList()) }
        var selectedRecordId by remember { mutableStateOf("") }
        var selectedRecordLabel by remember { mutableStateOf("") }
        var recordMenuExpanded by remember { mutableStateOf(false) }

        var fetchedRoJson by remember { mutableStateOf("") }
        var fetchedRwJson by remember { mutableStateOf("") }

        var pendingRebuildConfirmation by remember { mutableStateOf(false) }
        var pendingRebuildUid by remember { mutableStateOf<String?>(null) }
        var pendingRebuildSummary by remember { mutableStateOf("") }

        var pendingApiUpdateRwJson by remember { mutableStateOf("") }
        var pendingApiUpdateSummary by remember { mutableStateOf("") }
        var pendingApiUpdateBundleId by remember { mutableStateOf("") }

        var pendingServerUpdateRwJson by remember { mutableStateOf("") }
        var pendingServerUpdateSummary by remember { mutableStateOf("") }

        val logLines = remember { mutableStateListOf("Idle") }

        val baseUrlOptions = listOf(
            "https://ipsmern-dep.azurewebsites.net",
            "http://localhost:5049"
        )
        var baseUrlMenuExpanded by remember { mutableStateOf(false) }

        fun log(msg: String) {
            logLines.add(msg)
            while (logLines.size > 120) {
                logLines.removeAt(0)
            }
        }

        fun startOperation(title: String, message: String) {
            operationState = OperationState.WORKING
            operationTitle = title
            operationMessage = message
            log("$title: started")
        }

        fun succeedOperation(title: String, message: String) {
            operationState = OperationState.SUCCESS
            operationTitle = title
            operationMessage = message
            log("$title: success")
        }

        fun failOperation(title: String, message: String) {
            operationState = OperationState.ERROR
            operationTitle = title
            operationMessage = message
            log("$title: error - $message")
        }

        fun clearRebuildConfirmation() {
            pendingRebuildConfirmation = false
            pendingRebuildUid = null
            pendingRebuildSummary = ""
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "NPS-NFC-Desktop",
                style = MaterialTheme.typography.headlineSmall
            )

            TopConfigRow(
                baseUrl = baseUrl,
                baseUrlOptions = baseUrlOptions,
                baseUrlMenuExpanded = baseUrlMenuExpanded,
                onBaseUrlMenuExpandedChange = { baseUrlMenuExpanded = it },
                onBaseUrlSelected = {
                    baseUrl = it
                    baseUrlMenuExpanded = false
                },
                protectOptions = protectOptions,
                selectedProtectLabel = selectedProtectLabel,
                protectMenuExpanded = protectMenuExpanded,
                onProtectMenuExpandedChange = { protectMenuExpanded = it },
                onProtectSelected = {
                    selectedProtectLabel = it
                    protectMenuExpanded = false
                },
                onLoadApiList = {
                    startOperation("Load API List", "Loading available records...")
                    scope.launch(Dispatchers.IO) {
                        try {
                            val list = api.listRecords(baseUrl)
                            withContext(Dispatchers.Main) {
                                availableRecords = list
                                if (list.isNotEmpty()) {
                                    selectedRecordId = list.first().packageUUID
                                    selectedRecordLabel = list.first().displayLabel()
                                } else {
                                    selectedRecordId = ""
                                    selectedRecordLabel = ""
                                }
                                selectedTab = MainTab.LOG
                                succeedOperation("Load API List", "Loaded ${list.size} record(s).")
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                selectedTab = MainTab.LOG
                                failOperation("Load API List", "${e::class.simpleName}: ${e.message}")
                            }
                        }
                    }
                }
            )

            RecordSelectionRow(
                availableRecords = availableRecords,
                selectedRecordLabel = selectedRecordLabel,
                recordMenuExpanded = recordMenuExpanded,
                onRecordMenuExpandedChange = { recordMenuExpanded = it },
                onRecordSelected = { item ->
                    selectedRecordId = item.packageUUID
                    selectedRecordLabel = item.displayLabel()
                    recordMenuExpanded = false
                },
                onFetchRecord = {
                    startOperation("Fetch Record", "Fetching selected record...")
                    scope.launch(Dispatchers.IO) {
                        try {
                            require(selectedRecordId.isNotBlank()) { "No record selected" }
                            val protect = protectLabelToValue(selectedProtectLabel)
                            val loaded = api.fetchRecord(baseUrl, selectedRecordId, protect)

                            withContext(Dispatchers.Main) {
                                val prettyRo = prettyPrintJson(loaded.roJson)
                                val prettyRw = prettyPrintJson(loaded.rwJson)

                                fetchedRoJson = loaded.roJson
                                fetchedRwJson = loaded.rwJson

                                cardInfoText = buildString {
                                    appendLine("Source package: ${loaded.meta.id}")
                                    appendLine("Cutoff: ${loaded.meta.cutoff}")
                                    appendLine("Protect: $selectedProtectLabel")
                                    appendLine("Protect (server): ${loaded.meta.protect}")
                                    appendLine("Encoding: ${loaded.meta.encoding}")
                                    appendLine("RO JSON bytes: ${loaded.meta.roBytesJson}")
                                    appendLine("RW JSON bytes: ${loaded.meta.rwBytesJson}")
                                    appendLine("RO gzip bytes: ${loaded.meta.roBytesGz}")
                                    appendLine("RW gzip bytes: ${loaded.meta.rwBytesGz}")
                                }.trim()

                                payloadText = buildString {
                                    appendLine("--- RO / E104 candidate ---")
                                    appendLine(prettyRo)
                                    appendLine()
                                    appendLine("--- RW / E105 candidate ---")
                                    appendLine(prettyRw)
                                }.trim()

                                payloadEditable = prettyRw
                                selectedTab = MainTab.EDIT_EXTRA
                                succeedOperation("Fetch Record", "Record fetched and decoded.")
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                selectedTab = MainTab.LOG
                                failOperation("Fetch Record", "${e::class.simpleName}: ${e.message}")
                            }
                        }
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(2f)) {
                    CardToolsSection(
                        onInspectCard = {
                            startOperation("Inspect Card", "Waiting for card...")
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val currentBaseUrl = baseUrl
                                    val currentProtect = protectLabelToValue(selectedProtectLabel)

                                    val inspection = service.inspectCard { msg ->
                                        scope.launch { log(msg) }
                                    }

                                    val cardNpsJson = inspection.nps?.decompressedText
                                    val cardExtraJson = inspection.extra?.decompressedText
                                    val cardBundleId =
                                        parseBundleSummary(cardNpsJson)?.bundleId
                                            ?: parseBundleSummary(cardExtraJson)?.bundleId

                                    var updateCheck: ApiUpdateCheckResult? = null

                                    if (!cardBundleId.isNullOrBlank()) {
                                        try {
                                            log("Inspect Card: checking API for bundle $cardBundleId")
                                            val loaded = api.fetchRecord(currentBaseUrl, cardBundleId, currentProtect)
                                            updateCheck = buildApiUpdateCheckResult(
                                                fetchedRoJson = loaded.roJson,
                                                fetchedRwJson = loaded.rwJson,
                                                cardExtraJson = cardExtraJson
                                            )
                                        } catch (apiError: Exception) {
                                            log("Inspect Card: API update check skipped - ${apiError::class.simpleName}: ${apiError.message}")
                                        }
                                    } else {
                                        log("Inspect Card: no bundle id found on card, skipping API update check")
                                    }

                                    withContext(Dispatchers.Main) {
                                        cardInfoText = inspection.toDisplayText()

                                        val prettyNps = inspection.nps?.decompressedText
                                            ?.let(::prettyPrintJson)
                                            ?: "(not available)"

                                        val prettyExtra = inspection.extra?.decompressedText
                                            ?.let(::prettyPrintJson)
                                            ?: "(not available)"

                                        payloadText = buildString {
                                            appendLine("--- NPS (E104) ---")
                                            appendLine(prettyNps)
                                            appendLine()
                                            appendLine("--- EXTRA (E105) ---")
                                            appendLine(prettyExtra)
                                        }.trim()

                                        payloadEditable = inspection.extra?.decompressedText
                                            ?.let(::prettyPrintJson)
                                            ?: ""

                                        selectedTab = MainTab.CARD_INFO

                                        val stateMessage: String = when {
                                            updateCheck?.hasUpdate == true -> {
                                                pendingApiUpdateRwJson = updateCheck.fetchedRwJson
                                                pendingApiUpdateBundleId = updateCheck.bundleId
                                                pendingApiUpdateSummary =
                                                    "API has ${updateCheck.newEntryKeys.size} additional RW entries not present on the card."
                                                "Update available from API. ${updateCheck.newEntryKeys.size} additional RW entries detected."
                                            }

                                            updateCheck != null -> {
                                                pendingApiUpdateRwJson = ""
                                                pendingApiUpdateBundleId = ""
                                                pendingApiUpdateSummary = "Card RW already matches the fetched API RW content."
                                                "Card is up to date with the selected API source."
                                            }

                                            else -> {
                                                pendingApiUpdateRwJson = ""
                                                pendingApiUpdateBundleId = ""
                                                pendingApiUpdateSummary = ""

                                                when (inspection.state.kind) {
                                                    CardStateKind.NATO_FORMATTED ->
                                                        "Existing NATO card detected. No API update result available."
                                                    CardStateKind.BLANK_OR_UNFORMATTED ->
                                                        "Blank or unformatted card detected."
                                                    CardStateKind.PARTIAL_OR_UNEXPECTED ->
                                                        "Card has partial/unexpected structure. No API update result available."
                                                    CardStateKind.ERROR ->
                                                        "Card read completed, but classification is uncertain."
                                                }
                                            }
                                        }

                                        succeedOperation("Inspect Card", stateMessage)
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        selectedTab = MainTab.LOG
                                        failOperation("Inspect Card", "${e::class.simpleName}: ${e.message}")
                                    }
                                }
                            }
                        },
                        onSyncCard = {
                            startOperation("Sync Card", "Waiting for card...")
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val syncTargets = baseUrlOptions.distinct()
                                    val currentProtect = protectLabelToValue(selectedProtectLabel)

                                    val inspection = service.inspectCard { msg ->
                                        scope.launch { log(msg) }
                                    }

                                    val cardNpsJson = inspection.nps?.decompressedText
                                        ?: error("Card does not contain readable NPS data in E104.")

                                    val cardExtraJson = inspection.extra?.decompressedText ?: ""
                                    val bundleId =
                                        parseBundleSummary(cardNpsJson)?.bundleId
                                            ?: error("Could not determine bundle id from card NPS data.")

                                    log("Sync Card: bundle id on card = $bundleId")
                                    log("Sync Card: syncing against ${syncTargets.size} base URL(s)")

                                    data class ServerSyncResult(
                                        val baseUrl: String,
                                        val existedBefore: Boolean,
                                        val refreshedRoJson: String,
                                        val refreshedRwJson: String
                                    )

                                    val serverResults = mutableListOf<ServerSyncResult>()

                                    // Step 1: ensure each server has at least the card content
                                    for (targetBaseUrl in syncTargets) {
                                        log("Sync Card: checking server $targetBaseUrl")

                                        val existingServerRecord = api.tryFetchRecord(
                                            baseUrl = targetBaseUrl,
                                            id = bundleId,
                                            protect = currentProtect
                                        )

                                        if (existingServerRecord == null) {
                                            log("Sync Card: record not found on $targetBaseUrl, creating from card NPS bundle")
                                            api.createOrMergeBundle(targetBaseUrl, cardNpsJson)

                                            if (cardExtraJson.isNotBlank()) {
                                                log("Sync Card: merging card EXTRA bundle into $targetBaseUrl")
                                                api.createOrMergeBundle(targetBaseUrl, cardExtraJson)
                                            }
                                        } else {
                                            log("Sync Card: record exists on $targetBaseUrl, merging card EXTRA bundle")
                                            if (cardExtraJson.isNotBlank()) {
                                                api.createOrMergeBundle(targetBaseUrl, cardExtraJson)
                                            }
                                        }

                                        val refreshed = api.fetchRecord(
                                            baseUrl = targetBaseUrl,
                                            id = bundleId,
                                            protect = currentProtect
                                        )

                                        serverResults += ServerSyncResult(
                                            baseUrl = targetBaseUrl,
                                            existedBefore = existingServerRecord != null,
                                            refreshedRoJson = refreshed.roJson,
                                            refreshedRwJson = refreshed.rwJson
                                        )
                                    }

                                    // Step 2: determine the most complete RW payload
                                    val candidateRwJsons = buildList {
                                        if (cardExtraJson.isNotBlank()) add(cardExtraJson)
                                        addAll(serverResults.map { it.refreshedRwJson })
                                    }

                                    val unionRwJson = candidateRwJsons.maxByOrNull { countBundleEntries(it) } ?: ""
                                    val unionRwCount = countBundleEntries(unionRwJson)
                                    val cardRwCount = countBundleEntries(cardExtraJson)

                                    // Step 3: push the most complete RW back to any server that is behind
                                    for (server in serverResults) {
                                        val serverRwCount = countBundleEntries(server.refreshedRwJson)
                                        if (unionRwCount > serverRwCount && unionRwJson.isNotBlank()) {
                                            log("Sync Card: server ${server.baseUrl} is behind ($serverRwCount < $unionRwCount), pushing union RW")
                                            api.createOrMergeBundle(server.baseUrl, unionRwJson)
                                        }
                                    }

                                    // Step 4: refetch final server states after convergence
                                    val finalServerResults = syncTargets.map { targetBaseUrl ->
                                        val refreshed = api.fetchRecord(
                                            baseUrl = targetBaseUrl,
                                            id = bundleId,
                                            protect = currentProtect
                                        )
                                        ServerSyncResult(
                                            baseUrl = targetBaseUrl,
                                            existedBefore = true,
                                            refreshedRoJson = refreshed.roJson,
                                            refreshedRwJson = refreshed.rwJson
                                        )
                                    }

                                    val finalRwJson = buildList {
                                        if (cardExtraJson.isNotBlank()) add(cardExtraJson)
                                        addAll(finalServerResults.map { it.refreshedRwJson })
                                    }.maxByOrNull { countBundleEntries(it) } ?: ""

                                    val finalRwCount = countBundleEntries(finalRwJson)
                                    val updateAvailableForCard = finalRwCount > cardRwCount

                                    withContext(Dispatchers.Main) {
                                        cardInfoText = buildString {
                                            appendLine("Sync complete for bundle: $bundleId")
                                            appendLine("Card RW entries: $cardRwCount")
                                            appendLine("Union RW entries: $finalRwCount")
                                            appendLine()
                                            appendLine("Server states:")
                                            finalServerResults.forEach { result ->
                                                appendLine("- ${result.baseUrl}")
                                                appendLine("  RW entries: ${countBundleEntries(result.refreshedRwJson)}")
                                            }
                                        }.trim()

                                        payloadText = buildString {
                                            appendLine("--- Card NPS (E104) ---")
                                            appendLine(prettyPrintJson(cardNpsJson))
                                            appendLine()
                                            appendLine("--- Card EXTRA (E105) before sync ---")
                                            appendLine(if (cardExtraJson.isNotBlank()) prettyPrintJson(cardExtraJson) else "(empty)")
                                            appendLine()
                                            appendLine("--- Final RW union after multi-server sync ---")
                                            appendLine(if (finalRwJson.isNotBlank()) prettyPrintJson(finalRwJson) else "(empty)")
                                        }.trim()

                                        if (updateAvailableForCard) {
                                            pendingServerUpdateRwJson = finalRwJson
                                            pendingServerUpdateSummary =
                                                "Card is behind the reconciled server state. Offer write-back to card."
                                            payloadEditable = prettyPrintJson(finalRwJson)
                                            selectedTab = MainTab.EDIT_EXTRA
                                            succeedOperation(
                                                "Sync Card",
                                                "Servers reconciled. Card has older RW content and can now be updated."
                                            )
                                        } else {
                                            pendingServerUpdateRwJson = ""
                                            pendingServerUpdateSummary = ""
                                            selectedTab = MainTab.CARD_INFO
                                            succeedOperation(
                                                "Sync Card",
                                                "Card and all configured servers are aligned."
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        selectedTab = MainTab.LOG
                                        failOperation("Sync Card", "${e::class.simpleName}: ${e.message}")
                                    }
                                }
                            }
                        },
                        onReadNps = {
                            startOperation("Read NPS", "Waiting for card...")
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val parsed = service.readNps { msg ->
                                        scope.launch { log(msg) }
                                    }

                                    withContext(Dispatchers.Main) {
                                        payloadText = parsed.decompressedText
                                            ?.let(::prettyPrintJson)
                                            ?: "(Payload read, but not decompressed)"

                                        cardInfoText = buildString {
                                            appendLine("NPS MIME: ${parsed.mimeType}")
                                            appendLine("Compressed bytes: ${parsed.compressedPayload.size}")
                                        }.trim()

                                        selectedTab = MainTab.PAYLOAD
                                        succeedOperation("Read NPS", "Historic NPS payload read successfully.")
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        selectedTab = MainTab.LOG
                                        failOperation("Read NPS", "${e::class.simpleName}: ${e.message}")
                                    }
                                }
                            }
                        },
                        onReadExtra = {
                            startOperation("Read EXTRA", "Waiting for card...")
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val parsed = service.readExtra { msg ->
                                        scope.launch { log(msg) }
                                    }

                                    withContext(Dispatchers.Main) {
                                        val pretty = parsed.decompressedText
                                            ?.let(::prettyPrintJson)
                                            ?: "(Payload read, but not decompressed)"

                                        payloadText = pretty
                                        payloadEditable = pretty

                                        cardInfoText = buildString {
                                            appendLine("EXTRA MIME: ${parsed.mimeType}")
                                            appendLine("Compressed bytes: ${parsed.compressedPayload.size}")
                                        }.trim()

                                        selectedTab = MainTab.EDIT_EXTRA
                                        succeedOperation("Read EXTRA", "Operational payload read successfully.")
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        selectedTab = MainTab.LOG
                                        failOperation("Read EXTRA", "${e::class.simpleName}: ${e.message}")
                                    }
                                }
                            }
                        },
                        onWriteExtra = {
                            startOperation("Write EXTRA", "Waiting for card...")
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val parsed = service.writeExtraJson(
                                        jsonText = payloadEditable,
                                        onStatus = { msg -> scope.launch { log(msg) } }
                                    )

                                    withContext(Dispatchers.Main) {
                                        val pretty = parsed.decompressedText
                                            ?.let(::prettyPrintJson)
                                            ?: "(Payload written, but not decompressed on verify)"

                                        payloadText = pretty
                                        payloadEditable = pretty

                                        cardInfoText = buildString {
                                            appendLine("EXTRA MIME: ${parsed.mimeType}")
                                            appendLine("Compressed bytes: ${parsed.compressedPayload.size}")
                                            appendLine("Write/verify: OK")
                                        }.trim()

                                        selectedTab = MainTab.PAYLOAD
                                        succeedOperation("Write EXTRA", "Operational payload written and verified.")
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        selectedTab = MainTab.LOG
                                        failOperation("Write EXTRA", "${e::class.simpleName}: ${e.message}")
                                    }
                                }
                            }
                        },
                        onApplyApiUpdate = {
                            startOperation("Apply API Update", "Waiting for card...")
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val rwToWrite = when {
                                        pendingServerUpdateRwJson.isNotBlank() -> pendingServerUpdateRwJson
                                        pendingApiUpdateRwJson.isNotBlank() -> pendingApiUpdateRwJson
                                        else -> error("No pending update available. Run Inspect Card or Sync Card first.")
                                    }

                                    val bundleIdForDisplay = when {
                                        pendingApiUpdateBundleId.isNotBlank() -> pendingApiUpdateBundleId
                                        else -> "(unknown)"
                                    }

                                    val parsed = service.writeExtraJson(
                                        jsonText = rwToWrite,
                                        onStatus = { msg -> scope.launch { log(msg) } }
                                    )

                                    withContext(Dispatchers.Main) {
                                        val pretty = parsed.decompressedText
                                            ?.let(::prettyPrintJson)
                                            ?: "(Payload written, but not decompressed on verify)"

                                        payloadText = pretty
                                        payloadEditable = pretty

                                        cardInfoText = buildString {
                                            appendLine("API update applied to E105.")
                                            appendLine("Bundle ID: ${pendingApiUpdateBundleId.ifBlank { "(unknown)" }}")
                                            appendLine("EXTRA MIME: ${parsed.mimeType}")
                                            appendLine("Compressed bytes: ${parsed.compressedPayload.size}")
                                            appendLine("Write/verify: OK")
                                        }.trim()

                                        pendingApiUpdateRwJson = ""
                                        pendingApiUpdateBundleId = ""
                                        pendingApiUpdateSummary = ""

                                        pendingServerUpdateRwJson = ""
                                        pendingServerUpdateSummary = ""

                                        selectedTab = MainTab.PAYLOAD
                                        succeedOperation("Apply API Update", "Additional RW data written to card successfully.")
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        selectedTab = MainTab.LOG
                                        failOperation("Apply API Update", "${e::class.simpleName}: ${e.message}")
                                    }
                                }
                            }
                        }
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    CardAdminSection(
                        onRebuildCard = {
                            startOperation("Rebuild Card", "Waiting for card...")
                            scope.launch(Dispatchers.IO) {
                                try {
                                    require(fetchedRoJson.isNotBlank()) { "No fetched RO record available. Fetch an API record first." }
                                    require(fetchedRwJson.isNotBlank()) { "No fetched RW record available. Fetch an API record first." }

                                    val inspection = try {
                                        service.inspectCard { msg ->
                                            scope.launch { log(msg) }
                                        }
                                    } catch (e: Exception) {
                                        val msg = e.message ?: ""
                                        if (msg.contains("SW=6A82")) null else throw e
                                    }

                                    val isBlank = inspection == null ||
                                            inspection.state.kind == CardStateKind.BLANK_OR_UNFORMATTED

                                    val sameCardAwaitingConfirmation =
                                        inspection != null &&
                                                pendingRebuildConfirmation &&
                                                pendingRebuildUid != null &&
                                                pendingRebuildUid == inspection.uidHex

                                    if (!isBlank && !sameCardAwaitingConfirmation) {
                                        withContext(Dispatchers.Main) {
                                            pendingRebuildConfirmation = true
                                            pendingRebuildUid = inspection.uidHex
                                            pendingRebuildSummary = buildString {
                                                appendLine("UID: ${inspection.uidHex ?: "(unknown)"}")
                                                appendLine("State: ${inspection.state.label}")
                                                appendLine("Detail: ${inspection.state.detail}")
                                            }.trim()

                                            cardInfoText = buildString {
                                                appendLine("Rebuild confirmation required.")
                                                appendLine()
                                                appendLine(pendingRebuildSummary)
                                                appendLine()
                                                appendLine("Press 'Rebuild Card' again to overwrite this card.")
                                            }.trim()

                                            selectedTab = MainTab.CARD_INFO
                                            operationState = OperationState.WORKING
                                            operationTitle = "Rebuild Card"
                                            operationMessage =
                                                "Card is not blank. Press 'Rebuild Card' again to confirm overwrite."
                                            log("Rebuild Card: confirmation required for non-blank card.")
                                        }
                                        return@launch
                                    }

                                    val result = adminService.rebuildCard(
                                        roJson = fetchedRoJson,
                                        rwJson = fetchedRwJson,
                                        onStatus = { msg -> scope.launch { log(msg) } }
                                    )

                                    withContext(Dispatchers.Main) {
                                        cardInfoText = result.toDisplayText()
                                        payloadText = buildString {
                                            appendLine("--- RO candidate written to E104 ---")
                                            appendLine(prettyPrintJson(fetchedRoJson))
                                            appendLine()
                                            appendLine("--- RW candidate written to E105 ---")
                                            appendLine(prettyPrintJson(fetchedRwJson))
                                        }.trim()
                                        payloadEditable = prettyPrintJson(fetchedRwJson)

                                        clearRebuildConfirmation()

                                        selectedTab = MainTab.CARD_INFO
                                        succeedOperation("Rebuild Card", "Card rebuilt successfully.")
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        selectedTab = MainTab.LOG
                                        failOperation("Rebuild Card", "${e::class.simpleName}: ${e.message}")
                                    }
                                }
                            }
                        },
                        onCancelRebuild = {
                            clearRebuildConfirmation()
                            succeedOperation("Rebuild Card", "Rebuild confirmation cleared.")
                        },
                        onWipeCard = {
                            startOperation("Wipe Card", "Preparing destructive wipe...")
                            scope.launch(Dispatchers.IO) {
                                try {
                                    adminService.wipeCard { msg ->
                                        scope.launch { log(msg) }
                                    }

                                    withContext(Dispatchers.Main) {
                                        selectedTab = MainTab.LOG
                                        clearRebuildConfirmation()
                                        succeedOperation("Wipe Card", "Card wiped successfully.")
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        selectedTab = MainTab.LOG
                                        failOperation("Wipe Card", "${e::class.simpleName}: ${e.message}")
                                    }
                                }
                            }
                        }
                    )
                }
            }

            OperationBanner(
                state = operationState,
                title = operationTitle,
                message = operationMessage
            )

            AppTabRow(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            AppTabContent(
                selectedTab = selectedTab,
                cardInfoText = cardInfoText,
                payloadText = payloadText,
                payloadEditable = payloadEditable,
                onPayloadEditableChange = { payloadEditable = it },
                logLines = logLines
            )
        }
    }
}