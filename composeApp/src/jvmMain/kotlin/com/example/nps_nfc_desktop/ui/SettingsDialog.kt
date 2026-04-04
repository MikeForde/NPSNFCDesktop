package com.example.nps_nfc_desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import com.example.nps_nfc_desktop.model.AppState
import com.example.nps_nfc_desktop.model.ServerEntry

@Composable
fun SettingsDialog(
    appState: AppState,
    onClose: () -> Unit
) {
    var editableServers by remember { mutableStateOf(appState.serverEntries.toList()) }
    var newUrlText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    fun refreshFromState() {
        editableServers = appState.serverEntries.toList()
        newUrlText = ""
        message = ""
    }


    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .width(860.dp)
                .height(700.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Manage server URLs",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "You can enable, disable, edit, delete, add, or reset the available servers here.",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = "Current servers",
                style = MaterialTheme.typography.labelLarge
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Enabled",
                            modifier = Modifier.width(90.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            "URL",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.width(90.dp))
                    }

                    Divider()

                    if (editableServers.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No servers configured.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(editableServers) { index, server ->
                                val isActive = server.url == appState.baseUrl && server.enabled

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.width(90.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Checkbox(
                                            checked = server.enabled,
                                            onCheckedChange = { checked ->
                                                editableServers =
                                                    editableServers.toMutableList().also {
                                                        it[index] = server.copy(enabled = checked)
                                                    }
                                                message = ""
                                            }
                                        )
                                    }

                                    OutlinedTextField(
                                        value = server.url,
                                        onValueChange = { updated ->
                                            editableServers = editableServers.toMutableList().also {
                                                it[index] = server.copy(url = updated)
                                            }
                                            message = ""
                                        },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        label = {
                                            Text(
                                                if (isActive) "Active server" else "Server ${index + 1}"
                                            )
                                        }
                                    )

                                    Button(
                                        enabled = editableServers.size > 1,
                                        onClick = {
                                            editableServers = editableServers.toMutableList().also {
                                                it.removeAt(index)
                                            }
                                            message = ""
                                        }
                                    ) {
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Divider()

            Text(
                text = "Add new server",
                style = MaterialTheme.typography.labelLarge
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newUrlText,
                    onValueChange = {
                        newUrlText = it
                        message = ""
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("New server URL") }
                )

                Button(
                    onClick = {
                        val candidate = newUrlText.trim()
                        if (candidate.isNotBlank()) {
                            editableServers = editableServers + ServerEntry(
                                url = candidate,
                                enabled = true
                            )
                            newUrlText = ""
                            message = ""
                        }
                    }
                ) {
                    Text("Add")
                }
            }

            if (message.isNotBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        appState.resetServerEntries()
                        editableServers = appState.serverEntries.toList()
                        newUrlText = ""
                        message = "Reset to original server list."
                    }
                ) {
                    Text("Reset defaults")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { refreshFromState() }
                    ) {
                        Text("Revert")
                    }

                    Button(
                        onClick = {
                            val ok = appState.replaceServerEntries(editableServers)
                            message = if (ok) {
                                editableServers = appState.serverEntries.toList()
                                "Changes saved."
                            } else {
                                "Could not save changes. Ensure URLs are valid and at least one server is enabled."
                            }
                        }
                    ) {
                        Text("Save changes")
                    }

                    TextButton(onClick = onClose) {
                        Text("Close")
                    }
                }
            }
        }
    }

}