package com.example.nps_nfc_desktop.nfc

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class NdefMimeRecord(
    val mimeType: String,
    val payload: ByteArray,
    val shortRecord: Boolean
)

data class ParsedNdefPayload(
    val mimeType: String,
    val compressedPayload: ByteArray,
    val decompressedText: String?,
    val ndefMessageHex: String
)

object NdefCodec {

    private const val TNF_MIME = 0x02
    private const val FLAG_MB = 0x80
    private const val FLAG_ME = 0x40
    private const val FLAG_SR = 0x10

    fun buildSingleMimeMessage(
        mimeType: String,
        payload: ByteArray
    ): ByteArray {
        val typeBytes = mimeType.toByteArray(StandardCharsets.US_ASCII)
        val shortRecord = payload.size < 256

        val out = ByteArrayOutputStream()
        var header = FLAG_MB or FLAG_ME or TNF_MIME
        if (shortRecord) {
            header = header or FLAG_SR
        }

        out.write(header)
        out.write(typeBytes.size)

        if (shortRecord) {
            out.write(payload.size)
        } else {
            out.write((payload.size ushr 24) and 0xFF)
            out.write((payload.size ushr 16) and 0xFF)
            out.write((payload.size ushr 8) and 0xFF)
            out.write(payload.size and 0xFF)
        }

        out.write(typeBytes)
        out.write(payload)
        return out.toByteArray()
    }

    fun wrapAsType4NdefFile(message: ByteArray): ByteArray {
        require(message.size <= 0xFFFF) { "NDEF message too large for 2-byte NLEN" }
        return byteArrayOf(
            ((message.size ushr 8) and 0xFF).toByte(),
            (message.size and 0xFF).toByte()
        ) + message
    }

    fun parseType4NdefFile(fileBytes: ByteArray): ParsedNdefPayload {
        require(fileBytes.size >= 2) { "NDEF file too short for NLEN" }

        val nlen = ((fileBytes[0].toInt() and 0xFF) shl 8) or (fileBytes[1].toInt() and 0xFF)
        require(fileBytes.size >= 2 + nlen) {
            "NDEF file truncated: NLEN=$nlen, bytesAvailable=${fileBytes.size - 2}"
        }

        val message = fileBytes.copyOfRange(2, 2 + nlen)
        val record = parseSingleMimeMessage(message)

        val decompressedText = if (record.mimeType.endsWith(".gzip.v1-0")) {
            ungzipToUtf8(record.payload)
        } else {
            null
        }

        return ParsedNdefPayload(
            mimeType = record.mimeType,
            compressedPayload = record.payload,
            decompressedText = decompressedText,
            ndefMessageHex = message.toHex()
        )
    }

    fun parseSingleMimeMessage(message: ByteArray): NdefMimeRecord {
        require(message.isNotEmpty()) { "Empty NDEF message" }

        val header = message[0].toInt() and 0xFF
        val mb = header and FLAG_MB != 0
        val me = header and FLAG_ME != 0
        val sr = header and FLAG_SR != 0
        val tnf = header and 0x07

        require(mb && me) { "Only single-record NDEF messages are supported" }
        require(tnf == TNF_MIME) { "Expected MIME NDEF record (TNF=0x02), got TNF=$tnf" }

        var index = 1
        val typeLength = message[index].toInt() and 0xFF
        index += 1

        val payloadLength = if (sr) {
            val len = message[index].toInt() and 0xFF
            index += 1
            len
        } else {
            val len = ((message[index].toInt() and 0xFF) shl 24) or
                    ((message[index + 1].toInt() and 0xFF) shl 16) or
                    ((message[index + 2].toInt() and 0xFF) shl 8) or
                    (message[index + 3].toInt() and 0xFF)
            index += 4
            len
        }

        val typeBytes = message.copyOfRange(index, index + typeLength)
        index += typeLength

        val payload = message.copyOfRange(index, index + payloadLength)
        val mimeType = String(typeBytes, StandardCharsets.US_ASCII)

        return NdefMimeRecord(
            mimeType = mimeType,
            payload = payload,
            shortRecord = sr
        )
    }

    fun gzipUtf8(text: String): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { gzip ->
            gzip.write(text.toByteArray(StandardCharsets.UTF_8))
        }
        return baos.toByteArray()
    }

    fun ungzipToUtf8(bytes: ByteArray): String {
        return GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(StandardCharsets.UTF_8).use {
            it.readText()
        }
    }
}

private fun ByteArray.toHex(): String =
    joinToString("") { "%02X".format(it) }