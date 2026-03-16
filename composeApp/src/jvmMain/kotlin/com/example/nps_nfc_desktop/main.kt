package com.example.nps_nfc_desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
//    PcscBootstrap.configure()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "NPSNFCDesktop",
            state = rememberWindowState(
                width = 1400.dp,
                height = 1050.dp
            )
        ) {
            App()
        }
    }
}