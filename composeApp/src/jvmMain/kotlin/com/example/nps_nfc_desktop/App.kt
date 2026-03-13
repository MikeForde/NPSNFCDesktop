package com.example.nps_nfc_desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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

        var cardInfoText by remember { mutableStateOf("(none)") }
        var payloadText by remember { mutableStateOf("(none)") }
        val statusLines = remember { mutableStateListOf("Idle") }

        fun log(msg: String) {
            statusLines.add(msg)
            while (statusLines.size > 80) {
                statusLines.removeAt(0)
            }
        }

        fun clearLog() {
            statusLines.clear()
            statusLines.add("Idle")
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
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val readers = nfcReader.listReadersWithDiagnostics { msg ->
                                    scope.launch { log(msg) }
                                }
                                withContext(Dispatchers.Main) {
                                    if (readers.isEmpty()) {
                                        log("No readers returned by smartcardio.")
                                    } else {
                                        log("Readers: " + readers.joinToString { it.name })
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    log("List readers error: ${e::class.simpleName}: ${e.message}")
                                }
                            }
                        }
                    }
                ) {
                    Text("List Readers")
                }

                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val inspection = service.inspectCardNative { msg ->
                                    scope.launch { log(msg) }
                                }
                                withContext(Dispatchers.Main) {
                                    cardInfoText = inspection.toDisplayText()
                                    payloadText = buildString {
                                        appendLine("--- NPS (E104) ---")
                                        appendLine(inspection.nps?.decompressedText ?: "(not available)")
                                        appendLine()
                                        appendLine("--- EXTRA (E105) ---")
                                        appendLine(inspection.extra?.decompressedText ?: "(not available)")
                                    }.trim()
                                    log("Native inspection complete.")
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    log("Native inspect error: ${e::class.simpleName}: ${e.message}")
                                }
                            }
                        }
                    }
                ) {
                    Text("Inspect Native")
                }

                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val inspection = service.inspectCard { msg ->
                                    scope.launch { log(msg) }
                                }
                                withContext(Dispatchers.Main) {
                                    cardInfoText = inspection.toDisplayText()
                                    payloadText = buildString {
                                        appendLine("--- NPS (E104) ---")
                                        appendLine(inspection.nps?.decompressedText ?: "(not available)")
                                        appendLine()
                                        appendLine("--- EXTRA (E105) ---")
                                        appendLine(inspection.extra?.decompressedText ?: "(not available)")
                                    }.trim()
                                    log("Inspection complete.")
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    log("Inspect error: ${e::class.simpleName}: ${e.message}")
                                }
                            }
                        }
                    }
                ) {
                    Text("Inspect Card")
                }

                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val parsed = service.readNps { msg ->
                                    scope.launch { log(msg) }
                                }
                                withContext(Dispatchers.Main) {
                                    payloadText = parsed.decompressedText
                                        ?: "(Payload read, but not decompressed)"
                                    cardInfoText = "NPS MIME: ${parsed.mimeType}\nCompressed bytes: ${parsed.compressedPayload.size}"
                                    log("NPS read complete.")
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    log("Read NPS error: ${e::class.simpleName}: ${e.message}")
                                }
                            }
                        }
                    }
                ) {
                    Text("Read NPS")
                }

                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val parsed = service.readExtra { msg ->
                                    scope.launch { log(msg) }
                                }
                                withContext(Dispatchers.Main) {
                                    payloadText = parsed.decompressedText
                                        ?: "(Payload read, but not decompressed)"
                                    cardInfoText = "EXTRA MIME: ${parsed.mimeType}\nCompressed bytes: ${parsed.compressedPayload.size}"
                                    log("EXTRA read complete.")
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    log("Read EXTRA error: ${e::class.simpleName}: ${e.message}")
                                }
                            }
                        }
                    }
                ) {
                    Text("Read EXTRA")
                }

                OutlinedButton(
                    onClick = { clearLog() }
                ) {
                    Text("Clear Log")
                }
            }

            Text("Card Info", style = MaterialTheme.typography.titleMedium)
            TextField(
                value = cardInfoText,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .verticalScroll(rememberScrollState())
            )

            Text("Payload", style = MaterialTheme.typography.titleMedium)
            TextField(
                value = payloadText,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .verticalScroll(rememberScrollState())
            )

            Text("Status", style = MaterialTheme.typography.titleMedium)
            TextField(
                value = statusLines.joinToString("\n"),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}