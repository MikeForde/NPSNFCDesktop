package com.example.nps_nfc_desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MainTab(val title: String) {
    CARD_INFO("Card Info"),
    PAYLOAD("NPS - RO"),
    EDIT_EXTRA("Edit EXTRA - RW"),
    LOG("Log")
}

private enum class OperationState {
    IDLE,
    WORKING,
    SUCCESS,
    ERROR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        val nfcReader = remember { NfcReader() }
        val service = remember { DesfireNdefService(nfcReader) }
        val api = remember { NpsApiService() }

        var cardInfoText by remember { mutableStateOf("(none)") }
        var payloadText by remember { mutableStateOf("(none)") }
        var payloadEditable by remember { mutableStateOf("") }
        var selectedTab by remember { mutableStateOf(MainTab.CARD_INFO) }

        var operationState by remember { mutableStateOf(OperationState.IDLE) }
        var operationTitle by remember { mutableStateOf("Ready") }
        var operationMessage by remember { mutableStateOf("Tap an action to begin.") }

        var baseUrl by remember { mutableStateOf("https://ipsmern-dep.azurewebsites.net") }
        var protectText by remember { mutableStateOf("0") }
        var availableRecords by remember { mutableStateOf<List<IpsListItem>>(emptyList()) }
        var selectedRecordId by remember { mutableStateOf("") }
        var selectedRecordLabel by remember { mutableStateOf("") }
        var recordMenuExpanded by remember { mutableStateOf(false) }

        val logLines = remember { mutableStateListOf("Idle") }

        var fetchedRoJson by remember { mutableStateOf("") }
        var fetchedRwJson by remember { mutableStateOf("") }

        val adminService = remember { DesfireAdminService(nfcReader) }

