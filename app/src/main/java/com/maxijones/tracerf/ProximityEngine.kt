package com.maxijones.tracerf

import kotlin.math.roundToInt

object ProximityEngine {
    fun score(device: DeviceReading, baseline: BaselineValue?): Int {
        val strength = ((device.emaRssi + 100.0) * 1.55).roundToInt().coerceIn(0, 82)
        val stability = when (device.stability()) {
            Stability.HIGH -> 8
            Stability.MEDIUM -> 4
            Stability.LOW -> 0
        }
        val trend = when (device.trend()) {
            Trend.RISING -> 5
            Trend.STABLE -> 2
            Trend.FALLING -> 0
        }
        val delta = baseline?.let { (device.emaRssi - it.averageRssi).roundToInt().coerceIn(0, 15) } ?: 5
        return (strength + stability + trend + delta).coerceIn(0, 100)
    }

    fun strengthLabel(rssi: Double): String = when {
        rssi >= -48 -> "MUY FUERTE"
        rssi >= -60 -> "FUERTE"
        rssi >= -72 -> "MODERADA"
        else -> "DÉBIL"
    }

    fun progress(rssi: Double): Float = ((rssi + 100.0) / 60.0).toFloat().coerceIn(0f, 1f)
}
