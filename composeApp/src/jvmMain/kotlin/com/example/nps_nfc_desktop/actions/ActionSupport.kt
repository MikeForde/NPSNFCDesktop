package com.example.nps_nfc_desktop.actions

import com.example.nps_nfc_desktop.model.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class ActionSupport(
    protected val scope: CoroutineScope,
    protected val state: AppState
) {
    protected fun log(msg: String) {
        scope.launch(Dispatchers.Main) {
            state.log(msg)
        }
    }
}