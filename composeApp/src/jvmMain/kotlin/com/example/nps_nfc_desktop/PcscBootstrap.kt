package com.example.nps_nfc_desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object PcscBootstrap {

    fun configure() {
        val existing = System.getProperty("sun.security.smartcardio.library")
        if (!existing.isNullOrBlank()) {
            println("PcscBootstrap: already set to $existing")
            return
        }

        val found = findPcscLiteLibrary()

        if (found != null) {
            System.setProperty("sun.security.smartcardio.library", found)
            println("PcscBootstrap: set sun.security.smartcardio.library=$found")
        } else {
            println("PcscBootstrap: no libpcsclite candidate found")
        }
    }

    private fun findPcscLiteLibrary(): String? {
        findViaLdconfig()?.let { return it }

        val candidates = listOf(
            "/usr/lib/x86_64-linux-gnu/libpcsclite.so.1",
            "/usr/lib/x86_64-linux-gnu/libpcsclite.so.1.0.0",
            "/lib/x86_64-linux-gnu/libpcsclite.so.1",
            "/lib/x86_64-linux-gnu/libpcsclite.so.1.0.0",
            "/usr/lib64/libpcsclite.so.1",
            "/usr/lib64/libpcsclite.so",
            "/usr/local/lib64/libpcsclite.so.1",
            "/usr/local/lib64/libpcsclite.so"
        )

        println("PcscBootstrap: checking fallback paths...")
        for (candidate in candidates) {
            val exists = Files.exists(Path.of(candidate))
            val fileExists = File(candidate).exists()
            println("PcscBootstrap: candidate=$candidate  Files.exists=$exists  File.exists=$fileExists")
            if (exists || fileExists) {
                return candidate
            }
        }

        return null
    }

    private fun findViaLdconfig(): String? {
        return try {
            val process = ProcessBuilder("ldconfig", "-p")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exit = process.waitFor()

            println("PcscBootstrap: ldconfig exit=$exit")
            println("PcscBootstrap: ldconfig raw output:")
            println(output)

            val matchingLine = output.lineSequence()
                .map { it.trim() }
                .firstOrNull { line ->
                    line.contains("libpcsclite.so.1") || line.contains("libpcsclite.so")
                }

            println("PcscBootstrap: ldconfig matching line=$matchingLine")

            val parsedPath = matchingLine
                ?.substringAfter("=>", "")
                ?.trim()

            println("PcscBootstrap: ldconfig parsed path=$parsedPath")

            if (!parsedPath.isNullOrBlank()) {
                val exists = File(parsedPath).exists()
                println("PcscBootstrap: parsed path exists=$exists")
                if (exists) {
                    return parsedPath
                }
            }

            null
        } catch (e: Exception) {
            println("PcscBootstrap: ldconfig lookup failed: ${e::class.simpleName}: ${e.message}")
            null
        }
    }
}