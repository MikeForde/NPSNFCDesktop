package com.example.nps_nfc_desktop.model

enum class ObservationEntryType(
    val label: String,
    val display: String,
    val snomedCode: String,
    val unit: String,
    val ucumCode: String,
    val isBloodPressure: Boolean = false
) {
    BLOOD_PRESSURE(
        label = "Blood Pressure",
        display = "Blood Pressure",
        snomedCode = "",
        unit = "mm[Hg]",
        ucumCode = "mm[Hg]",
        isBloodPressure = true
    ),
    RESPIRATION_RATE(
        label = "Respiration rate",
        display = "Respiration rate",
        snomedCode = "86290005",
        unit = "rpm",
        ucumCode = "rpm"
    ),
    WEIGHT(
        label = "Weight",
        display = "Weight",
        snomedCode = "27113001",
        unit = "kg",
        ucumCode = "kg"
    ),
    BODY_TEMPERATURE(
        label = "Body temperature",
        display = "Body temperature",
        snomedCode = "386725007",
        unit = "cel",
        ucumCode = "Cel"
    ),
    PULSE_RATE(
        label = "Pulse rate",
        display = "Pulse rate",
        snomedCode = "78564009",
        unit = "bpm",
        ucumCode = "/min"
    ),
    BLOOD_OXYGEN_SATURATION(
        label = "Blood oxygen saturation",
        display = "Blood oxygen saturation",
        snomedCode = "103228002",
        unit = "%",
        ucumCode = "%"
    )
}