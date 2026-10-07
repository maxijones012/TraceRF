package com.maxijones.tracerf

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TraceRfViewModel(app: Application) : AndroidViewModel(app) {
    private val scanner = RadioScanner(app, ::onObservation, ::onRadioStatus)
    private val feedback = FeedbackEngine(app)

    private val _devices = MutableStateFlow<Map<String, DeviceReading>>(emptyMap())
    val devices: StateFlow<Map<String, DeviceReading>> = _devices.asStateFlow()

    private val _baseline = MutableStateFlow<Map<String, BaselineValue>>(emptyMap())
    val baseline: StateFlow<Map<String, BaselineValue>> = _baseline.asStateFlow()

    private val _radioStatus = MutableStateFlow(RadioStatus())
    val radioStatus: StateFlow<RadioStatus> = _radioStatus.asStateFlow()

    private val _ui = MutableStateFlow(SearchUiState())
    val ui: StateFlow<SearchUiState> = _ui.asStateFlow()

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    private val ignored = mutableSetOf<String>()
    private val baselineSamples = linkedMapOf<String, MutableList<Int>>()
    private var scanLoop: Job? = null
    private var baselineJob: Job? = null

    init {
        refreshPermissions()
        if (_permissionsGranted.value) startScanning()
    }

    fun refreshPermissions() {
        val c = getApplication<Application>()
        _permissionsGranted.value = requiredPermissions().all {
            ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED
        }
        if (_permissionsGranted.value) startScanning()
    }

    fun requiredPermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }.toTypedArray()

    fun startMode(mode: SearchMode) {
        clearSessionInternal()
        _ui.value = SearchUiState(mode = mode)
        if (mode == SearchMode.HOME || mode == SearchMode.VEHICLE) startBaseline(20)
    }

    fun finishSession() {
        clearSessionInternal()
        _ui.value = SearchUiState()
    }

    private fun clearSessionInternal() {
        baselineJob?.cancel()
        ignored.clear()
        synchronized(baselineSamples) { baselineSamples.clear() }
        _devices.value = emptyMap()
        _baseline.value = emptyMap()
    }

    fun startBaseline(seconds: Int = 20) {
        baselineJob?.cancel()
        synchronized(baselineSamples) { baselineSamples.clear() }
        _baseline.value = emptyMap()
        _ui.update { it.copy(baselineActive = true, baselineReady = false, baselineSecondsLeft = seconds) }
        baselineJob = viewModelScope.launch {
            for (left in seconds downTo 1) {
                _ui.update { it.copy(baselineSecondsLeft = left) }
                delay(1_000L)
            }
            finishBaseline()
        }
    }

    private fun finishBaseline() {
        val snap = synchronized(baselineSamples) {
            baselineSamples.mapValues { (_, values) -> BaselineValue(values.average(), values.size) }
        }
        _baseline.value = snap
        _ui.update { it.copy(baselineActive = false, baselineReady = true, baselineSecondsLeft = 0) }
    }

    fun setNewOnly(on: Boolean) = _ui.update { it.copy(newOnly = on) }
    fun setStrongOnly(on: Boolean) = _ui.update { it.copy(strongOnly = on) }
    fun setVibration(on: Boolean) = _ui.update { it.copy(vibrationEnabled = on) }
    fun setSound(on: Boolean) = _ui.update { it.copy(soundEnabled = on) }

    fun follow(key: String) {
        _ui.update { it.copy(selectedKey = key) }
    }

    fun stopFollowing() {
        _ui.update { it.copy(selectedKey = null) }
    }

    fun ignore(key: String) {
        ignored += key
        _devices.update { it - key }
        if (_ui.value.selectedKey == key) stopFollowing()
    }

    fun captureVehicleZone(zone: String) {
        val snapshot = _devices.value.mapValues { it.value.emaRssi }
        _ui.update { it.copy(zoneSnapshots = it.zoneSnapshots + (zone to snapshot)) }
    }

    fun bestZoneFor(key: String): Pair<String, Double>? =
        _ui.value.zoneSnapshots
            .mapNotNull { (zone, values) -> values[key]?.let { zone to it } }
            .maxByOrNull { it.second }

    fun baselineDelta(device: DeviceReading): Double? =
        _baseline.value[device.key]?.let { device.emaRssi - it.averageRssi }

    fun score(device: DeviceReading): Int =
        ProximityEngine.score(device, _baseline.value[device.key])

    fun visibleDevices(): List<DeviceReading> {
        val state = _ui.value
        val base = _baseline.value
        return _devices.value.values
            .asSequence()
            .filter { it.key !in ignored }
            .filter { !state.newOnly || (state.baselineReady && it.key !in base) }
            .filter { !state.strongOnly || it.emaRssi >= -65.0 }
            .filter { modeAllows(state.mode, it) }
            .sortedWith(compareByDescending<DeviceReading> { score(it) }.thenByDescending { it.emaRssi })
            .toList()
    }

    private fun modeAllows(mode: SearchMode?, d: DeviceReading): Boolean = when (mode) {
        SearchMode.CAMERA ->
            d.classification.category in setOf(DeviceCategory.CAMERA, DeviceCategory.IOT) ||
                (d.classification.category == DeviceCategory.UNKNOWN && d.emaRssi >= -60)
        SearchMode.TRACKER ->
            d.classification.category in setOf(DeviceCategory.TRACKER, DeviceCategory.BEACON)
        SearchMode.DRONE ->
            d.classification.category == DeviceCategory.DRONE
        SearchMode.UNKNOWN ->
            d.classification.category == DeviceCategory.UNKNOWN
        SearchMode.VEHICLE ->
            d.classification.category != DeviceCategory.ROUTER || d.emaRssi >= -58
        else -> true
    }

    private fun startScanning() {
        if (scanLoop?.isActive == true) return
        scanner.startBle()
        scanLoop = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                scanner.scanWifi()
                delay(12_000L)
            }
        }
    }

    private fun onRadioStatus(status: RadioStatus) {
        _radioStatus.value = status
    }

    private fun onObservation(o: RadioObservation) {
        if (o.key in ignored) return
        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (_ui.value.baselineActive) {
                synchronized(baselineSamples) {
                    val list = baselineSamples.getOrPut(o.key) { mutableListOf() }
                    if (list.size < 100) list += o.rssi
                }
            }
            _devices.update { map ->
                val old = map[o.key]
                val history = ((old?.history ?: emptyList()) + o.rssi).takeLast(14)
                val ema = if (old == null) o.rssi.toDouble() else (0.38 * o.rssi) + (0.62 * old.emaRssi)
                val reading = DeviceReading(
                    key = o.key,
                    kind = o.kind,
                    address = o.address,
                    name = o.name,
                    classification = SignatureEngine.classify(o),
                    currentRssi = o.rssi,
                    emaRssi = ema,
                    history = history,
                    firstSeen = old?.firstSeen ?: o.seenAt,
                    lastSeen = o.seenAt,
                    hitCount = (old?.hitCount ?: 0) + 1,
                    frequencyMhz = o.frequencyMhz ?: old?.frequencyMhz,
                    channel = o.channel ?: old?.channel,
                    manufacturerId = o.manufacturerId ?: old?.manufacturerId,
                    manufacturerDataHex = o.manufacturerDataHex.ifBlank { old?.manufacturerDataHex.orEmpty() },
                    serviceUuids = if (o.serviceUuids.isNotEmpty()) o.serviceUuids else old?.serviceUuids.orEmpty(),
                    rawHex = o.rawHex.ifBlank { old?.rawHex.orEmpty() },
                )
                map + (o.key to reading)
            }
            val selected = _ui.value.selectedKey
            if (selected == o.key) {
                val rssi = _devices.value[selected]?.emaRssi ?: o.rssi.toDouble()
                feedback.pulse(rssi, _ui.value.vibrationEnabled, _ui.value.soundEnabled)
            }
        }
    }

    override fun onCleared() {
        scanLoop?.cancel()
        baselineJob?.cancel()
        scanner.stopBle()
        feedback.release()
        super.onCleared()
    }
}
