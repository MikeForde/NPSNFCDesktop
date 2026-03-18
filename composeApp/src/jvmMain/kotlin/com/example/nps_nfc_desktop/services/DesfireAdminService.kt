package com.example.nps_nfc_desktop.services

import com.example.nps_nfc_desktop.nfc.NdefCodec
import com.example.nps_nfc_desktop.nfc.NfcReader
import nfcjlib.core.DESFireEV1
import nfcjlib.core.DESFireEV1.KeyType
import kotlin.math.max

data class RebuildResult(
    val npsCapacity: Int,
    val extraCapacity: Int
) {
    fun toDisplayText(): String = buildString {
        appendLine("Rebuild complete.")
        appendLine("E104 capacity: $npsCapacity bytes")
        appendLine("E105 capacity: $extraCapacity bytes")
        appendLine("Layout: AID 000001, E103/E104/E105 created")
    }.trim()
}

class DesfireAdminService(
    private val reader: NfcReader
) {

    fun wipeCard(onStatus: (String) -> Unit) {
        val desfire = DESFireEV1()

        try {
            onStatus("Connecting to card via DESFire admin layer...")
            require(desfire.connect()) {
                "Could not connect to card. Make sure a card is on the reader."
            }

            onStatus("Selecting PICC master application (000000)...")
            require(desfire.selectApplication(byteArrayOf(0x00, 0x00, 0x00))) {
                "Failed to select PICC master application."
            }

            onStatus("Authenticating PICC master key (default DES key 00 00 00 00 00 00 00 00)...")
            val sessionKey = desfire.authenticate(
                ByteArray(8) { 0x00 },
                0x00.toByte(),
                KeyType.DES
            )
            require(sessionKey != null) {
                "PICC master authentication failed."
            }

            onStatus("Formatting PICC (destructive wipe)...")
            require(desfire.formatPICC()) {
                "FormatPICC failed."
            }

            onStatus("PICC format complete.")
        } finally {
            try {
                desfire.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    fun rebuildCard(
        roJson: String,
        rwJson: String,
        onStatus: (String) -> Unit
    ): RebuildResult {
        val desfire = DESFireEV1()

        try {
            onStatus("Connecting to card...")
            require(desfire.connect()) {
                "Could not connect to card. Make sure a card is on the reader."
            }

            // Step 1: select PICC + auth + wipe
            onStatus("Selecting PICC master application (000000)...")
            require(desfire.selectApplication(byteArrayOf(0x00, 0x00, 0x00))) {
                "Failed to select PICC master application."
            }

            onStatus("Authenticating PICC master key...")
            val piccSession = desfire.authenticate(
                ByteArray(8) { 0x00 },
                0x00.toByte(),
                KeyType.DES
            )
            require(piccSession != null) {
                "PICC master authentication failed."
            }

            onStatus("Formatting PICC...")
            require(desfire.formatPICC()) {
                "FormatPICC failed."
            }

            // Build NDEF file bytes first so we know capacity needs
            val npsMessage = NdefCodec.buildSingleMimeMessage(
                mimeType = "application/x.nps.gzip.v1-0",
                payload = NdefCodec.gzipUtf8(roJson)
            )
            val npsFileBytes = NdefCodec.wrapAsType4NdefFile(npsMessage)

            val extraMessage = NdefCodec.buildSingleMimeMessage(
                mimeType = "application/x.ext.gzip.v1-0",
                payload = NdefCodec.gzipUtf8(rwJson)
            )
            val extraFileBytes = NdefCodec.wrapAsType4NdefFile(extraMessage)

            // Small growth margin + alignment for E104
            val npsCapacity = roundUp(max(npsFileBytes.size + 16, 384), 64)

            // Step 2: create ISO NDEF app via raw native APDU
            onStatus("Creating NFC Forum NDEF application...")
            val createAppBody = hex("0100000F210501D2760000850101")
            val createAppResp = sendNative(desfire, 0xCA, createAppBody)
            require(isNativeSuccess(createAppResp, allowDuplicate = true)) {
                "CreateApplication failed: ${createAppResp.toHex()}"
            }

            // Step 3: select app + auth
            onStatus("Selecting NDEF application (000001)...")
            require(desfire.selectApplication(byteArrayOf(0x01, 0x00, 0x00))) {
                "Failed to select NDEF application."
            }

            onStatus("Authenticating NDEF application master key...")
            val appSession = desfire.authenticate(
                ByteArray(8) { 0x00 },
                0x00.toByte(),
                KeyType.DES
            )
            require(appSession != null) {
                "NDEF application authentication failed."
            }

            // Step 4: create E103 CC
            onStatus("Creating CC file E103...")
            val createCcBody = byteArrayOf(
                0x01,                   // fileNo
                0x03, 0xE1.toByte(),    // ISO FID E103 (LE in body)
                0x00,                   // comm = plain
                0xEE.toByte(), 0xEE.toByte(),
                0x17, 0x00, 0x00        // size = 23 (LSB)
            )
            val createCcResp = sendNative(desfire, 0xCD, createCcBody)
            require(isNativeSuccess(createCcResp, allowDuplicate = true)) {
                "CreateStdDataFile E103 failed: ${createCcResp.toHex()}"
            }

            // Step 5: create E104 NPS (permissive for now)
            onStatus("Creating NPS file E104...")
            val npsCapLsb = threeByteLsb(npsCapacity)
            val createNpsBody = byteArrayOf(
                0x02,
                0x04, 0xE1.toByte(),
                0x00,
                0xEE.toByte(), 0xEE.toByte(),
                npsCapLsb[0], npsCapLsb[1], npsCapLsb[2]
            )
            val createNpsResp = sendNative(desfire, 0xCD, createNpsBody)
            require(isNativeSuccess(createNpsResp, allowDuplicate = true)) {
                "CreateStdDataFile E104 failed: ${createNpsResp.toHex()}"
            }

            // Step 6: compute free memory at PICC level and create E105
            onStatus("Computing free memory for EXTRA file...")
            require(desfire.selectApplication(byteArrayOf(0x00, 0x00, 0x00))) {
                "Failed to re-select PICC for FreeMemory."
            }

            val freeRaw = desfire.freeMemory()
            require(freeRaw != null && freeRaw.size >= 3) {
                "FreeMemory failed."
            }

            val remaining = (freeRaw[0].toInt() and 0xFF) or
                    ((freeRaw[1].toInt() and 0xFF) shl 8) or
                    ((freeRaw[2].toInt() and 0xFF) shl 16)

            val extraCapacity = roundDown(max(remaining - 128, 256), 32)
            require(extraFileBytes.size <= extraCapacity) {
                "Computed E105 capacity too small: need ${extraFileBytes.size}, capacity $extraCapacity"
            }

            require(desfire.selectApplication(byteArrayOf(0x01, 0x00, 0x00))) {
                "Failed to re-select NDEF app."
            }
            val appSession2 = desfire.authenticate(
                ByteArray(8) { 0x00 },
                0x00.toByte(),
                KeyType.DES
            )
            require(appSession2 != null) {
                "NDEF application re-authentication failed."
            }

            onStatus("Creating EXTRA file E105...")
            val extraCapLsb = threeByteLsb(extraCapacity)
            val createExtraBody = byteArrayOf(
                0x03,
                0x05, 0xE1.toByte(),
                0x00,
                0xEE.toByte(), 0xEE.toByte(),
                extraCapLsb[0], extraCapLsb[1], extraCapLsb[2]
            )
            val createExtraResp = sendNative(desfire, 0xCD, createExtraBody)
            require(isNativeSuccess(createExtraResp, allowDuplicate = true)) {
                "CreateStdDataFile E105 failed: ${createExtraResp.toHex()}"
            }

            // Step 7: write CC
            onStatus("Writing CC file E103...")
            val ccBytes = buildCcBytes(
                npsCapacity = npsCapacity,
                extraCapacity = extraCapacity
            )
            require(writeStandardFileSlice(desfire, 0x01, 0, ccBytes)) {
                "Writing CC file failed."
            }

            // Step 8: write E104 and E105 as exact NLEN + NDEF
            onStatus("Writing NPS file E104...")
            require(writeStandardFileSlice(desfire, 0x02, 0, byteArrayOf(0x00, 0x00))) {
                "Pre-clear E104 NLEN failed."
            }
            require(writeStandardFileSlice(desfire, 0x02, 0, npsFileBytes)) {
                "Writing E104 failed."
            }

            onStatus("Writing EXTRA file E105...")
            require(writeStandardFileSlice(desfire, 0x03, 0, byteArrayOf(0x00, 0x00))) {
                "Pre-clear E105 NLEN failed."
            }
            require(writeStandardFileSlice(desfire, 0x03, 0, extraFileBytes)) {
                "Writing E105 failed."
            }

            onStatus("Rebuild complete.")
            return RebuildResult(
                npsCapacity = npsCapacity,
                extraCapacity = extraCapacity
            )
        } finally {
            try {
                desfire.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun buildCcBytes(npsCapacity: Int, extraCapacity: Int): ByteArray {
        require(npsCapacity <= 0xFFFF) { "E104 capacity exceeds 2-byte CC field" }
        require(extraCapacity <= 0xFFFF) { "E105 capacity exceeds 2-byte CC field" }

        return byteArrayOf(
            0x00, 0x17, 0x20, 0x00, 0x3B, 0x00, 0x34,
            0x04, 0x06, 0xE1.toByte(), 0x04,
            ((npsCapacity shr 8) and 0xFF).toByte(),
            (npsCapacity and 0xFF).toByte(),
            0x00, 0xFF.toByte(),
            0x04, 0x06, 0xE1.toByte(), 0x05,
            ((extraCapacity shr 8) and 0xFF).toByte(),
            (extraCapacity and 0xFF).toByte(),
            0x00, 0x00
        )
    }

    private fun writeStandardFileSlice(
        desfire: DESFireEV1,
        fileNo: Int,
        offset: Int,
        data: ByteArray
    ): Boolean {
        val len = data.size
        val payload = ByteArray(1 + 3 + 3 + len)
        payload[0] = fileNo.toByte()
        payload[1] = (offset and 0xFF).toByte()
        payload[2] = ((offset shr 8) and 0xFF).toByte()
        payload[3] = ((offset shr 16) and 0xFF).toByte()
        payload[4] = (len and 0xFF).toByte()
        payload[5] = ((len shr 8) and 0xFF).toByte()
        payload[6] = ((len shr 16) and 0xFF).toByte()
        System.arraycopy(data, 0, payload, 7, len)
        return desfire.writeData(payload)
    }

    private fun sendNative(desfire: DESFireEV1, ins: Int, body: ByteArray = ByteArray(0)): ByteArray {
        val apdu = ByteArray(6 + body.size)
        apdu[0] = 0x90.toByte()
        apdu[1] = ins.toByte()
        apdu[2] = 0x00
        apdu[3] = 0x00
        apdu[4] = body.size.toByte()
        if (body.isNotEmpty()) {
            System.arraycopy(body, 0, apdu, 5, body.size)
        }
        apdu[5 + body.size] = 0x00
        return desfire.transmit(apdu) ?: ByteArray(0)
    }

    private fun isNativeSuccess(response: ByteArray, allowDuplicate: Boolean = false): Boolean {
        if (response.isEmpty()) return false
        val status = response.last().toInt() and 0xFF
        return status == 0x00 || (allowDuplicate && status == 0xDE)
    }

    private fun threeByteLsb(value: Int): ByteArray =
        byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte()
        )

    private fun roundUp(value: Int, step: Int): Int =
        ((value + step - 1) / step) * step

    private fun roundDown(value: Int, step: Int): Int =
        (value / step) * step
}

private fun hex(value: String): ByteArray {
    val clean = value.replace(" ", "").replace("\n", "")
    require(clean.length % 2 == 0) { "Hex string must have even length" }
    return clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

private fun ByteArray.toHex(): String =
    joinToString("") { "%02X".format(it) }