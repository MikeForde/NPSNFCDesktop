package com.example.nps_nfc_desktop.actions

import com.example.nps_nfc_desktop.model.AppState
import com.example.nps_nfc_desktop.services.CardStateKind
import com.example.nps_nfc_desktop.services.DesfireAdminService
import com.example.nps_nfc_desktop.services.DesfireNdefService
import com.example.nps_nfc_desktop.model.MainTab
import com.example.nps_nfc_desktop.model.OperationState
import com.example.nps_nfc_desktop.util.prettyPrintJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CardAdminActions(
    scope: CoroutineScope,
    state: AppState,
    private val service: DesfireNdefService,
    private val adminService: DesfireAdminService
) : ActionSupport(scope, state) {

    fun rebuildCard() {
        state.startOperation("Rebuild Card", "Waiting for card...")
        scope.launch(Dispatchers.IO) {
            try {
                require(state.fetchedRoJson.isNotBlank()) { "No fetched RO record available. Fetch an API record first." }
                require(state.fetchedRwJson.isNotBlank()) { "No fetched RW record available. Fetch an API record first." }

                val inspection = try {
                    service.inspectCard { msg -> log(msg) }
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("SW=6A82")) null else throw e
                }

                val isBlank = inspection == null ||
                        inspection.state.kind == CardStateKind.BLANK_OR_UNFORMATTED

                val sameCardAwaitingConfirmation =
                    inspection != null &&
                            state.pendingRebuildConfirmation &&
                            state.pendingRebuildUid != null &&
                            state.pendingRebuildUid == inspection.uidHex

                if (!isBlank && !sameCardAwaitingConfirmation) {
                    withContext(Dispatchers.Main) {
                        state.pendingRebuildConfirmation = true
                        state.pendingRebuildUid = inspection?.uidHex
                        state.pendingRebuildSummary = buildString {
                            appendLine("UID: ${inspection?.uidHex ?: "(unknown)"}")
                            appendLine("State: ${inspection?.state?.label ?: "(unknown)"}")
                            appendLine("Detail: ${inspection?.state?.detail ?: "(none)"}")
                        }.trim()

                        state.cardInfoText = buildString {
                            appendLine("Rebuild confirmation required.")
                            appendLine()
                            appendLine(state.pendingRebuildSummary)
                            appendLine()
                            appendLine("Press 'Rebuild Card' again to overwrite this card.")
                        }.trim()

                        state.selectedTab = MainTab.CARD_INFO
                        state.operationState = OperationState.WORKING
                        state.operationTitle = "Rebuild Card"
                        state.operationMessage =
                            "Card is not blank. Press 'Rebuild Card' again to confirm overwrite."
                        state.log("Rebuild Card: confirmation required for non-blank card.")
                    }
                    return@launch
                }

                val result = adminService.rebuildCard(
                    roJson = state.fetchedRoJson,
                    rwJson = state.fetchedRwJson,
                    onStatus = { msg -> log(msg) }
                )

                withContext(Dispatchers.Main) {
                    state.cardInfoText = result.toDisplayText()
                    state.payloadText = buildString {
                        appendLine("--- RO candidate written to E104 ---")
                        appendLine(prettyPrintJson(state.fetchedRoJson))
                        appendLine()
                        appendLine("--- RW candidate written to E105 ---")
                        appendLine(prettyPrintJson(state.fetchedRwJson))
                    }.trim()
                    state.payloadEditable = prettyPrintJson(state.fetchedRwJson)

                    state.clearRebuildConfirmation()

                    state.selectedTab = MainTab.CARD_INFO
                    state.succeedOperation("Rebuild Card", "Card rebuilt successfully.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state.selectedTab = MainTab.LOG
                    state.failOperation("Rebuild Card", "${e::class.simpleName}: ${e.message}")
                }
            }
        }
    }

    fun cancelRebuild() {
        state.clearRebuildConfirmation()
        state.succeedOperation("Rebuild Card", "Rebuild confirmation cleared.")
    }

    fun wipeCard() {
        state.startOperation("Wipe Card", "Preparing destructive wipe...")
        scope.launch(Dispatchers.IO) {
            try {
                adminService.wipeCard { msg -> log(msg) }

                withContext(Dispatchers.Main) {
                    state.selectedTab = MainTab.LOG
                    state.clearRebuildConfirmation()
                    state.succeedOperation("Wipe Card", "Card wiped successfully.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state.selectedTab = MainTab.LOG
                    state.failOperation("Wipe Card", "${e::class.simpleName}: ${e.message}")
                }
            }
        }
    }
}