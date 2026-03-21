package com.example.nps_nfc_desktop.services

import com.example.nps_nfc_desktop.nfc.NfcReader
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

class SimpleNdefReadService {

    data class SimpleNdefResult(
        val ok: Boolean,
        val summaryText: String,
        val payloadText: String? = null,
        val mimeType: String? = null,
        val isGzipIps: Boolean = false
    )

    private data class CcInfo(
        val version: String,
        val maxRead: Int,
        val maxWrite: Int,
        val ndefFileId: ByteArray,
        val ndefMaxSize: Int,
        val readAccess: Int,
        val writeAccess: Int
    )

    private data class NdefRecordInfo(
        val tnf: Int,
        val type: String,
        val id: String?,
        val payload: ByteArray,
        val mb: Boolean,
        val me: Boolean,
        val cf: Boolean,
        val sr: Boolean,
        val il: Boolean
    )

    fun readSimpleNdef(session: NfcReader.CardSession): SimpleNdefResult {
        return try {
            selectNdefApplication(session)

            val ccBytes = readCcFile(session)
            val cc = parseCapabilityContainer(ccBytes)

            selectFile(session, cc.ndefFileId)

            val ndefBytes = readNdefFile(session, cc.maxRead)
            if (ndefBytes.isEmpty()) {
                return SimpleNdefResult(
                    ok = false,
                    summaryText = buildString {
                        appendLine("Simple NFC Type 4 NDEF detected")
                        appendLine("Reader: ${session.readerName}")
                        appendLine("Protocol: ${session.protocol}")
                        appendLine("ATR: ${session.atrHex ?: "(none)"}")
                        appendLine("NDEF file is empty.")
                    }
                )
            }

            val record = parseFirstNdefRecord(ndefBytes)

            val baseSummary = buildString {
                appendLine("Simple NFC Type 4 NDEF detected")
                appendLine("Reader: ${session.readerName}")
                appendLine("Protocol: ${session.protocol}")
                appendLine("ATR: ${session.atrHex ?: "(none)"}")
                appendLine("CC version: ${cc.version}")
                appendLine("Max read: ${cc.maxRead}")
                appendLine("Max write: ${cc.maxWrite}")
                appendLine("NDEF file ID: ${cc.ndefFileId.toHexSpaced()}")
                appendLine("NDEF max size: ${cc.ndefMaxSize}")
                appendLine("Read access: 0x${"%02X".format(cc.readAccess)}")
                appendLine("Write access: 0x${"%02X".format(cc.writeAccess)}")
                appendLine("NDEF length: ${ndefBytes.size} bytes")
                appendLine("Record TNF: ${record.tnf} (${tnfName(record.tnf)})")
                appendLine("Record type: ${record.type.ifBlank { "(blank)" }}")
                appendLine("Record ID: ${record.id ?: "(none)"}")
                appendLine("Record payload length: ${record.payload.size} bytes")
            }

            when (record.tnf) {
                0x02 -> {
                    val decoded = decodeMimeRecord(record.type, record.payload)
                    decoded.copy(
                        summaryText = baseSummary + "\n" + decoded.summaryText
                    )
                }

                0x01 -> {
                    // Well-known type, e.g. T / U
                    val payloadText = decodeWellKnownRecord(record)
                    SimpleNdefResult(
                        ok = true,
                        summaryText = baseSummary,
                        payloadText = payloadText,
                        mimeType = null,
                        isGzipIps = false
                    )
                }

                else -> {
                    SimpleNdefResult(
                        ok = true,
                        summaryText = baseSummary,
                        payloadText = record.payload.toDisplayPreview(),
                        mimeType = null,
                        isGzipIps = false
                    )
                }
            }
        } catch (e: Exception) {
            SimpleNdefResult(
                ok = false,
                summaryText = buildString {
                    appendLine("Simple NFC read failed.")
                    appendLine("Reader: ${session.readerName}")
                    appendLine("Protocol: ${session.protocol}")
                    appendLine("ATR: ${session.atrHex ?: "(none)"}")
                    appendLine("Error: ${e.message ?: e::class.simpleName}")
                }
            )
        }
    }

