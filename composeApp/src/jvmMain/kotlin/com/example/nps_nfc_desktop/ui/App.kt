package com.example.nps_nfc_desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nps_nfc_desktop.services.DesfireAdminService
import com.example.nps_nfc_desktop.services.DesfireNdefService
import com.example.nps_nfc_desktop.nfc.NfcReader
import com.example.nps_nfc_desktop.services.NpsApiService
import com.example.nps_nfc_desktop.actions.AppActions
import com.example.nps_nfc_desktop.model.AppState
import com.example.nps_nfc_desktop.model.ObservationEntryType
import com.example.nps_nfc_desktop.services.SimpleNdefReadService
import com.example.nps_nfc_desktop.services.LegacyService

@Composable
fun App() {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        val state = remember { AppState() }

        val nfcReader = remember { NfcReader() }
        val service = remember { DesfireNdefService(nfcReader) }
        val api = remember { NpsApiService() }
        val adminService = remember { DesfireAdminService(nfcReader) }
        val simpleNdefService = remember { SimpleNdefReadService() }
        val legacyService = remember { LegacyService(service) }

        val actions = remember(scope, state) {
            AppActions(
                scope = scope,
                state = state,
                nfcReader = nfcReader,
                service = service,
                api = api,
                adminService = adminService,
                legacyService = legacyService
            )
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

            TopConfigRow(
                baseUrl = state.baseUrl,
                baseUrlOptions = state.baseUrlOptions,
                baseUrlMenuExpanded = state.baseUrlMenuExpanded,
                onBaseUrlMenuExpandedChange = { state.baseUrlMenuExpanded = it },
                onBaseUrlSelected = {
                    state.baseUrl = it
                    state.baseUrlMenuExpanded = false
                },
                protectOptions = state.protectOptions,
                selectedProtectLabel = state.selectedProtectLabel,
                protectMenuExpanded = state.protectMenuExpanded,
                onProtectMenuExpandedChange = { state.protectMenuExpanded = it },
                onProtectSelected = {
                    state.selectedProtectLabel = it
                    state.protectMenuExpanded = false
                },
                onLoadApiList = { actions.loadApiList() }
            )

            RecordSelectionRow(
                availableRecords = state.availableRecords,
                selectedRecordLabel = state.selectedRecordLabel,
                recordMenuExpanded = state.recordMenuExpanded,
                onRecordMenuExpandedChange = { state.recordMenuExpanded = it },
                onRecordSelected = { item ->
                    state.selectedRecordId = item.packageUUID
                    state.selectedRecordLabel = item.displayLabel()
                    state.recordMenuExpanded = false
                },
                onFetchRecord = { actions.fetchRecord() }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(9f)) {
                    CardToolsSection(
                        onAddObservation = {
                            state.selectedObservationType = ObservationEntryType.BLOOD_PRESSURE
                            state.observationValue1Text = ""
                            state.observationValue2Text = ""
                            state.showAddObservationDialog = true
                        },
                        onInspectCard = {  actions.inspectCard()  },
                        onSyncCard = { actions.syncCard() },
                        onReadNps = { actions.readNps()  },
                        onReadExtra = { actions.readExtra() },
                        onWriteExtra = {  actions.writeExtra() },
                        onApplyApiUpdate = {  actions.applyApiUpdate()  }
                    )
                }

                Column(modifier = Modifier.weight(8f)) {
                    CardAdminSection(
                        onRebuildCard = {  actions.rebuildCard()  },
                        onRebuildLegacyCard = { actions.rebuildLegacyCard() },
                        onCancelRebuild = {
                            state.clearRebuildConfirmation()
                            state.succeedOperation("Rebuild Card", "Rebuild confirmation cleared.")
                        },
                        onWipeCard = { actions.wipeCard()  }
                    )
                }

                if (state.showAddObservationDialog) {
                    AddObservationDialog(
                        selectedType = state.selectedObservationType,
                        onTypeChange = { newType ->
                            state.selectedObservationType = newType
                            state.observationValue1Text = ""
                            state.observationValue2Text = ""
                        },
                        value1Text = state.observationValue1Text,
                        onValue1Change = { state.observationValue1Text = it },
                        value2Text = state.observationValue2Text,
                        onValue2Change = { state.observationValue2Text = it },
                        onDismiss = { state.showAddObservationDialog = false },
                        onConfirm = {
                            actions.addSelectedObservation()
                            state.showAddObservationDialog = false
                        }
                    )
                }
            }

            OperationBanner(
                state = state.operationState,
                title = state.operationTitle,
                message = state.operationMessage
            )

            AppTabRow(
                selectedTab = state.selectedTab,
                onTabSelected = { state.selectedTab = it }
            )

            AppTabContent(
                selectedTab = state.selectedTab,
                cardInfoText = state.cardInfoText,
                payloadText = state.payloadText,
                payloadRoText = state.payloadRoText,
                payloadEditable = state.payloadEditable,
                onPayloadEditableChange = { state.payloadEditable = it },
                logLines = state.logLines
            )
        }
    }
}
