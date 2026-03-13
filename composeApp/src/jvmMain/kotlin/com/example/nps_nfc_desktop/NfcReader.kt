package com.example.nps_nfc_desktop

import java.security.Provider
import java.security.Security
import javax.smartcardio.*

class NfcReader {

    data class ReaderInfo(
        val name: String
    )

    class CardSession(
        val readerName: String,
        private val card: Card
    ) {
        val protocol: String
            get() = try {
                card.protocol
            } catch (_: Exception) {
                "(unknown)"
            }

        val atrHex: String?
            get() = try {
                card.atr?.bytes?.toHex()
            } catch (_: Exception) {
                null
            }

        fun transmit(apdu: ByteArray): ResponseAPDU {
            return card.basicChannel.transmit(CommandAPDU(apdu))
        }

        fun disconnect(reset: Boolean = false) {
            try {
                card.disconnect(reset)
            } catch (_: Exception) {
            }
        }
    }

    private fun ensurePcscProvider(onStatus: (String) -> Unit) {
        val existing = Security.getProvider("SunPCSC")
        if (existing != null) {
            onStatus("Provider already present: ${existing.name}")
            return
        }

        try {
            val clazz = Class.forName("sun.security.smartcardio.SunPCSC")
            val provider = clazz.getDeclaredConstructor().newInstance() as Provider
            Security.addProvider(provider)
            onStatus("Registered provider: ${provider.name}")
        } catch (e: Exception) {
            onStatus("Could not register SunPCSC: ${e::class.simpleName}: ${e.message}")
        }
    }

    private fun createPcscFactory(onStatus: (String) -> Unit): TerminalFactory? {
        val lib = System.getProperty("sun.security.smartcardio.library")
        onStatus("PC/SC lib: ${lib ?: "(default lookup)"}")
        onStatus("Default factory type: ${TerminalFactory.getDefaultType()}")

        ensurePcscProvider(onStatus)

        return try {
            val factory = TerminalFactory.getInstance("PC/SC", null)
            onStatus("Factory type in use: ${factory.type}, provider: ${factory.provider.name}")
            factory
        } catch (e: Exception) {
            onStatus("PC/SC factory error: ${e::class.simpleName}: ${e.message}")
            null
        }
    }

    fun listReadersWithDiagnostics(onStatus: (String) -> Unit): List<ReaderInfo> {
        val factory = createPcscFactory(onStatus) ?: return emptyList()

        return try {
            val readers = factory.terminals().list()
            onStatus("Reader count: ${readers.size}")
            readers.forEach { onStatus("Reader: ${it.name}") }
            readers.map { ReaderInfo(it.name) }
        } catch (e: Exception) {
            onStatus("List terminals error: ${e::class.simpleName}: ${e.message}")
            emptyList()
        }
    }

    fun <T> withFirstCard(
        onStatus: (String) -> Unit,
        block: (CardSession) -> T
    ): T {
        val factory = createPcscFactory(onStatus)
            ?: error("No PC/SC factory available")

        val terminals = factory.terminals().list()
        require(terminals.isNotEmpty()) { "No PC/SC readers found by smartcardio" }

        val terminal = terminals.first()
        onStatus("Using: ${terminal.name}. Tap a card...")

        while (true) {
            if (!terminal.waitForCardPresent(250)) {
                continue
            }

            var card: Card? = null
            try {
                card = terminal.connect("*")
                val session = CardSession(
                    readerName = terminal.name,
                    card = card
                )
                onStatus("Card connected. Protocol=${session.protocol}, ATR=${session.atrHex ?: "(none)"}")
                val result = block(session)
                onStatus("Operation complete. Remove card...")
                waitForRemoval(terminal)
                return result
            } catch (e: Exception) {
                onStatus("Card operation error: ${e::class.simpleName}: ${e.message}")
                throw e
            } finally {
                try {
                    card?.disconnect(false)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun waitForRemoval(terminal: CardTerminal) {
        while (terminal.isCardPresent) {
            Thread.sleep(80)
        }
    }
}

private fun ByteArray.toHex(): String =
    joinToString("") { "%02X".format(it) }