package com.example.nps_nfc_desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
        val reader = remember { NfcReader() }

        var running by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("Idle") }
        var lastUid by remember { mutableStateOf("") }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text("NPS-NFC-Desktop", style = MaterialTheme.typography.headlineSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !running,
                    onClick = {
                        running = true
                        status = "Starting..."
                        scope.launch(Dispatchers.IO) {
                            reader.runUidLoop(
                                onStatus = { msg ->
                                    scope.launch { status = msg }
                                },
                                onUid = { uid ->
                                    scope.launch { lastUid = uid }
                                }
                            )
                            withContext(Dispatchers.Main) { running = false }
                        }
                    }
                ) { Text("Start NFC") }

                OutlinedButton(
                    enabled = running,
                    onClick = { reader.stop() }
                ) { Text("Stop") }
            }

            Text("Status: $status")
            Text("Last UID: ${if (lastUid.isBlank()) "(none)" else lastUid}")
        }
    }
}
