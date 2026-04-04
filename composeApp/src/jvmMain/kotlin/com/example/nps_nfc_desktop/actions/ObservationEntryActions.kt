package com.example.nps_nfc_desktop.actions

import com.example.nps_nfc_desktop.model.AppState
import com.example.nps_nfc_desktop.model.MainTab
import com.example.nps_nfc_desktop.model.ObservationEntryType
import com.example.nps_nfc_desktop.util.appendObservationToExtraBundle
import com.example.nps_nfc_desktop.util.prettyPrintJson
import kotlinx.coroutines.CoroutineScope

class ObservationEntryActions(
    scope: CoroutineScope,
    state: AppState
) : ActionSupport(scope, state) {

    fun addObservation(
        type: ObservationEntryType,
        primaryValueText: String,
        secondaryValueText: String? = null
    ) {
        try {
            val primaryValue = primaryValueText.toDoubleOrNull()
                ?: error("${type.label} value must be numeric.")

            val secondaryValue = if (type.isBloodPressure) {
                secondaryValueText?.toDoubleOrNull()
                    ?: error("Blood pressure requires two numeric values.")
            } else {
                null
            }

            require(primaryValue > 0) { "${type.label} value must be greater than 0." }
            if (secondaryValue != null) {
                require(secondaryValue > 0) { "Second value must be greater than 0." }
            }

            val baseRw = when {
                state.payloadEditable.isNotBlank() -> state.payloadEditable
                state.fetchedRwJson.isNotBlank() -> state.fetchedRwJson
                else -> error("No RW / EXTRA payload available. Fetch or read a record first.")
            }

            val updatedRwJson = appendObservationToExtraBundle(
                roJson = state.fetchedRoJson,
                rwJson = baseRw,
                type = type,
                primaryValue = primaryValue,
                secondaryValue = secondaryValue
            )

            val pretty = prettyPrintJson(updatedRwJson)

            state.payloadEditable = pretty
            state.payloadText = pretty
            state.fetchedRwJson = updatedRwJson
            state.selectedTab = MainTab.EDIT_EXTRA

            state.succeedOperation(
                "Add ${type.label}",
                "${type.label} observation added to RW as the next Observation id."
            )
        } catch (e: Exception) {
            state.failOperation("Add ${type.label}", "${e::class.simpleName}: ${e.message}")
        }
    }
}