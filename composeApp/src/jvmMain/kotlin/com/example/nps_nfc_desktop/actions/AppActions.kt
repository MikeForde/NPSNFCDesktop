package com.example.nps_nfc_desktop.actions

import com.example.nps_nfc_desktop.model.AppState
import com.example.nps_nfc_desktop.services.DesfireAdminService
import com.example.nps_nfc_desktop.services.DesfireNdefService
import com.example.nps_nfc_desktop.nfc.NfcReader
import com.example.nps_nfc_desktop.services.NpsApiService
import com.example.nps_nfc_desktop.services.LegacyService
import kotlinx.coroutines.CoroutineScope

class AppActions(
    private val scope: CoroutineScope,
    private val state: AppState,
    nfcReader: NfcReader,
    service: DesfireNdefService,
    api: NpsApiService,
    adminService: DesfireAdminService,
    legacyService: LegacyService
) {
    private val apiActions = ApiActions(
        scope = scope,
        state = state,
        api = api
    )

    private val observationEntryActions = ObservationEntryActions(
        scope = scope,
        state = state
    )

    private val syncActions = SyncActions(
        scope = scope,
        state = state,
        nfcReader = nfcReader,
        service = service,
        api = api
    )

    private val cardDataActions = CardDataActions(
        scope = scope,
        state = state,
        service = service,
        api = api,
        legacyService = legacyService
    )

    private val cardAdminActions = CardAdminActions(
        scope = scope,
        state = state,
        service = service,
        adminService = adminService
    )

    fun loadApiList() = apiActions.loadApiList()
    fun fetchRecord() = apiActions.fetchRecord()

    fun addSelectedObservation() = observationEntryActions.addObservation(
        type = state.selectedObservationType,
        primaryValueText = state.observationValue1Text,
        secondaryValueText = state.observationValue2Text.takeIf { it.isNotBlank() }
    )

    fun inspectCard() = cardDataActions.inspectCard()
    fun syncCard() = syncActions.syncCard()
    fun readNps() = cardDataActions.readNps()
    fun readExtra() = cardDataActions.readExtra()
    fun writeExtra() = cardDataActions.writeExtra()
    fun applyApiUpdate() = cardDataActions.applyApiUpdate()

    fun rebuildCard() = cardAdminActions.rebuildCard()
    fun cancelRebuild() = cardAdminActions.cancelRebuild()
    fun wipeCard() = cardAdminActions.wipeCard()
}
