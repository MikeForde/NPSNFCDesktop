package com.example.nps_nfc_desktop

import java.util.concurrent.atomic.AtomicBoolean
import javax.smartcardio.CardTerminal
import javax.smartcardio.CommandAPDU
import javax.smartcardio.TerminalFactory

class NfcReader {

    private val running = AtomicBoolean(false)

    fun listReaders(): List<CardTerminal> =
        TerminalFactory.getDefault().terminals().list()

    /**
     * Blocks waiting for cards and calls onUid each time a card is presented.
     * Call stop() to exit.
     */
    fun runUidLoop(onStatus: (String) -> Unit, onUid: (String) -> Unit) {
        running.set(true)

        val terminals = TerminalFactory.getDefault().terminals()
        val readers = terminals.list()

        if (readers.isEmpty()) {
            onStatus("No PC/SC readers found. Is pcscd running? Is the ACS driver installed?")
            running.set(false)
            return
        }

        onStatus("Readers: " + readers.joinToString { it.name })
        val reader = readers.first()
        onStatus("Using: ${reader.name}. Tap a card...")

        // ACR-style “Get UID” APDU (reader command)
        val getUid = CommandAPDU(byteArrayOf(0xFF.toByte(), 0xCA.toByte(), 0x00, 0x00, 0x00))

        while (running.get()) {
            // Wait for a card
            if (!reader.waitForCardPresent(250)) continue

            try {
                val card = reader.connect("*")
                val channel = card.basicChannel

                val resp = channel.transmit(getUid)
                val data = resp.data

                if (data.isNotEmpty()) {
                    val uidHex = data.joinToString("") { "%02X".format(it) }
                    onUid(uidHex)
                    onStatus("Card read OK (UID $uidHex). Remove card...")
                } else {
                    onStatus("Card present but no UID returned.")
                }

                try { card.disconnect(false) } catch (_: Exception) {}

            } catch (e: Exception) {
                onStatus("Read error: ${e.message}")
            }

            // Wait until removed (avoid repeated reads)
            while (running.get() && reader.isCardPresent) {
                Thread.sleep(80)
            }
        }

        onStatus("Stopped NFC loop.")
    }

    fun stop() {
        running.set(false)
    }
}
