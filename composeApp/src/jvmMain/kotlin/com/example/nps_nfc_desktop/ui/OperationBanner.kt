package com.example.nps_nfc_desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nps_nfc_desktop.model.OperationState

@Composable
fun OperationBanner(
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