package com.maxijones.tracerf

object SignatureEngine {
    private val cameraWords = listOf(
        "hikvision", "dahua", "reolink", "ezviz", "tapo", "imou", "wyze", "arlo",
        "nest cam", "ring", "gopro", "camera", "ipcam", "ip cam", "cam_", "cam-"
    )
    private val trackerWords = listOf("airtag", "smarttag", "tile", "chipolo", "pebblebee", "tracker", "findmy")
    private val wearableWords = listOf("watch", "band", "garmin", "fitbit", "amazfit", "mi band", "galaxy fit")
    private val audioWords = listOf("airpods", "buds", "jbl", "bose", "headphone", "earbud", "sony wh", "sony wf")
    private val phoneWords = listOf("iphone", "galaxy", "pixel", "moto", "motorola", "redmi", "xiaomi", "oneplus", "oppo", "realme")
    private val vehicleWords = listOf("tpms", "obd", "car", "auto", "bmw", "mercedes", "volkswagen", "ford", "toyota", "renault", "peugeot")
    private val droneWords = listOf("dji", "autel", "skydio", "parrot", "drone", "uas", "remote id", "remoteid")
    private val iotWords = listOf("esp32", "esp8266", "shelly", "sonoff", "tuya", "smartlife", "smart life", "homekit")

    fun classify(o: RadioObservation): Classification {
        val text = buildString {
            append(o.name.lowercase())
            append(' ')
            append(o.serviceUuids.joinToString(" ").lowercase())
        }

        if (o.serviceUuids.any { it.contains("fffa", ignoreCase = true) } || droneWords.any { it in text }) {
            return Classification(DeviceCategory.DRONE, manufacturerFromText(text), Confidence.HIGH, "Remote ID/firma de dron")
        }
        if (cameraWords.any { it in text }) {
            return Classification(DeviceCategory.CAMERA, manufacturerFromText(text), Confidence.HIGH, "Nombre/firma compatible con cámara")
        }
        if (trackerWords.any { it in text }) {
            return Classification(DeviceCategory.TRACKER, manufacturerFromText(text), Confidence.HIGH, "Nombre/firma compatible con tracker")
        }
        if (isIBeacon(o)) {
            return Classification(DeviceCategory.BEACON, "Apple/iBeacon compatible", Confidence.MEDIUM, "Trama iBeacon")
        }
        if (vehicleWords.any { it in text }) {
            return Classification(DeviceCategory.VEHICLE, manufacturerFromText(text), Confidence.MEDIUM, "Nombre compatible con vehículo/accesorio")
        }
        if (wearableWords.any { it in text }) {
            return Classification(DeviceCategory.WEARABLE, manufacturerFromText(text), Confidence.HIGH, "Nombre compatible con wearable")
        }
        if (audioWords.any { it in text }) {
            return Classification(DeviceCategory.AUDIO, manufacturerFromText(text), Confidence.HIGH, "Nombre compatible con audio")
        }
        if (phoneWords.any { it in text }) {
            return Classification(DeviceCategory.PHONE, manufacturerFromText(text), Confidence.MEDIUM, "Nombre compatible con teléfono/tablet")
        }
        if (iotWords.any { it in text }) {
            return Classification(DeviceCategory.IOT, manufacturerFromText(text), Confidence.MEDIUM, "Firma IoT")
        }
        if (o.kind == RadioKind.WIFI) {
            return Classification(DeviceCategory.ROUTER, manufacturerFromText(text), Confidence.MEDIUM, "Punto de acceso Wi‑Fi visible")
        }
        if (o.manufacturerId != null) {
            val maker = manufacturerFromId(o.manufacturerId)
            if (maker != null) return Classification(DeviceCategory.UNKNOWN, maker, Confidence.LOW, "Fabricante BLE conocido; tipo no concluyente")
        }
        return Classification(DeviceCategory.UNKNOWN, manufacturerFromText(text), Confidence.LOW, "Sin firma suficiente")
    }

    private fun isIBeacon(o: RadioObservation): Boolean =
        o.manufacturerId == 0x004C && o.manufacturerDataHex.replace(" ", "").uppercase().startsWith("0215")

    private fun manufacturerFromId(id: Int): String? = when (id) {
        0x004C -> "Apple"
        0x0006 -> "Microsoft"
        0x0075 -> "Samsung"
        0x00E0 -> "Google"
        0x0131 -> "Xiaomi"
        else -> null
    }

    private fun manufacturerFromText(text: String): String? = when {
        "apple" in text || "iphone" in text || "airpods" in text || "airtag" in text -> "Apple"
        "samsung" in text || "galaxy" in text || "smarttag" in text -> "Samsung"
        "motorola" in text || "moto" in text -> "Motorola"
        "google" in text || "pixel" in text || "nest" in text -> "Google"
        "xiaomi" in text || "redmi" in text || "mi band" in text -> "Xiaomi"
        "dji" in text -> "DJI"
        "hikvision" in text -> "Hikvision"
        "dahua" in text -> "Dahua"
        "reolink" in text -> "Reolink"
        "tapo" in text -> "TP-Link/Tapo"
        "ezviz" in text -> "EZVIZ"
        "imou" in text -> "Imou"
        "jbl" in text -> "JBL"
        "garmin" in text -> "Garmin"
        else -> null
    }
}
