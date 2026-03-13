package com.example.nps_nfc_desktop

import javax.smartcardio.ResponseAPDU

data class NativeDesfireResponse(
    val data: ByteArray,
    val sw: Int
) {
    val ok: Boolean get() = (sw and 0xFF) == 0x00
    val statusByte: Int get() = sw and 0xFF
}

class DesfireNative(
    private val session: NfcReader.CardSession
) {
    companion object {
        private const val INS_SELECT_APPLICATION = 0x5A
        private const val INS_READ_DATA = 0xBD
    }

    fun selectApplication(aidMsb: ByteArray): NativeDesfireResponse {
        require(aidMsb.size == 3) { "AID must be 3 bytes" }

        // DESFire native expects AID LSB-first in the command body
        val aidLsb = byteArrayOf(aidMsb[2], aidMsb[1], aidMsb[0])
        return transmitNative(INS_SELECT_APPLICATION, aidLsb)
    }

    fun readData(fileNo: Int, offset: Int, length: Int): NativeDesfireResponse {
        require(fileNo in 0..255)
        require(offset >= 0)
        require(length >= 0)

        val body = byteArrayOf(
            fileNo.toByte(),
            (offset and 0xFF).toByte(),
            ((offset shr 8) and 0xFF).toByte(),
            ((offset shr 16) and 0xFF).toByte(),
            (length and 0xFF).toByte(),
            ((length shr 8) and 0xFF).toByte(),
            ((length shr 16) and 0xFF).toByte()
        )

        return transmitNative(INS_READ_DATA, body)
    }

    private fun transmitNative(ins: Int, data: ByteArray = ByteArray(0)): NativeDesfireResponse {
        val apdu = ByteArray(6 + data.size)
        var i = 0
        apdu[i++] = 0x90.toByte()
        apdu[i++] = ins.toByte()
        apdu[i++] = 0x00
        apdu[i++] = 0x00
        apdu[i++] = data.size.toByte()
        if (data.isNotEmpty()) {
            System.arraycopy(data, 0, apdu, i, data.size)
            i += data.size
        }
        apdu[i] = 0x00

        val response: ResponseAPDU = session.transmit(apdu)
        return NativeDesfireResponse(
            data = response.data,
            sw = response.sw
        )
    }
}