package com.maxijones.tracerf

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import java.util.Locale

class RadioScanner(
    context: Context,
    private val onObservation: (RadioObservation) -> Unit,
    private val onStatus: (RadioStatus) -> Unit,
) {
    private val appContext = context.applicationContext
    private val btManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var bleScanning = false
    private var wifiScanning = false

    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = emitBle(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::emitBle)
        override fun onScanFailed(errorCode: Int) {
            bleScanning = false
            publishStatus("BLE no pudo iniciar ($errorCode)")
        }
    }

    @SuppressLint("MissingPermission")
    fun startBle() {
        val adapter = btManager.adapter
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || scanner == null) {
            bleScanning = false
            publishStatus("Bluetooth apagado o no disponible")
            return
        }
        if (bleScanning) return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0L)
            .build()
        val filters = listOf(ScanFilter.Builder().build())
        runCatching { scanner.startScan(filters, settings, bleCallback) }
            .onSuccess {
                bleScanning = true
                publishStatus()
            }
            .onFailure {
                bleScanning = false
                publishStatus(it.message ?: "No se pudo iniciar BLE")
            }
    }

    @SuppressLint("MissingPermission")
    fun stopBle() {
        runCatching { btManager.adapter?.bluetoothLeScanner?.stopScan(bleCallback) }
        bleScanning = false
        publishStatus()
    }

    @SuppressLint("MissingPermission")
    fun scanWifi() {
        wifiScanning = true
        val requested = runCatching { wifiManager.startScan() }.getOrDefault(false)
        val results = runCatching { wifiManager.scanResults }.getOrDefault(emptyList())
        val now = System.currentTimeMillis()
        results.forEach { r ->
            val ssid = if (Build.VERSION.SDK_INT >= 33) {
                r.wifiSsid?.toString()?.trim('"').orEmpty()
            } else {
                @Suppress("DEPRECATION") r.SSID.orEmpty()
            }
            val bssid = r.BSSID.orEmpty()
            if (bssid.isBlank()) return@forEach
            onObservation(
                RadioObservation(
                    key = "WIFI:$bssid",
                    kind = RadioKind.WIFI,
                    address = bssid,
                    name = ssid.ifBlank { "SSID oculto" },
                    rssi = r.level,
                    frequencyMhz = r.frequency,
                    channel = channelOf(r.frequency),
                    seenAt = now,
                )
            )
        }
        wifiScanning = requested || results.isNotEmpty()
        publishStatus(if (!requested && results.isEmpty()) "Wi‑Fi limitado por Android o sin resultados" else "")
    }

    @SuppressLint("MissingPermission")
    private fun emitBle(result: ScanResult) {
        val record = result.scanRecord
        val mfg = record?.manufacturerSpecificData
        val manufacturerId = if (mfg != null && mfg.size() > 0) mfg.keyAt(0) else null
        val manufacturerBytes = manufacturerId?.let { mfg?.get(it) }
        val uuids = record?.serviceUuids.orEmpty().map { it.toString().uppercase(Locale.US) }
        val name = sequenceOf(record?.deviceName, result.device?.name)
            .mapNotNull { it?.trim()?.ifBlank { null } }
            .firstOrNull().orEmpty()
        val address = result.device?.address.orEmpty()
        if (address.isBlank()) return
        onObservation(
            RadioObservation(
                key = "BLE:$address",
                kind = RadioKind.BLE,
                address = address,
                name = name.ifBlank { "BLE sin nombre" },
                rssi = result.rssi,
                manufacturerId = manufacturerId,
                manufacturerDataHex = manufacturerBytes.toHex(),
                serviceUuids = uuids,
                rawHex = record?.bytes.toHex(),
            )
        )
    }

    private fun publishStatus(message: String = "") {
        val adapter = btManager.adapter
        onStatus(
            RadioStatus(
                bleAvailable = adapter != null && adapter.isEnabled,
                bleScanning = bleScanning,
                wifiAvailable = wifiManager.isWifiEnabled,
                wifiScanning = wifiScanning,
                message = message,
            )
        )
    }

    private fun channelOf(freq: Int): Int? = when {
        freq == 2484 -> 14
        freq in 2412..2472 -> (freq - 2407) / 5
        freq in 5000..5895 -> (freq - 5000) / 5
        freq in 5955..7115 -> (freq - 5950) / 5
        else -> null
    }

    private fun ByteArray?.toHex(): String = this?.joinToString("") { "%02X".format(it.toInt() and 0xFF) }.orEmpty()
}
