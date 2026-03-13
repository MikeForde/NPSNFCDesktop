package com.example.nps_nfc_desktop

import javax.smartcardio.ResponseAPDU

data class CardInspection(
    val readerName: String,
    val protocol: String,
    val atrHex: String?,
    val uidHex: String?,
    val ccFileHex: String?,
    val ccSummary: String?,
    val nps: ParsedNdefPayload?,
    val extra: ParsedNdefPayload?
) {
    fun toDisplayText(): String = buildString {
        appendLine("Reader: $readerName")
        appendLine("Protocol: $protocol")
        appendLine("ATR: ${atrHex ?: "(none)"}")
        appendLine("UID: ${uidHex ?: "(not available)"}")
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

    fun inspectCardNative(onStatus: (String) -> Unit): CardInspection {
        return reader.withFirstCard(onStatus) { session ->
            val uid = tryGetUid(session, onStatus)
            val native = DesfireNative(session)

            val selectResp = native.selectApplication(byteArrayOf(0x00, 0x00, 0x01))
            require(selectResp.ok) {
                "Native select app 000001 failed, SW=${"%04X".format(selectResp.sw)}"
            }
            onStatus("Native DESFire app select ok (000001)")

            val ccBytes = tryReadNativeFile(native, 0x01, "E103", 23, onStatus)
            val ccSummary = ccBytes?.let { summarizeCc(it) }

            val npsBytes = tryReadNativeType4File(native, 0x02, "E104", onStatus)
            val extraBytes = tryReadNativeType4File(native, 0x03, "E105", onStatus)

            val parsedNps = try {
                npsBytes?.let { NdefCodec.parseType4NdefFile(it) }
            } catch (e: Exception) {
                onStatus("Failed to parse E104 via native read: ${e.message}")
                null
            }

            val parsedExtra = try {
                extraBytes?.let { NdefCodec.parseType4NdefFile(it) }
            } catch (e: Exception) {
                onStatus("Failed to parse E105 via native read: ${e.message}")
                null
            }

            CardInspection(
                readerName = session.readerName,
                protocol = session.protocol,
                atrHex = session.atrHex,
                uidHex = uid,
                ccFileHex = ccBytes?.toHex(),
                ccSummary = ccSummary,
                nps = parsedNps,
                extra = parsedExtra
            )
        }
    }

    private fun tryReadNativeFile(
        native: DesfireNative,
        fileNo: Int,
        label: String,
        length: Int,
        onStatus: (String) -> Unit
    ): ByteArray? {
        return try {
            val resp = native.readData(fileNo, 0, length)
            require(resp.ok) {
                "Native read $label failed, SW=${"%04X".format(resp.sw)}"
            }
            onStatus("Native read $label bytes=${resp.data.size}")
            resp.data
        } catch (e: Exception) {
            onStatus("Could not native-read $label: ${e.message}")
            null
        }
    }

    private fun tryReadNativeType4File(
        native: DesfireNative,
        fileNo: Int,
        label: String,
        onStatus: (String) -> Unit
    ): ByteArray? {
        return try {
            val first = native.readData(fileNo, 0, 2)
            require(first.ok) {
                "Native read NLEN for $label failed, SW=${"%04X".format(first.sw)}"
            }
            require(first.data.size >= 2) { "Native NLEN read for $label returned <2 bytes" }

            val nlen = ((first.data[0].toInt() and 0xFF) shl 8) or (first.data[1].toInt() and 0xFF)
            onStatus("Native $label NLEN=$nlen")

            val total = 2 + nlen
            val full = native.readData(fileNo, 0, total)
            require(full.ok) {
                "Native full read for $label failed, SW=${"%04X".format(full.sw)}"
            }

            onStatus("Native $label total bytes=${full.data.size}")
            full.data
        } catch (e: Exception) {
            onStatus("Could not native-read $label: ${e.message}")
            null
        }
    }

    private fun tryReadCcFile(
        session: NfcReader.CardSession,
        onStatus: (String) -> Unit
    ): ByteArray? {
        return try {
            val selectResp = session.transmit(SELECT_CC_FILE)
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

    fun inspectCard(onStatus: (String) -> Unit): CardInspection {
        return reader.withFirstCard(onStatus) { session ->
            val uid = tryGetUid(session, onStatus)
            selectNdefApplication(session, onStatus)

            val ccBytes = tryReadCcFile(session, onStatus)
            val ccSummary = ccBytes?.let { summarizeCc(it) }

            val npsBytes = tryReadNdefFile(session, SELECT_NPS_FILE, "E104", onStatus)
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

            CardInspection(
                readerName = session.readerName,
                protocol = session.protocol,
                atrHex = session.atrHex,
                uidHex = uid,
                ccFileHex = ccBytes?.toHex(),
                ccSummary = ccSummary,
                nps = parsedNps,
                extra = parsedExtra
            )
        }
    }

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
        val response = session.transmit(SELECT_NDEF_APP_BY_DF_NAME)
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
            val response = session.transmit(GET_UID)
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

    private fun tryReadSelectedFile(
        session: NfcReader.CardSession,
        selectFileApdu: ByteArray,
        onStatus: (String) -> Unit
    ): ByteArray? {
        return try {
            val fileName = when {
                selectFileApdu.contentEquals(SELECT_CC_FILE) -> "E103"
                selectFileApdu.contentEquals(SELECT_NPS_FILE) -> "E104"
                selectFileApdu.contentEquals(SELECT_EXTRA_FILE) -> "E105"
                else -> "file"
            }
            readSelectedType4File(session, selectFileApdu, fileName, onStatus)
        } catch (e: Exception) {
            onStatus("Could not read file: ${e.message}")
            null
        }
    }

    private fun readSelectedType4File(
        session: NfcReader.CardSession,
        selectFileApdu: ByteArray,
        label: String,
        onStatus: (String) -> Unit
    ): ByteArray {
        val selectResp = session.transmit(selectFileApdu)
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