    fun decodeMimeRecord(mimeType: String, payload: ByteArray): SimpleNdefResult {
        return if (mimeType == "application/x.ips.gzip.v1-0") {
            try {
                val jsonText = gunzipToUtf8(payload)
                SimpleNdefResult(
                    ok = true,
                    summaryText = buildString {
                        appendLine("Simple NFC MIME record detected")
                        appendLine("MIME type: $mimeType")
                        appendLine("Encoding: gzip-compressed UTF-8 JSON")
                        appendLine("Decoded successfully.")
                    },
                    payloadText = jsonText,
                    mimeType = mimeType,
                    isGzipIps = true
                )
            } catch (e: Exception) {
                SimpleNdefResult(
                    ok = false,
                    summaryText = buildString {
                        appendLine("Simple NFC MIME record detected")
                        appendLine("MIME type: $mimeType")
                        appendLine("Gzip decode failed: ${e.message}")
                    },
                    payloadText = payload.toHexPreview(),
                    mimeType = mimeType,
                    isGzipIps = true
                )
            }
        } else {
            SimpleNdefResult(
                ok = true,
                summaryText = buildString {
                    appendLine("Simple NFC MIME record detected")
                    appendLine("MIME type: $mimeType")
                    appendLine("Payload length: ${payload.size} bytes")
                },
                payloadText = payload.toDisplayPreview(),
                mimeType = mimeType,
                isGzipIps = false
            )
        }
    }

