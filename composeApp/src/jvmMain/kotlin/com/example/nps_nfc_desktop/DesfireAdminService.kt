package com.example.nps_nfc_desktop

class DesfireAdminService(
    private val reader: NfcReader
) {
    fun wipeCard(onStatus: (String) -> Unit) {
        error("Not yet wired: DESFire auth/format layer required")
    }
}