package com.example.nps_nfc_desktop.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.nps_nfc_desktop.model.MainTab

@Composable
fun AppTabRow(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    TabRow(selectedTabIndex = selectedTab.ordinal) {
        MainTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = { Text(tab.title) }
            )
        }
    }
}

@Composable
fun ColumnScope.AppTabContent(
    selectedTab: MainTab,
    cardInfoText: String,
    payloadText: String,
    payloadEditable: String,
    onPayloadEditableChange: (String) -> Unit,
    logLines: List<String>
) {
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
                onValueChange = onPayloadEditableChange,
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