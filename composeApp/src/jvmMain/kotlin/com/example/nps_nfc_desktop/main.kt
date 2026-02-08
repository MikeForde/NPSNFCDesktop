package com.example.nps_nfc_desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "NPSNFCDesktop",
    ) {
        App()
    }
}