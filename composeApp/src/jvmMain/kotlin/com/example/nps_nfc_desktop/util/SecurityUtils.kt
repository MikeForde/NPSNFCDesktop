package com.example.nps_nfc_desktop.util

/**
 * Maps UI label → API protect value
 */
fun protectLabelToValue(label: String): Int =
    when (label.uppercase()) {
        "NONE" -> 0
        "LOW" -> 1
        "MEDIUM" -> 2
        "HIGH" -> 3
        else -> 0
    }