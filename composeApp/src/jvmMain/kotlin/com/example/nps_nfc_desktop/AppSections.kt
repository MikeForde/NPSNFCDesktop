package com.example.nps_nfc_desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopConfigRow(
    baseUrl: String,
    baseUrlOptions: List<String>,
    baseUrlMenuExpanded: Boolean,
    onBaseUrlMenuExpandedChange: (Boolean) -> Unit,
    onBaseUrlSelected: (String) -> Unit,
    protectOptions: List<String>,
    selectedProtectLabel: String,
    protectMenuExpanded: Boolean,
    onProtectMenuExpandedChange: (Boolean) -> Unit,
    onProtectSelected: (String) -> Unit,
    onLoadApiList: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExposedDropdownMenuBox(
            expanded = baseUrlMenuExpanded,
            onExpandedChange = { onBaseUrlMenuExpandedChange(!baseUrlMenuExpanded) },
            modifier = Modifier.weight(1.4f)
        ) {
            TextField(
                value = baseUrl,
                onValueChange = {},
                readOnly = true,
                label = { Text("API Source") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = baseUrlMenuExpanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = baseUrlMenuExpanded,
                onDismissRequest = { onBaseUrlMenuExpandedChange(false) }
            ) {
                baseUrlOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { onBaseUrlSelected(option) }
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = protectMenuExpanded,
            onExpandedChange = { onProtectMenuExpandedChange(!protectMenuExpanded) },
            modifier = Modifier.weight(0.5f)
        ) {
            TextField(
                value = selectedProtectLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Protect") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = protectMenuExpanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = protectMenuExpanded,
                onDismissRequest = { onProtectMenuExpandedChange(false) }
            ) {
                protectOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { onProtectSelected(option) }
                    )
                }
            }
        }

        Button(onClick = onLoadApiList) {
            Text("Load API List")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordSelectionRow(
    availableRecords: List<IpsListItem>,
    selectedRecordLabel: String,
    recordMenuExpanded: Boolean,
    onRecordMenuExpandedChange: (Boolean) -> Unit,
    onRecordSelected: (IpsListItem) -> Unit,
    onFetchRecord: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExposedDropdownMenuBox(
            expanded = recordMenuExpanded,
            onExpandedChange = { onRecordMenuExpandedChange(!recordMenuExpanded) },
            modifier = Modifier.weight(1f)
        ) {
            TextField(
                value = selectedRecordLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Available Records") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = recordMenuExpanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = recordMenuExpanded,
                onDismissRequest = { onRecordMenuExpandedChange(false) }
            ) {
                availableRecords.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.displayLabel()) },
                        onClick = { onRecordSelected(item) }
                    )
                }
            }
        }

        Button(onClick = onFetchRecord) {
            Text("Fetch Record")
        }
    }
}

@Composable
fun CardToolsSection(
    systolicText: String,
    onSystolicChange: (String) -> Unit,
    diastolicText: String,
    onDiastolicChange: (String) -> Unit,
    onAddBloodPressure: () -> Unit,
    onInspectCard: () -> Unit,
    onSyncCard: () -> Unit,
    onReadNps: () -> Unit,
    onReadExtra: () -> Unit,
    onWriteExtra: () -> Unit,
    onApplyApiUpdate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Card Tools", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = systolicText,
                    onValueChange = onSystolicChange,
                    label = { Text("Sys") },
                    modifier = Modifier.weight(1f)
                )

                TextField(
                    value = diastolicText,
                    onValueChange = onDiastolicChange,
                    label = { Text("Dia") },
                    modifier = Modifier.weight(1f)
                )

                Button(onClick = onAddBloodPressure) {
                    Text("Add BP")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onInspectCard) { Text("Inspect") }
                Button(onClick = onSyncCard) { Text("Sync") }
                Button(onClick = onReadNps) { Text("Read NPS") }
                Button(onClick = onReadExtra) { Text("Read EXTRA") }
                Button(onClick = onWriteExtra) { Text("Write EXTRA") }
                Button(onClick = onApplyApiUpdate) { Text("Update Card") }
            }
        }
    }
}

@Composable
fun CardAdminSection(
    onRebuildCard: () -> Unit,
    onCancelRebuild: () -> Unit,
    onWipeCard: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Card Admin", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onRebuildCard) { Text("Rebuild Card") }
                Button(onClick = onCancelRebuild) { Text("Cancel Rebuild") }
                Button(onClick = onWipeCard) { Text("Wipe Card") }
            }
        }
    }
}