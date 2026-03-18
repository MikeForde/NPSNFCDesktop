package com.example.nps_nfc_desktop.services

import com.example.nps_nfc_desktop.nfc.NdefCodec
import com.example.nps_nfc_desktop.nfc.NfcReader
import com.example.nps_nfc_desktop.nfc.ParsedNdefPayload
import javax.smartcardio.ResponseAPDU

data class FullWriteResult(
    val stateBefore: CardState,
    val writtenNps: ParsedNdefPayload,
    val writtenExtra: ParsedNdefPayload
) {
    fun toDisplayText(): String = buildString {
        appendLine("State before write: ${stateBefore.label}")
        appendLine("Detail: ${stateBefore.detail}")
        appendLine()
        appendLine("E104 MIME: ${writtenNps.mimeType}")
        appendLine("E104 compressed bytes: ${writtenNps.compressedPayload.size}")
        appendLine()
        appendLine("E105 MIME: ${writtenExtra.mimeType}")
        appendLine("E105 compressed bytes: ${writtenExtra.compressedPayload.size}")
    }.trim()
}

data class CardInspection(
    val readerName: String,
    val protocol: String,
    val atrHex: String?,
    val uidHex: String?,
    val ccFileHex: String?,
    val ccSummary: String?,
    val nps: ParsedNdefPayload?,
    val extra: ParsedNdefPayload?,
    val state: CardState
) {
    fun toDisplayText(): String = buildString {
        appendLine("Reader: $readerName")
        appendLine("Protocol: $protocol")
        appendLine("ATR: ${atrHex ?: "(none)"}")
        appendLine("UID: ${uidHex ?: "(not available)"}")
        appendLine("Card State: ${state.label}")
        appendLine("State Detail: ${state.detail}")
        appendLine("CC: ${ccFileHex ?: "(not read)"}")
        appendLine("CC Summary: ${ccSummary ?: "(not parsed)"}")
        appendLine()
        appendLine("--- E104 / NPS ---")
        if (nps == null) {
            appendLine("(not read)")
        } else {
            appendLine("MIME: ${nps.mimeType}")
            appendLine("Compressed bytes: ${nps.compressedPayload.size}")
            appendLine("JSON:")
            appendLine(nps.decompressedText ?: "(not gzip or not decoded)")
        }
        appendLine()
        appendLine("--- E105 / EXTRA ---")
        if (extra == null) {
            appendLine("(not read)")
        } else {
            appendLine("MIME: ${extra.mimeType}")
            appendLine("Compressed bytes: ${extra.compressedPayload.size}")
            appendLine("JSON:")
            appendLine(extra.decompressedText ?: "(not gzip or not decoded)")
        }
    }.trim()
}

enum class CardStateKind {
    BLANK_OR_UNFORMATTED,
    NATO_FORMATTED,
    PARTIAL_OR_UNEXPECTED,
    ERROR
}

data class CardState(
    val kind: CardStateKind,
    val label: String,
    val detail: String,
    val allowOverwriteForDemo: Boolean
)

