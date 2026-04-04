package com.example.nps_nfc_desktop.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.nps_nfc_desktop.services.IpsListItem
import java.net.URI
import java.util.prefs.Preferences

class AppState {

    private val prefs = Preferences.userRoot().node("com.example.nps_nfc_desktop")

    companion object {
        const val LOCAL_URL = "http://localhost:5049"
        const val AZURE_URL = "https://ipsmern-dep.azurewebsites.net"
        const val D2S_URL = "https://ips-d2s-uksc-medsnomed-medsno.apps.ocp1.azure.dso.digital.mod.uk"

        private const val PREF_BASE_URL = "baseUrl"
        private const val PREF_SERVER_ENTRIES = "serverEntries"
    }

    private fun defaultServers(): List<ServerEntry> = listOf(
        ServerEntry(LOCAL_URL, true),
        ServerEntry(AZURE_URL, true),
        ServerEntry(D2S_URL, true)
    )

    private fun normalizeUrl(url: String): String =
        url.trim().removeSuffix("/")

    private fun isReasonableHttpUrl(url: String): Boolean {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return false
        return try {
            val parsed = URI(url)
            !parsed.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    private fun encodeServers(servers: List<ServerEntry>): String =
        servers.joinToString("||") { "${it.enabled}::${it.url}" }

    private fun decodeServers(raw: String?): List<ServerEntry> {
        if (raw.isNullOrBlank()) return defaultServers()

        val decoded = raw.split("||")
            .mapNotNull { token ->
                val parts = token.split("::", limit = 2)
                if (parts.size != 2) return@mapNotNull null

                val enabled = parts[0].toBooleanStrictOrNull() ?: true
                val url = normalizeUrl(parts[1])

                if (url.isBlank()) return@mapNotNull null
                ServerEntry(url = url, enabled = enabled)
            }
            .distinctBy { it.url }

        return decoded.ifEmpty { defaultServers() }
    }

    private fun persistServers() {
        prefs.put(PREF_SERVER_ENTRIES, encodeServers(serverEntries))
    }

    private fun ensureValidBaseUrl(preferred: String? = null) {
        if (serverEntries.isEmpty()) {
            serverEntries.addAll(defaultServers())
            persistServers()
        }

        val enabledUrls = serverEntries.filter { it.enabled }.map { it.url }

        if (enabledUrls.isEmpty()) {
            serverEntries[0] = serverEntries[0].copy(enabled = true)
            persistServers()
        }

        val refreshedEnabledUrls = serverEntries.filter { it.enabled }.map { it.url }

        val target = when {
            preferred != null && preferred in refreshedEnabledUrls -> preferred
            _baseUrl in refreshedEnabledUrls -> _baseUrl
            else -> refreshedEnabledUrls.first()
        }

        _baseUrl = target
        prefs.put(PREF_BASE_URL, _baseUrl)
    }

    var cardInfoText by mutableStateOf("(none)")
    var payloadText by mutableStateOf("(none)")
    var payloadEditable by mutableStateOf("")
    var payloadRoText by mutableStateOf("")
    var selectedTab by mutableStateOf(MainTab.CARD_INFO)

    var cardWasLegacy by mutableStateOf(false)
    var currentCardRoJson by mutableStateOf("")

    var operationState by mutableStateOf(OperationState.IDLE)
    var operationTitle by mutableStateOf("Ready")
    var operationMessage by mutableStateOf("Tap an action to begin.")

    private var _baseUrl by mutableStateOf(
        normalizeUrl(prefs.get(PREF_BASE_URL, LOCAL_URL))
    )
    val baseUrl: String get() = _baseUrl

    val serverEntries = mutableStateListOf<ServerEntry>().apply {
        addAll(decodeServers(prefs.get(PREF_SERVER_ENTRIES, null)))
    }

    val baseUrlOptions: List<String>
        get() = serverEntries.filter { it.enabled }.map { it.url }

    init {
        ensureValidBaseUrl(preferred = LOCAL_URL)
    }

    fun setBaseUrl(url: String) {
        val normalized = normalizeUrl(url)
        if (normalized in baseUrlOptions) {
            _baseUrl = normalized
            prefs.put(PREF_BASE_URL, normalized)
        }
    }

    fun replaceServerEntries(entries: List<ServerEntry>): Boolean {
        val normalized = entries
            .map {
                it.copy(url = normalizeUrl(it.url))
            }
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url }

        if (normalized.isEmpty()) return false
        if (normalized.any { !isReasonableHttpUrl(it.url) }) return false
        if (normalized.none { it.enabled }) return false

        serverEntries.clear()
        serverEntries.addAll(normalized)
        persistServers()
        ensureValidBaseUrl(preferred = _baseUrl)
        return true
    }

    fun resetServerEntries() {
        serverEntries.clear()
        serverEntries.addAll(defaultServers())
        persistServers()
        ensureValidBaseUrl(preferred = LOCAL_URL)
    }

    val protectOptions = listOf(
        "0 - none",
        "1 - field-level encryption (JWE)",
        "2 - omit identifiers"
    )
    var selectedProtectLabel by mutableStateOf(protectOptions.first())
    var protectMenuExpanded by mutableStateOf(false)

    var availableRecords by mutableStateOf<List<IpsListItem>>(emptyList())
    var selectedRecordId by mutableStateOf("")
    var selectedRecordLabel by mutableStateOf("")
    var recordMenuExpanded by mutableStateOf(false)

    var fetchedRoJson by mutableStateOf("")
    var fetchedRwJson by mutableStateOf("")

    var pendingRebuildConfirmation by mutableStateOf(false)
    var pendingRebuildUid by mutableStateOf<String?>(null)
    var pendingRebuildSummary by mutableStateOf("")

    var pendingApiUpdateRwJson by mutableStateOf("")
    var pendingApiUpdateSummary by mutableStateOf("")
    var pendingApiUpdateBundleId by mutableStateOf("")

    var pendingServerUpdateRwJson by mutableStateOf("")
    var pendingServerUpdateSummary by mutableStateOf("")

    val logLines = mutableStateListOf("Idle")

    var showAddObservationDialog by mutableStateOf(false)

    var selectedObservationType by mutableStateOf(ObservationEntryType.BLOOD_PRESSURE)
    var observationValue1Text by mutableStateOf("")
    var observationValue2Text by mutableStateOf("")

    var baseUrlMenuExpanded by mutableStateOf(false)

    fun log(msg: String) {
        logLines.add(msg)
        while (logLines.size > 120) {
            logLines.removeAt(0)
        }
    }

    fun startOperation(title: String, message: String) {
        operationState = OperationState.WORKING
        operationTitle = title
        operationMessage = message
        log("$title: started")
    }

    fun succeedOperation(title: String, message: String) {
        operationState = OperationState.SUCCESS
        operationTitle = title
        operationMessage = message
        log("$title: success")
    }

    fun failOperation(title: String, message: String) {
        operationState = OperationState.ERROR
        operationTitle = title
        operationMessage = message
        log("$title: error - $message")
    }

    fun clearRebuildConfirmation() {
        pendingRebuildConfirmation = false
        pendingRebuildUid = null
        pendingRebuildSummary = ""
    }
}