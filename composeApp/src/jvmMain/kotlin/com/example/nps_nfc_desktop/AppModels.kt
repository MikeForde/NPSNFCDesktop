package com.example.nps_nfc_desktop

enum class MainTab(val title: String) {
    CARD_INFO("Card Info"),
    PAYLOAD("NPS - RO"),
    EDIT_EXTRA("Edit EXTRA - RW"),
    LOG("Log")
}

enum class OperationState {
    IDLE,
    WORKING,
    SUCCESS,
    ERROR
}

data class BundleSummary(
    val bundleId: String?,
    val total: Int?,
    val entryKeys: Set<String>
)

data class ApiUpdateCheckResult(
    val bundleId: String,
    val fetchedRoJson: String,
    val fetchedRwJson: String,
    val cardExtraSummary: BundleSummary?,
    val fetchedRwSummary: BundleSummary?,
    val newEntryKeys: Set<String>
) {
    val hasUpdate: Boolean get() = newEntryKeys.isNotEmpty()
}