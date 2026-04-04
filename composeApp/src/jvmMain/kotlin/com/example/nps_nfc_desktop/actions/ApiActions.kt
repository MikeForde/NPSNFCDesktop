package com.example.nps_nfc_desktop.actions

import com.example.nps_nfc_desktop.model.AppState
import com.example.nps_nfc_desktop.model.MainTab
import com.example.nps_nfc_desktop.services.NpsApiService
import com.example.nps_nfc_desktop.util.prettyPrintJson
import com.example.nps_nfc_desktop.util.protectLabelToValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class ApiActions(
    scope: CoroutineScope,
    state: AppState,
    private val api: NpsApiService
) : ActionSupport(scope, state) {

    fun loadApiList() {
        state.startOperation("Load API List", "Loading available records...")
        scope.launch(Dispatchers.IO) {
            try {
                val list = api.listRecords(state.baseUrl)
                withContext(Dispatchers.Main) {
                    state.availableRecords = list
                    if (list.isNotEmpty()) {
                        state.selectedRecordId = list.first().packageUUID
                        state.selectedRecordLabel = list.first().displayLabel()
                    } else {
                        state.selectedRecordId = ""
                        state.selectedRecordLabel = ""
                    }
                    state.selectedTab = MainTab.LOG
                    state.succeedOperation("Load API List", "Loaded ${list.size} record(s).")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state.selectedTab = MainTab.LOG
                    state.failOperation("Load API List", "${e::class.simpleName}: ${e.message}")
                }
            }
        }
    }

    fun fetchRecord() {
        state.startOperation("Fetch Record", "Fetching selected record...")
        scope.launch(Dispatchers.IO) {
            try {
                require(state.selectedRecordId.isNotBlank()) { "No record selected" }
                val protect = protectLabelToValue(state.selectedProtectLabel)
                val loaded = api.fetchRecord(state.baseUrl, state.selectedRecordId, protect)

                withContext(Dispatchers.Main) {
                    val prettyRo = prettyPrintJson(loaded.roJson)
                    val prettyRw = prettyPrintJson(loaded.rwJson)

                    state.fetchedRoJson = loaded.roJson
                    state.fetchedRwJson = loaded.rwJson

                    state.cardInfoText = buildString {
                        appendLine("Source package: ${loaded.meta.id}")
                        appendLine("Cutoff: ${loaded.meta.cutoff}")
                        appendLine("Protect: ${state.selectedProtectLabel}")
                        appendLine("Protect (server): ${loaded.meta.protect}")
                        appendLine("Encoding: ${loaded.meta.encoding}")
                        appendLine("RO JSON bytes: ${loaded.meta.roBytesJson}")
                        appendLine("RW JSON bytes: ${loaded.meta.rwBytesJson}")
                        appendLine("RO gzip bytes: ${loaded.meta.roBytesGz}")
                        appendLine("RW gzip bytes: ${loaded.meta.rwBytesGz}")
                    }.trim()

                    state.payloadText = buildString {
                        appendLine("--- RO / E104 candidate ---")
                        appendLine(prettyRo)
                        appendLine()
                        appendLine("--- RW / E105 candidate ---")
                        appendLine(prettyRw)
                    }.trim()

                    state.payloadRoText = prettyRo

                    state.payloadEditable = prettyRw
                    state.selectedTab = MainTab.EDIT_EXTRA
                    state.succeedOperation("Fetch Record", "Record fetched and decoded.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state.selectedTab = MainTab.LOG
                    state.failOperation("Fetch Record", "${e::class.simpleName}: ${e.message}")
                }
            }
        }
    }
}