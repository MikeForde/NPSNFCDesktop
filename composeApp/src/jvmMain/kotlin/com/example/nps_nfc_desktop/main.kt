package com.example.nps_nfc_desktop

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.example.nps_nfc_desktop.model.AppState
import com.example.nps_nfc_desktop.ui.App
import com.example.nps_nfc_desktop.ui.SettingsDialog

fun main() {
    application {
        val appState = remember { AppState() }
        var showSettings by remember { mutableStateOf(false) }

        Window(
            onCloseRequest = ::exitApplication,
            title = "NPSNFCDesktop",
            state = rememberWindowState(
                width = 1020.dp,
                height = 850.dp
            )
        ) {
            MenuBar {
                Menu("File") {
                    Item(
                        text = "Settings…",
                        onClick = { showSettings = true },
                        shortcut = KeyShortcut(Key.Comma, meta = true)
                    )
                }
            }

            val scale = 0.9f

            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = LocalDensity.current.density * scale,
                    fontScale = LocalDensity.current.fontScale * scale
                )
            ) {
                App(appState)
            }

            if (showSettings) {
                Window(
                    onCloseRequest = { showSettings = false },
                    title = "Settings",
                    resizable = true,
                    state = rememberWindowState(
                        width = 900.dp,
                        height = 620.dp
                    )
                ) {
                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            density = LocalDensity.current.density * scale,
                            fontScale = LocalDensity.current.fontScale * scale
                        )
                    ) {
                        SettingsDialog(
                            appState = appState,
                            onClose = { showSettings = false }
                        )
                    }
                }
            }
        }
    }
}
