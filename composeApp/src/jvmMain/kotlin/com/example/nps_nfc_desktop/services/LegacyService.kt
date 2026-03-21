package com.example.nps_nfc_desktop.services

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import com.example.nps_nfc_desktop.nfc.ParsedNdefPayload

class LegacyService(
    private val desfireNdefService: DesfireNdefService
) {

    companion object {
        const val LEGACY_IPS_MIME = "application/x.ips.gzip.v1-0"
    }

    fun writeLegacyIpsJson(
        jsonText: String,
        onStatus: (String) -> Unit = {}
    ): ParsedNdefPayload {
        require(jsonText.isNotBlank()) { "Legacy IPS JSON is blank." }

        onStatus("Legacy write: gzipping merged IPS bundle")
        val gzBytes = gzipUtf8(jsonText)

        onStatus("Legacy write: writing single legacy NDEF payload")
        return desfireNdefService.writeLegacySingleRecord(
            mimeType = LEGACY_IPS_MIME,
            compressedPayload = gzBytes,
            originalJson = jsonText,
            onStatus = onStatus
        )
    }

    private fun gzipUtf8(text: String): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(text)
        }
        return baos.toByteArray()
    }
}