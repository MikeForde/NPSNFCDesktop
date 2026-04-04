package com.example.nps_nfc_desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nps_nfc_desktop.model.ObservationEntryType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddObservationDialog(
    selectedType: ObservationEntryType,
    onTypeChange: (ObservationEntryType) -> Unit,
    value1Text: String,
    onValue1Change: (String) -> Unit,
    value2Text: String,
    onValue2Change: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }

    val value1Label = if (selectedType.isBloodPressure) "Systolic" else selectedType.label
    val value2Label = if (selectedType.isBloodPressure) "Diastolic" else ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Observation") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = typeMenuExpanded,
                    onExpandedChange = { typeMenuExpanded = !typeMenuExpanded }
                ) {
                    TextField(
                        value = selectedType.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Observation Type") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        ObservationEntryType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.label) },
                                onClick = {
                                    onTypeChange(type)
                                    typeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = value1Text,
                    onValueChange = onValue1Change,
                    label = { Text(value1Label) },
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedType.isBloodPressure) {
                    OutlinedTextField(
                        value = value2Text,
                        onValueChange = onValue2Change,
                        label = { Text(value2Label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = if (selectedType.isBloodPressure) {
                        "Units: mm[Hg]"
                    } else {
                        "Units: ${selectedType.unit}"
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Add")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = Modifier.width(420.dp)
    )
}