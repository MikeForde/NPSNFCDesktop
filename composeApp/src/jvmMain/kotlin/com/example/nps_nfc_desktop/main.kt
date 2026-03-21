package com.example.nps_nfc_desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.nps_nfc_desktop.ui.App

fun main() {

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "NPSNFCDesktop",
            state = rememberWindowState(
                width = 1100.dp,
                height = 850.dp
            )
        ) {
            val scale = 0.9f  // 👈 tweak this

            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = LocalDensity.current.density * scale,
                    fontScale = LocalDensity.current.fontScale * scale
                )
            ) {
                App()
            }
        }
    }
}