    private fun selectNdefApplication(session: NfcReader.CardSession) {
        // D2760000850101 = NFC Forum NDEF Tag Application
        val aid = byteArrayOf(
            0xD2.toByte(), 0x76.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x85.toByte(), 0x01.toByte(), 0x01.toByte()
        )
        val apdu = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()
        ) + aid
        val resp = session.transmit(apdu)
        require(resp.sw == 0x9000) {
            "SELECT NDEF application failed: SW=${"%04X".format(resp.sw)}"
        }
    }

    private fun readCcFile(session: NfcReader.CardSession): ByteArray {
        selectFile(session, byteArrayOf(0xE1.toByte(), 0x03.toByte()))
        val cc = readBinary(session, offset = 0, length = 15)
        require(cc.size >= 15) { "Capability Container too short: ${cc.size} bytes" }
        return cc
    }

    private fun parseCapabilityContainer(cc: ByteArray): CcInfo {
        require(cc.size >= 15) { "Capability Container too short: ${cc.size} bytes" }
        require(cc[0] == 0x00.toByte() && cc[1] == 0x0F.toByte()) {
            "Unexpected CC length/header: ${cc.take(2).toByteArray().toHexSpaced()}"
        }
        require(cc[7] == 0x04.toByte()) {
            "CC does not contain NDEF File Control TLV (expected T=0x04, got 0x${"%02X".format(cc[7])})"
        }
        require(cc[8] == 0x06.toByte()) {
            "Unexpected NDEF File Control TLV length: 0x${"%02X".format(cc[8])}"
        }

        val versionMajor = (cc[2].toInt() ushr 4) and 0x0F
        val versionMinor = cc[2].toInt() and 0x0F
        val maxRead = u16(cc, 3)
        val maxWrite = u16(cc, 5)
        val ndefFileId = byteArrayOf(cc[9], cc[10])
        val ndefMaxSize = u16(cc, 11)
        val readAccess = cc[13].toInt() and 0xFF
        val writeAccess = cc[14].toInt() and 0xFF

        return CcInfo(
            version = "$versionMajor.$versionMinor",
            maxRead = maxRead,
            maxWrite = maxWrite,
            ndefFileId = ndefFileId,
            ndefMaxSize = ndefMaxSize,
            readAccess = readAccess,
            writeAccess = writeAccess
        )
    }

    private fun selectFile(session: NfcReader.CardSession, fileId: ByteArray) {
        require(fileId.size == 2) { "ISO file ID must be 2 bytes" }
        val apdu = byteArrayOf(
            0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02
        ) + fileId
        val resp = session.transmit(apdu)
        require(resp.sw == 0x9000) {
            "SELECT FILE ${fileId.toHexSpaced()} failed: SW=${"%04X".format(resp.sw)}"
        }
    }

    private fun readNdefFile(session: NfcReader.CardSession, maxReadFromCc: Int): ByteArray {
        val safeChunk = maxReadFromCc.coerceIn(1, 250)

        val nlenBytes = readBinary(session, offset = 0, length = 2)
        require(nlenBytes.size == 2) { "Could not read NLEN" }

        val nlen = ((nlenBytes[0].toInt() and 0xFF) shl 8) or (nlenBytes[1].toInt() and 0xFF)
        if (nlen == 0) return ByteArray(0)

        val full = ByteArray(nlen)
        var copied = 0
        var offset = 2

        while (copied < nlen) {
            val chunkLen = minOf(safeChunk, nlen - copied)
            val chunk = readBinary(session, offset = offset, length = chunkLen)
            require(chunk.size == chunkLen) {
                "Short READ BINARY at offset $offset: expected $chunkLen got ${chunk.size}"
            }
            System.arraycopy(chunk, 0, full, copied, chunkLen)
            copied += chunkLen
            offset += chunkLen
        }

        return full
    }

    private fun readBinary(session: NfcReader.CardSession, offset: Int, length: Int): ByteArray {
        require(offset in 0..0xFFFF) { "Offset out of range: $offset" }
        require(length in 1..255) { "Length out of range for short APDU: $length" }

        val apdu = byteArrayOf(
            0x00,
            0xB0.toByte(),
            ((offset shr 8) and 0xFF).toByte(),
            (offset and 0xFF).toByte(),
            (length and 0xFF).toByte()
        )
        val resp = session.transmit(apdu)
        require(resp.sw == 0x9000) {
            "READ BINARY failed at offset=$offset len=$length: SW=${"%04X".format(resp.sw)}"
        }
        return resp.data
    }

    private fun parseFirstNdefRecord(bytes: ByteArray): NdefRecordInfo {
        require(bytes.isNotEmpty()) { "NDEF message is empty" }

        var pos = 0
        val header = bytes[pos++].toInt() and 0xFF

        val mb = (header and 0x80) != 0
        val me = (header and 0x40) != 0
        val cf = (header and 0x20) != 0
        val sr = (header and 0x10) != 0
        val il = (header and 0x08) != 0
        val tnf = header and 0x07

        require(!cf) { "Chunked NDEF records are not supported" }

        require(pos < bytes.size) { "Truncated NDEF record (missing type length)" }
        val typeLength = bytes[pos++].toInt() and 0xFF

        val payloadLength = if (sr) {
            require(pos < bytes.size) { "Truncated NDEF record (missing short payload length)" }
            bytes[pos++].toInt() and 0xFF
        } else {
            require(pos + 3 < bytes.size) { "Truncated NDEF record (missing payload length)" }
            val value =
                ((bytes[pos].toInt() and 0xFF) shl 24) or
                        ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                        ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                        (bytes[pos + 3].toInt() and 0xFF)
            pos += 4
            value
        }

        val idLength = if (il) {
            require(pos < bytes.size) { "Truncated NDEF record (missing ID length)" }
            bytes[pos++].toInt() and 0xFF
        } else {
            0
        }

        require(pos + typeLength <= bytes.size) { "Truncated NDEF record (type)" }
        val typeBytes = bytes.copyOfRange(pos, pos + typeLength)
        pos += typeLength

        val idBytes = if (il) {
            require(pos + idLength <= bytes.size) { "Truncated NDEF record (id)" }
            bytes.copyOfRange(pos, pos + idLength).also { pos += idLength }
        } else {
            null
        }

        require(pos + payloadLength <= bytes.size) { "Truncated NDEF record (payload)" }
        val payload = bytes.copyOfRange(pos, pos + payloadLength)

        val type = typeBytes.toString(Charsets.US_ASCII)
        val id = idBytes?.toString(Charsets.US_ASCII)

        return NdefRecordInfo(
            tnf = tnf,
            type = type,
            id = id,
            payload = payload,
            mb = mb,
            me = me,
            cf = cf,
            sr = sr,
            il = il
        )
    }

    private fun decodeWellKnownRecord(record: NdefRecordInfo): String {
        return when (record.type) {
            "T" -> decodeTextRecord(record.payload)
            "U" -> decodeUriRecord(record.payload)
            else -> record.payload.toDisplayPreview()
        }
    }

    private fun decodeTextRecord(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val status = payload[0].toInt() and 0xFF
        val isUtf16 = (status and 0x80) != 0
        val langLen = status and 0x3F
        if (payload.size < 1 + langLen) return payload.toDisplayPreview()

        val textBytes = payload.copyOfRange(1 + langLen, payload.size)
        return if (isUtf16) {
            textBytes.toString(Charsets.UTF_16)
        } else {
            textBytes.toString(Charsets.UTF_8)
        }
    }

    private fun decodeUriRecord(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val prefix = when (payload[0].toInt() and 0xFF) {
            0x00 -> ""
            0x01 -> "http://www."
            0x02 -> "https://www."
            0x03 -> "http://"
            0x04 -> "https://"
            0x05 -> "tel:"
            0x06 -> "mailto:"
            0x07 -> "ftp://anonymous:anonymous@"
            0x08 -> "ftp://ftp."
            0x09 -> "ftps://"
            0x0A -> "sftp://"
            0x0B -> "smb://"
            0x0C -> "nfs://"
            0x0D -> "ftp://"
            0x0E -> "dav://"
            0x0F -> "news:"
            0x10 -> "telnet://"
            0x11 -> "imap:"
            0x12 -> "rtsp://"
            0x13 -> "urn:"
            0x14 -> "pop:"
            0x15 -> "sip:"
            0x16 -> "sips:"
            0x17 -> "tftp:"
            0x18 -> "btspp://"
            0x19 -> "btl2cap://"
            0x1A -> "btgoep://"
            0x1B -> "tcpobex://"
            0x1C -> "irdaobex://"
            0x1D -> "file://"
            0x1E -> "urn:epc:id:"
            0x1F -> "urn:epc:tag:"
            0x20 -> "urn:epc:pat:"
            0x21 -> "urn:epc:raw:"
            0x22 -> "urn:epc:"
            0x23 -> "urn:nfc:"
            else -> ""
        }
        return prefix + payload.copyOfRange(1, payload.size).toString(Charsets.UTF_8)
    }

    private fun gunzipToUtf8(bytes: ByteArray): String {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { gis ->
            return gis.readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun tnfName(tnf: Int): String = when (tnf) {
        0x00 -> "Empty"
        0x01 -> "Well-known"
        0x02 -> "MIME media"
        0x03 -> "Absolute URI"
        0x04 -> "External type"
        0x05 -> "Unknown"
        0x06 -> "Unchanged"
        0x07 -> "Reserved"
        else -> "Unknown"
    }

    private fun ByteArray.toHexPreview(maxBytes: Int = 256): String =
        take(maxBytes).joinToString(" ") { "%02X".format(it) } +
                if (size > maxBytes) "\n... (${size - maxBytes} more bytes)" else ""

    private fun ByteArray.toDisplayPreview(maxBytes: Int = 1024): String {
        val slice = take(maxBytes).toByteArray()
        val text = slice.toString(Charsets.UTF_8)
        val mostlyPrintable = text.count { it.code in 32..126 || it == '\n' || it == '\r' || it == '\t' } >
                (text.length * 0.8)

        return if (mostlyPrintable) {
            text + if (size > maxBytes) "\n... (${size - maxBytes} more bytes)" else ""
        } else {
            toHexPreview(maxBytes.coerceAtMost(256))
        }
    }

    private fun ByteArray.toHexSpaced(): String =
        joinToString(" ") { "%02X".format(it) }
}