        var pendingRebuildConfirmation by remember { mutableStateOf(false) }
        var pendingRebuildUid by remember { mutableStateOf<String?>(null) }
        var pendingRebuildSummary by remember { mutableStateOf("") }

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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.weight(1.4f)
                )

                TextField(
                    value = protectText,
                    onValueChange = { protectText = it },
                    label = { Text("Protect") },
                    modifier = Modifier.weight(0.4f)
                )
            }

            ExposedDropdownMenuBox(
                expanded = recordMenuExpanded,
                onExpandedChange = { recordMenuExpanded = !recordMenuExpanded }
            ) {
                TextField(
                    value = selectedRecordLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Available records") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = recordMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = recordMenuExpanded,
                    onDismissRequest = { recordMenuExpanded = false }
                ) {
                    availableRecords.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.displayLabel()) },
                            onClick = {
                                selectedRecordId = item.packageUUID
                                selectedRecordLabel = item.displayLabel()
                                recordMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
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
                ) {
                    Text("Load API List")
                }

                Button(
                    onClick = {
                        startOperation("Fetch API Record", "Fetching selected record...")
                        scope.launch(Dispatchers.IO) {
                            try {
                                require(selectedRecordId.isNotBlank()) { "No record selected" }
                                val protect = protectText.toIntOrNull() ?: 0
                                val loaded = api.fetchRecord(baseUrl, selectedRecordId, protect)

                                withContext(Dispatchers.Main) {
                                    val prettyRo = prettyPrintJson(loaded.roJson)
                                    val prettyRw = prettyPrintJson(loaded.rwJson)

                                    fetchedRoJson = loaded.roJson
                                    fetchedRwJson = loaded.rwJson

                                    cardInfoText = buildString {
                                        appendLine("Source package: ${loaded.meta.id}")
                                        appendLine("Cutoff: ${loaded.meta.cutoff}")
                                        appendLine("Protect: ${loaded.meta.protect}")
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
                                    succeedOperation("Fetch API Record", "Record fetched and decoded.")
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    selectedTab = MainTab.LOG
                                    failOperation("Fetch API Record", "${e::class.simpleName}: ${e.message}")
                                }
                            }
                        }
                    }
                ) {
                    Text("Fetch API Record")
                }

                Button(
                    onClick = {
                        startOperation("Inspect Card", "Waiting for card...")
                        scope.launch(Dispatchers.IO) {
                            try {
                                val inspection = service.inspectCard { msg ->
                                    scope.launch { log(msg) }
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
                                    val stateMessage = when (inspection.state.kind) {
                                        CardStateKind.NATO_FORMATTED ->
                                            "Existing NATO card detected. Overwrite allowed for demo."
                                        CardStateKind.BLANK_OR_UNFORMATTED ->
                                            "Blank or unformatted card detected."
                                        CardStateKind.PARTIAL_OR_UNEXPECTED ->
                                            "Card has partial/unexpected structure. Overwrite allowed for demo."
                                        CardStateKind.ERROR ->
                                            "Card read completed, but classification is uncertain."
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
                    }
                ) {
                    Text("Inspect Card")
                }

                Button(
                    onClick = {
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
                    }
                ) {
                    Text("Read NPS")
                }

                Button(
                    onClick = {
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
                    }
                ) {
                    Text("Read EXTRA")
                }

                Button(
                    onClick = {
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
                    }
                ) {
                    Text("Write EXTRA")
                }

                Button(
                    onClick = {
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
                                    if (msg.contains("SW=6A82")) {
                                        null
                                    } else {
                                        throw e
                                    }
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
                                        pendingRebuildUid = inspection?.uidHex
                                        pendingRebuildSummary = buildString {
                                            appendLine("UID: ${inspection?.uidHex ?: "(unknown)"}")
                                            appendLine("State: ${inspection?.state?.label ?: "(unknown)"}")
                                            appendLine("Detail: ${inspection?.state?.detail ?: "(unknown)"}")
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
                                        operationMessage = "Card is not blank. Press 'Rebuild Card' again to confirm overwrite."
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

                                    pendingRebuildConfirmation = false
                                    pendingRebuildUid = null
                                    pendingRebuildSummary = ""

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
                    }
                ) {
                    Text("Rebuild Card")
                }

                Button(
                    onClick = {
                        pendingRebuildConfirmation = false
                        pendingRebuildUid = null
                        pendingRebuildSummary = ""
                        succeedOperation("Rebuild Card", "Overwrite confirmation cleared.")
                    }
                ) {
                    Text("Cancel Overwrite")
                }

                Button(
                    onClick = {
                        startOperation("Wipe Card", "Preparing destructive wipe...")
                        scope.launch(Dispatchers.IO) {
                            try {
                                adminService.wipeCard { msg ->
                                    scope.launch { log(msg) }
                                }

                                withContext(Dispatchers.Main) {
                                    selectedTab = MainTab.LOG
                                    succeedOperation("Wipe Card", "Card wiped successfully.")
                                }
                                pendingRebuildConfirmation = false
                                pendingRebuildUid = null
                                pendingRebuildSummary = ""
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    selectedTab = MainTab.LOG
                                    failOperation("Wipe Card", "${e::class.simpleName}: ${e.message}")
                                }
                            }
                        }
                    }
                ) {
                    Text("Wipe Card")
                }

                Button(
                    onClick = {
                        pendingRebuildConfirmation = false
                        pendingRebuildUid = null
                        pendingRebuildSummary = ""
                        succeedOperation("Rebuild Card", "Overwrite confirmation cleared.")
                    }
                ) {
                    Text("Cancel Overwrite")
                }
            }

            OperationBanner(
                state = operationState,
                title = operationTitle,
                message = operationMessage
            )

            TabRow(selectedTabIndex = selectedTab.ordinal) {
                MainTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) }
                    )
                }
            }

            when (selectedTab) {
                MainTab.CARD_INFO -> {
                    TextField(
                        value = cardInfoText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    )
                }

                MainTab.PAYLOAD -> {
                    TextField(
                        value = payloadText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    )
                }

                MainTab.EDIT_EXTRA -> {
                    TextField(
                        value = payloadEditable,
                        onValueChange = { payloadEditable = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    )
                }

                MainTab.LOG -> {
                    TextField(
                        value = logLines.joinToString("\n"),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationBanner(
    state: OperationState,
    title: String,
    message: String
) {
    val colorScheme = MaterialTheme.colorScheme

    val containerColor = when (state) {
        OperationState.IDLE -> colorScheme.surfaceVariant
        OperationState.WORKING -> colorScheme.secondaryContainer
        OperationState.SUCCESS -> colorScheme.primaryContainer
        OperationState.ERROR -> colorScheme.errorContainer
    }

    val contentColor = when (state) {
        OperationState.IDLE -> colorScheme.onSurfaceVariant
        OperationState.WORKING -> colorScheme.onSecondaryContainer
        OperationState.SUCCESS -> colorScheme.onPrimaryContainer
        OperationState.ERROR -> colorScheme.onErrorContainer
    }

    val stateLabel = when (state) {
        OperationState.IDLE -> "Idle"
        OperationState.WORKING -> "Working"
        OperationState.SUCCESS -> "Success"
        OperationState.ERROR -> "Error"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$stateLabel — $title",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun prettyPrintJson(input: String): String {
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