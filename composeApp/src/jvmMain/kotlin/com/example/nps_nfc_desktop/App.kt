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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MainTab(val title: String) {
    CARD_INFO("Card Info"),
    PAYLOAD("Payload"),
    EDIT_EXTRA("Edit EXTRA"),
    LOG("Log")
}

private enum class OperationState {
    IDLE,
    WORKING,
    SUCCESS,
    ERROR
}

@Composable
fun App() {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        val nfcReader = remember { NfcReader() }
        val service = remember { DesfireNdefService(nfcReader) }

        var cardInfoText by remember { mutableStateOf("(none)") }
        var payloadText by remember { mutableStateOf("(none)") }
        var payloadEditable by remember { mutableStateOf("") }
        var selectedTab by remember { mutableStateOf(MainTab.CARD_INFO) }

        var operationState by remember { mutableStateOf(OperationState.IDLE) }
        var operationTitle by remember { mutableStateOf("Ready") }
        var operationMessage by remember { mutableStateOf("Tap an action to begin.") }

        val logLines = remember { mutableStateListOf("Idle") }

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
                text = "NATO Patient Summary NFC Desktop POC",
                style = MaterialTheme.typography.headlineSmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    succeedOperation(
                                        "Inspect Card",
                                        "Card read successfully."
                                    )
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    selectedTab = MainTab.LOG
                                    failOperation(
                                        "Inspect Card",
                                        "${e::class.simpleName}: ${e.message}"
                                    )
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
                                    succeedOperation(
                                        "Read NPS",
                                        "Historic NPS payload read successfully."
                                    )
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    selectedTab = MainTab.LOG
                                    failOperation(
                                        "Read NPS",
                                        "${e::class.simpleName}: ${e.message}"
                                    )
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
                                    succeedOperation(
                                        "Read EXTRA",
                                        "Operational payload read successfully."
                                    )
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    selectedTab = MainTab.LOG
                                    failOperation(
                                        "Read EXTRA",
                                        "${e::class.simpleName}: ${e.message}"
                                    )
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
                                    succeedOperation(
                                        "Write EXTRA",
                                        "Operational payload written and verified."
                                    )
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    selectedTab = MainTab.LOG
                                    failOperation(
                                        "Write EXTRA",
                                        "${e::class.simpleName}: ${e.message}"
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Text("Write EXTRA")
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