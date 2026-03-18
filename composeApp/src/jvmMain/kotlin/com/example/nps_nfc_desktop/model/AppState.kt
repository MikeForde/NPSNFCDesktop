package com.example.nps_nfc_desktop.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.nps_nfc_desktop.services.IpsListItem
import com.example.nps_nfc_desktop.model.ObservationEntryType

class AppState {
    var cardInfoText by mutableStateOf("(none)")
    var payloadText by mutableStateOf("(none)")
    var payloadEditable by mutableStateOf("")
    var selectedTab by mutableStateOf(MainTab.CARD_INFO)

    var operationState by mutableStateOf(OperationState.IDLE)
    var operationTitle by mutableStateOf("Ready")
    var operationMessage by mutableStateOf("Tap an action to begin.")

    var baseUrl by mutableStateOf("https://ipsmern-dep.azurewebsites.net")
    val protectOptions = listOf(
        "0 - none",
        "1 - field-level encryption (JWE)",
        "2 - omit identifiers"
    )
    var selectedProtectLabel by mutableStateOf(protectOptions.first())
    var protectMenuExpanded by mutableStateOf(false)

    var availableRecords by mutableStateOf<List<IpsListItem>>(emptyList())
    var selectedRecordId by mutableStateOf("")
    var selectedRecordLabel by mutableStateOf("")
    var recordMenuExpanded by mutableStateOf(false)

    var fetchedRoJson by mutableStateOf("")
    var fetchedRwJson by mutableStateOf("")

    var pendingRebuildConfirmation by mutableStateOf(false)
    var pendingRebuildUid by mutableStateOf<String?>(null)
    var pendingRebuildSummary by mutableStateOf("")

    var pendingApiUpdateRwJson by mutableStateOf("")
    var pendingApiUpdateSummary by mutableStateOf("")
    var pendingApiUpdateBundleId by mutableStateOf("")

    var pendingServerUpdateRwJson by mutableStateOf("")
    var pendingServerUpdateSummary by mutableStateOf("")

    val logLines = mutableStateListOf("Idle")

    var showAddObservationDialog by mutableStateOf(false)

    var selectedObservationType by mutableStateOf(ObservationEntryType.BLOOD_PRESSURE)
    var observationValue1Text by mutableStateOf("")
    var observationValue2Text by mutableStateOf("")

    val baseUrlOptions = listOf(
        "https://ipsmern-dep.azurewebsites.net",
        "http://localhost:5049"
    )

    var baseUrlMenuExpanded by mutableStateOf(false)

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

    fun clearRebuildConfirmation() {
        pendingRebuildConfirmation = false
        pendingRebuildUid = null
        pendingRebuildSummary = ""
    }
}