class DesfireNdefService(
    private val reader: NfcReader
) {

    companion object {
        private val SELECT_NDEF_APP_BY_DF_NAME = hex(
            "00A4040007D276000085010100"
        )

        private val SELECT_CC_FILE = hex(
            "00A4000C02E103"
        )

        private val SELECT_NPS_FILE = hex(
            "00A4000C02E104"
        )

        private val SELECT_EXTRA_FILE = hex(
            "00A4000C02E105"
        )

        private val GET_UID = hex(
            "FFCA000000"
        )
    }

    fun writeExtraJson(
        jsonText: String,
        onStatus: (String) -> Unit
    ): ParsedNdefPayload {
        return reader.withFirstCard(onStatus) { session ->
            selectNdefApplication(session, onStatus)

            val ccBytes = readCcFile(session, onStatus)
            val extraCapacity = extractMaxSizeFromCc(ccBytes, 0xE105)
            onStatus("E105 capacity from CC: $extraCapacity bytes")

            val compressed = NdefCodec.gzipUtf8(jsonText)
            val message = NdefCodec.buildSingleMimeMessage(
                mimeType = "application/x.ext.gzip.v1-0",
                payload = compressed
            )
            val fileBytes = NdefCodec.wrapAsType4NdefFile(message)

            require(fileBytes.size <= extraCapacity) {
                "E105 payload too large: need ${fileBytes.size} bytes, capacity is $extraCapacity"
            }

            writeSelectedType4File(
                session = session,
                selectFileApdu = SELECT_EXTRA_FILE,
                label = "E105",
                fileBytes = fileBytes,
                onStatus = onStatus
            )

            val verifyBytes = readSelectedType4File(session, SELECT_EXTRA_FILE, "E105", onStatus)
            NdefCodec.parseType4NdefFile(verifyBytes)
        }
    }

    private fun readCcFile(
        session: NfcReader.CardSession,
        onStatus: (String) -> Unit
    ): ByteArray {
        val selectResp = session.transmit(SELECT_CC_FILE, onStatus)
        require(selectResp.sw == 0x9000) {
            "Failed to select E103, SW=${selectResp.swHex()}"
        }
        onStatus("Selected file E103")
        return readBinary(session, 0, 23)
    }

    private fun extractMaxSizeFromCc(ccBytes: ByteArray, targetFileId: Int): Int {
        require(ccBytes.size >= 23) { "CC too short" }

        fun parseTlv(offset: Int): Pair<Int, Int>? {
            if (ccBytes.size < offset + 8) return null
            val t = ccBytes[offset].toInt() and 0xFF
            val l = ccBytes[offset + 1].toInt() and 0xFF
            if (t != 0x04 || l != 0x06) return null

            val fileId = ((ccBytes[offset + 2].toInt() and 0xFF) shl 8) or
                    (ccBytes[offset + 3].toInt() and 0xFF)
            val maxSize = ((ccBytes[offset + 4].toInt() and 0xFF) shl 8) or
                    (ccBytes[offset + 5].toInt() and 0xFF)

            return fileId to maxSize
        }

        val tlv1 = parseTlv(7)
        val tlv2 = parseTlv(15)

        return listOfNotNull(tlv1, tlv2)
            .firstOrNull { it.first == targetFileId }
            ?.second
            ?: error("File ID 0x${"%04X".format(targetFileId)} not found in CC")
    }

    private fun writeSelectedType4File(
        session: NfcReader.CardSession,
        selectFileApdu: ByteArray,
        label: String,
        fileBytes: ByteArray,
        onStatus: (String) -> Unit
    ) {
        val selectResp = session.transmit(selectFileApdu, onStatus)
        require(selectResp.sw == 0x9000) {
            "Failed to select $label for write, SW=${selectResp.swHex()}"
        }
        onStatus("Selected file $label for write")

        // Step 1: NLEN = 0
        updateBinary(session, 0, byteArrayOf(0x00, 0x00))
        onStatus("$label NLEN cleared")

        // Step 2: write exact [NLEN + NDEF] bytes, no padding
        var offset = 0
        val chunkSize = 240

        while (offset < fileBytes.size) {
            val end = minOf(offset + chunkSize, fileBytes.size)
            val chunk = fileBytes.copyOfRange(offset, end)
            updateBinary(session, offset, chunk)
            offset = end
        }

        onStatus("$label write complete (${fileBytes.size} bytes)")
    }

    private fun updateBinary(
        session: NfcReader.CardSession,
        offset: Int,
        data: ByteArray
    ) {
        require(offset in 0..0xFFFF) { "Offset out of range for UPDATE BINARY: $offset" }
        require(data.isNotEmpty()) { "UPDATE BINARY data must not be empty" }
        require(data.size <= 255) { "UPDATE BINARY chunk too large: ${data.size}" }

        val apdu = ByteArray(5 + data.size)
        apdu[0] = 0x00
        apdu[1] = 0xD6.toByte()
        apdu[2] = ((offset shr 8) and 0xFF).toByte()
        apdu[3] = (offset and 0xFF).toByte()
        apdu[4] = data.size.toByte()
        System.arraycopy(data, 0, apdu, 5, data.size)

        val response = session.transmit(apdu)
        require(response.sw == 0x9000) {
            "UPDATE BINARY failed at offset=$offset, SW=${response.swHex()}"
        }
    }

    private fun tryReadCcFile(
        session: NfcReader.CardSession,
        onStatus: (String) -> Unit
    ): ByteArray? {
        return try {
            val selectResp = session.transmit(SELECT_CC_FILE, onStatus)
            require(selectResp.sw == 0x9000) {
                "Failed to select E103, SW=${selectResp.swHex()}"
            }
            onStatus("Selected file E103")

            val ccBytes = readBinary(session, 0, 23)
            onStatus("E103 raw bytes read: ${ccBytes.size}")
            ccBytes
        } catch (e: Exception) {
            onStatus("Could not read E103: ${e.message}")
            null
        }
    }

    private fun tryReadNdefFile(
        session: NfcReader.CardSession,
        selectFileApdu: ByteArray,
        label: String,
        onStatus: (String) -> Unit
    ): ByteArray? {
        return try {
            readSelectedType4File(session, selectFileApdu, label, onStatus)

        } catch (e: Exception) {
            onStatus("Could not read $label: ${e.message}")
            null
        }
    }

    private fun classifyCard(
        ccBytes: ByteArray?,
        nps: ParsedNdefPayload?,
        extra: ParsedNdefPayload?
    ): CardState {
        val hasCc = ccBytes != null && ccBytes.size == 23
        val hasExpectedCc = hasCc && ccLooksLikeNato(ccBytes)
        val hasNps = nps != null && nps.mimeType == "application/x.nps.gzip.v1-0"
        val hasExtra = extra != null && extra.mimeType == "application/x.ext.gzip.v1-0"

        return when {
            hasExpectedCc && hasNps && hasExtra -> CardState(
                kind = CardStateKind.NATO_FORMATTED,
                label = "NATO formatted",
                detail = "CC, E104 and E105 are present and readable.",
                allowOverwriteForDemo = true
            )

            !hasCc && !hasNps && !hasExtra -> CardState(
                kind = CardStateKind.BLANK_OR_UNFORMATTED,
                label = "Blank / unformatted",
                detail = "No NATO CC/NDEF structure was detected.",
                allowOverwriteForDemo = true
            )

            hasExpectedCc || hasNps || hasExtra -> CardState(
                kind = CardStateKind.PARTIAL_OR_UNEXPECTED,
                label = "Partial / unexpected",
                detail = "Some NATO-like structure was found, but the card is not fully in the expected layout.",
                allowOverwriteForDemo = true
            )

            else -> CardState(
                kind = CardStateKind.ERROR,
                label = "Unreadable / unknown",
                detail = "Card could not be confidently classified.",
                allowOverwriteForDemo = false
            )
        }
    }

    private fun ccLooksLikeNato(ccBytes: ByteArray): Boolean {
        if (ccBytes.size != 23) return false

        val tlv1FileId = ((ccBytes[9].toInt() and 0xFF) shl 8) or (ccBytes[10].toInt() and 0xFF)
        val tlv2FileId = ((ccBytes[17].toInt() and 0xFF) shl 8) or (ccBytes[18].toInt() and 0xFF)

        return ccBytes[0] == 0x00.toByte() &&
                ccBytes[1] == 0x17.toByte() &&
                ccBytes[2] == 0x20.toByte() &&
                tlv1FileId == 0xE104 &&
                tlv2FileId == 0xE105
    }

    fun inspectCard(onStatus: (String) -> Unit): CardInspection {
        return reader.withFirstCard(onStatus) { session ->
            val uid = tryGetUid(session, onStatus)
            selectNdefApplication(session, onStatus)

            val ccBytes = tryReadCcFile(session, onStatus)
            val ccSummary = ccBytes?.let { summarizeCc(it) }

            val npsBytes = tryReadNdefFile(session, SELECT_NPS_FILE, "E104", onStatus)

            selectNdefApplication(session, onStatus)

            val extraBytes = tryReadNdefFile(session, SELECT_EXTRA_FILE, "E105", onStatus)

            val parsedNps = try {
                npsBytes?.let { NdefCodec.parseType4NdefFile(it) }
            } catch (e: Exception) {
                onStatus("Failed to parse E104 as NDEF: ${e.message}")
                null
            }

            val parsedExtra = try {
                extraBytes?.let { NdefCodec.parseType4NdefFile(it) }
            } catch (e: Exception) {
                onStatus("Failed to parse E105 as NDEF: ${e.message}")
                null
            }

            val state = classifyCard(
                ccBytes = ccBytes,
                nps = parsedNps,
                extra = parsedExtra
            )

            CardInspection(
                readerName = session.readerName,
                protocol = session.protocol,
                atrHex = session.atrHex,
                uidHex = uid,
                ccFileHex = ccBytes?.toHex(),
                ccSummary = ccSummary,
                nps = parsedNps,
                extra = parsedExtra,
                state = state
            )
        }
    }

//    fun inspectCard(onStatus: (String) -> Unit): CardInspection {
//        return reader.withFirstCard(onStatus) { session ->
//            val uid = tryGetUid(session, onStatus)
//            selectNdefApplication(session, onStatus)
//
//            val ccBytes = tryReadCcFile(session, onStatus)
//            val ccSummary = ccBytes?.let { summarizeCc(it) }
//
//            val npsBytes = tryReadNdefFile(session, SELECT_NPS_FILE, "E104", onStatus)
//
//            selectNdefApplication(session, onStatus)
//
//            val extraBytes = tryReadNdefFile(session, SELECT_EXTRA_FILE, "E105", onStatus)
//            CardInspection(
//                readerName = session.readerName,
//                protocol = session.protocol,
//                atrHex = session.atrHex,
//                uidHex = uid,
//                ccFileHex = null,
//                ccSummary = null,
//                nps = null,
//                extra = null,
//                state = CardState(
//                    kind = CardStateKind.ERROR,
//                    label = "Test only",
//                    detail = "UID only",
//                    allowOverwriteForDemo = false
//                )
//            )
//        }
//    }

    fun readNps(onStatus: (String) -> Unit): ParsedNdefPayload {
        return reader.withFirstCard(onStatus) { session ->
            selectNdefApplication(session, onStatus)
            val fileBytes = readSelectedType4File(session, SELECT_NPS_FILE, "E104", onStatus)
            NdefCodec.parseType4NdefFile(fileBytes)
        }
    }

    fun readExtra(onStatus: (String) -> Unit): ParsedNdefPayload {
        return reader.withFirstCard(onStatus) { session ->
            selectNdefApplication(session, onStatus)
            val fileBytes = readSelectedType4File(session, SELECT_EXTRA_FILE, "E105", onStatus)
            NdefCodec.parseType4NdefFile(fileBytes)
        }
    }

    private fun selectNdefApplication(
        session: NfcReader.CardSession,
        onStatus: (String) -> Unit
    ) {
        val response = session.transmit(SELECT_NDEF_APP_BY_DF_NAME, onStatus)
        require(response.sw == 0x9000) {
            "Failed to select NDEF application by DF name, SW=${response.swHex()}"
        }
        onStatus("Selected NDEF application (D2760000850101)")
    }

    private fun tryGetUid(
        session: NfcReader.CardSession,
        onStatus: (String) -> Unit
    ): String? {
        return try {
            val response = session.transmit(GET_UID, onStatus)
            if (response.sw == 0x9000 && response.data.isNotEmpty()) {
                val uid = response.data.toHex()
                onStatus("UID: $uid")
                uid
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readSelectedType4File(
        session: NfcReader.CardSession,
        selectFileApdu: ByteArray,
        label: String,
        onStatus: (String) -> Unit
    ): ByteArray {
        val selectResp = session.transmit(selectFileApdu, onStatus)
        require(selectResp.sw == 0x9000) {
            "Failed to select $label, SW=${selectResp.swHex()}"
        }
        onStatus("Selected file $label")

        val firstTwo = readBinary(session, 0, 2)
        require(firstTwo.size == 2) { "Failed to read NLEN from $label" }

        val nlen = ((firstTwo[0].toInt() and 0xFF) shl 8) or (firstTwo[1].toInt() and 0xFF)
        onStatus("$label NLEN=$nlen")

        val totalSize = 2 + nlen
        val result = readBinary(session, 0, totalSize)
        onStatus("$label total bytes read=${result.size}")
        return result
    }

    private fun readBinary(
        session: NfcReader.CardSession,
        offset: Int,
        length: Int
    ): ByteArray {
        if (length <= 0) return ByteArray(0)

        val out = ArrayList<Byte>()
        var currentOffset = offset
        var remaining = length

        while (remaining > 0) {
            val chunk = minOf(remaining, 0xFA)
            val apdu = byteArrayOf(
                0x00,
                0xB0.toByte(),
                ((currentOffset ushr 8) and 0xFF).toByte(),
                (currentOffset and 0xFF).toByte(),
                chunk.toByte()
            )

            val response = session.transmit(apdu)
            require(response.sw == 0x9000) {
                "READ BINARY failed at offset=$currentOffset, SW=${response.swHex()}"
            }

            out.addAll(response.data.toList())
            currentOffset += response.data.size
            remaining -= response.data.size

            if (response.data.isEmpty()) {
                break
            }
        }

        return out.toByteArray()
    }

    private fun summarizeCc(ccBytes: ByteArray): String {
        if (ccBytes.size < 23) return "CC too short (${ccBytes.size} bytes)"

        val cclen = ((ccBytes[0].toInt() and 0xFF) shl 8) or (ccBytes[1].toInt() and 0xFF)
        val version = ccBytes[2].toInt() and 0xFF
        val mle = ((ccBytes[3].toInt() and 0xFF) shl 8) or (ccBytes[4].toInt() and 0xFF)
        val mlc = ((ccBytes[5].toInt() and 0xFF) shl 8) or (ccBytes[6].toInt() and 0xFF)

        fun parseTlv(offset: Int): String {
            if (ccBytes.size < offset + 8) return "TLV@${offset}: incomplete"

            val t = ccBytes[offset].toInt() and 0xFF
            val l = ccBytes[offset + 1].toInt() and 0xFF
            val fileId = ((ccBytes[offset + 2].toInt() and 0xFF) shl 8) or
                    (ccBytes[offset + 3].toInt() and 0xFF)
            val maxSize = ((ccBytes[offset + 4].toInt() and 0xFF) shl 8) or
                    (ccBytes[offset + 5].toInt() and 0xFF)
            val readAccess = ccBytes[offset + 6].toInt() and 0xFF
            val writeAccess = ccBytes[offset + 7].toInt() and 0xFF

            return "TLV(type=0x${"%02X".format(t)}, len=$l, fileId=0x${"%04X".format(fileId)}, maxSize=$maxSize, read=0x${"%02X".format(readAccess)}, write=0x${"%02X".format(writeAccess)})"
        }

        val tlv1 = parseTlv(7)
        val tlv2 = parseTlv(15)

        return "CCLEN=$cclen, version=0x${"%02X".format(version)}, MLe=$mle, MLc=$mlc, $tlv1, $tlv2"
    }
}

private fun ResponseAPDU.swHex(): String =
    "%04X".format(sw)

private fun ByteArray.toHex(): String =
    joinToString("") { "%02X".format(it) }

private fun hex(value: String): ByteArray {
    val clean = value.replace(" ", "").replace("\n", "")
    require(clean.length % 2 == 0) { "Hex string must have even length" }
    return clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}