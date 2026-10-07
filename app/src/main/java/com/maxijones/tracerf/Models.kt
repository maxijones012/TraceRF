package com.maxijones.tracerf

import kotlin.math.sqrt

enum class RadioKind { BLE, WIFI }

enum class SearchMode(val title: String, val subtitle: String) {
    QUICK("Escaneo rápido", "Todo lo relevante, ordenado por proximidad"),
    HOME("Domicilio", "Referencia exterior + búsqueda interior"),
    VEHICLE("Vehículo", "Compará ambiente y sectores del vehículo"),
    CAMERA("Cámaras / IoT", "Emisiones compatibles con cámaras y dispositivos IoT"),
    TRACKER("Trackers", "Trackers y beacons BLE reconocibles"),
    DRONE("Drones", "Remote ID BLE y firmas compatibles"),
    UNKNOWN("Desconocidos", "Señales que no pudieron clasificarse"),
    EXPERT("Modo experto", "Datos técnicos sin simplificar"),
}

enum class DeviceCategory(val label: String) {
    PHONE("Teléfono / tablet probable"),
    WEARABLE("Wearable"),
    AUDIO("Audio"),
    TRACKER("Tracker"),
    VEHICLE("Vehículo / TPMS"),
    DRONE("Dron / Remote ID"),
    CAMERA("Cámara"),
    ROUTER("Router / AP"),
    IOT("IoT"),
    BEACON("Beacon"),
    UNKNOWN("Desconocido"),
}

enum class Confidence(val label: String) { HIGH("Alta"), MEDIUM("Media"), LOW("Baja") }
enum class Trend(val label: String, val symbol: String) {
    RISING("Aumentando", "↑"), STABLE("Estable", "→"), FALLING("Disminuyendo", "↓")
}
enum class Stability(val label: String) { HIGH("Alta"), MEDIUM("Media"), LOW("Baja") }

data class RadioObservation(
    val key: String,
    val kind: RadioKind,
    val address: String,
    val name: String,
    val rssi: Int,
    val frequencyMhz: Int? = null,
    val channel: Int? = null,
    val manufacturerId: Int? = null,
    val manufacturerDataHex: String = "",
    val serviceUuids: List<String> = emptyList(),
    val rawHex: String = "",
    val seenAt: Long = System.currentTimeMillis(),
)

data class Classification(
    val category: DeviceCategory,
    val manufacturer: String? = null,
    val confidence: Confidence = Confidence.LOW,
    val reason: String = "",
)

data class DeviceReading(
    val key: String,
    val kind: RadioKind,
    val address: String,
    val name: String,
    val classification: Classification,
    val currentRssi: Int,
    val emaRssi: Double,
    val history: List<Int>,
    val firstSeen: Long,
    val lastSeen: Long,
    val hitCount: Int,
    val frequencyMhz: Int? = null,
    val channel: Int? = null,
    val manufacturerId: Int? = null,
    val manufacturerDataHex: String = "",
    val serviceUuids: List<String> = emptyList(),
    val rawHex: String = "",
) {
    fun trend(): Trend {
        if (history.size < 6) return Trend.STABLE
        val old = history.takeLast(6).take(3).average()
        val recent = history.takeLast(3).average()
        return when {
            recent - old >= 4.0 -> Trend.RISING
            old - recent >= 4.0 -> Trend.FALLING
            else -> Trend.STABLE
        }
    }

    fun stability(): Stability {
        if (history.size < 3) return Stability.LOW
        val h = history.takeLast(8)
        val mean = h.average()
        val sd = sqrt(h.sumOf { (it - mean) * (it - mean) } / h.size)
        return when {
            sd <= 3.5 -> Stability.HIGH
            sd <= 7.0 -> Stability.MEDIUM
            else -> Stability.LOW
        }
    }
}

data class BaselineValue(val averageRssi: Double, val samples: Int)

data class RadioStatus(
    val bleAvailable: Boolean = false,
    val bleScanning: Boolean = false,
    val wifiAvailable: Boolean = false,
    val wifiScanning: Boolean = false,
    val message: String = "",
)

data class SearchUiState(
    val mode: SearchMode? = null,
    val baselineActive: Boolean = false,
    val baselineSecondsLeft: Int = 0,
    val baselineReady: Boolean = false,
    val newOnly: Boolean = false,
    val strongOnly: Boolean = false,
    val selectedKey: String? = null,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val zoneSnapshots: Map<String, Map<String, Double>> = emptyMap(),
)
