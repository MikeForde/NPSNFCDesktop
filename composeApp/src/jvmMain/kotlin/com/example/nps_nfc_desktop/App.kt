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
                                val inspection = service.inspectCard { msg ->
                                    scope.launch { log(msg) }
                                }

                                withContext(Dispatchers.Main) {
                                    cardInfoText = inspection.toDisplayText()
                                    payloadText = buildString {
                                        appendLine("--- NPS (E104) ---")
                                        appendLine(
                                            inspection.nps?.decompressedText?.let(::prettyPrintJson)
                                                ?: "(not available)"
                                        )
                                        appendLine()
                                        appendLine("--- EXTRA (E105) ---")
                                        appendLine(
                                            inspection.extra?.decompressedText?.let(::prettyPrintJson)
                                                ?: "(not available)"
                                        )
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
                                        ?.let(::prettyPrintJson)
                                        ?: "(Payload read, but not decompressed)"

                                    cardInfoText = buildString {
                                        appendLine("NPS MIME: ${parsed.mimeType}")
                                        appendLine("Compressed bytes: ${parsed.compressedPayload.size}")
                                    }.trim()

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
                                        ?.let(::prettyPrintJson)
                                        ?: "(Payload read, but not decompressed)"

                                    cardInfoText = buildString {
                                        appendLine("EXTRA MIME: ${parsed.mimeType}")
                                        appendLine("Compressed bytes: ${parsed.compressedPayload.size}")
                                    }.trim()

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
                    .height(320.dp)
                    .verticalScroll(rememberScrollState())
            )

            Text("Status", style = MaterialTheme.typography.titleMedium)
            TextField(
                value = statusLines.joinToString("\n"),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .verticalScroll(rememberScrollState())
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
                // skip original whitespace outside strings
            }

            else -> {
                out.append(ch)
            }
        }
    }

    return out.toString()
}