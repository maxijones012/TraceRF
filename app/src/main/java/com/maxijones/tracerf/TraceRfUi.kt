package com.maxijones.tracerf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

private val Cyan = Color(0xFF2BE4FF)
private val Bg = Color(0xFF061019)
private val Panel = Color(0xFF0D1B26)
private val Panel2 = Color(0xFF122634)
private val Muted = Color(0xFF89A8B6)
private val Warn = Color(0xFFFFC857)
private val Scheme = darkColorScheme(primary = Cyan, background = Bg, surface = Panel, onPrimary = Color.Black)

@Composable
fun TraceRfApp(vm: TraceRfViewModel, requestPermissions: () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        val granted by vm.permissionsGranted.collectAsStateWithLifecycle()
        val ui by vm.ui.collectAsStateWithLifecycle()
        val devices by vm.devices.collectAsStateWithLifecycle()
        val baseline by vm.baseline.collectAsStateWithLifecycle()
        val radio by vm.radioStatus.collectAsStateWithLifecycle()
        @Suppress("UNUSED_VARIABLE") val refresh = devices.size + baseline.size
        Box(Modifier.fillMaxSize().background(Bg)) {
            when {
                !granted -> PermissionScreen(requestPermissions)
                ui.selectedKey != null -> devices[ui.selectedKey]?.let { FollowScreen(vm, it, ui) } ?: vm.stopFollowing()
                ui.mode == null -> HomeScreen(vm, radio)
                else -> ModeScreen(vm, ui, radio)
            }
        }
    }
}

@Composable
private fun PermissionScreen(requestPermissions: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("TraceRF", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Cyan)
        Text("Encontrá la señal.", fontSize = 20.sp)
        Spacer(Modifier.height(24.dp))
        Info("Permisos necesarios", "Android exige permisos de Bluetooth y de entorno Wi‑Fi/ubicación para observar señales cercanas. TraceRF no guarda coordenadas ni sesiones.")
        Spacer(Modifier.height(18.dp))
        Button(onClick = requestPermissions, modifier = Modifier.fillMaxWidth()) { Text("HABILITAR ESCANEO") }
    }
}

@Composable
private fun HomeScreen(vm: TraceRfViewModel, radio: RadioStatus) {
    val modes = listOf(
        SearchMode.QUICK, SearchMode.HOME, SearchMode.VEHICLE, SearchMode.CAMERA,
        SearchMode.TRACKER, SearchMode.DRONE, SearchMode.UNKNOWN, SearchMode.EXPERT
    )
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("TraceRF", fontSize = 42.sp, fontWeight = FontWeight.Black, color = Cyan)
            Text("Encontrá la señal.", fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))
            RadioStrip(radio)
            Spacer(Modifier.height(10.dp))
            Text("Sin historial · sin nube · datos temporales", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Info("BUSCADOR RF", "Detecta emisiones observables y te ayuda a seguir cambios de intensidad. No intercepta contenido ni se conecta a los equipos.")
        }
        items(modes) { mode ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { vm.startMode(mode) },
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(mode.title.uppercase(), color = Cyan, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(mode.subtitle, color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ModeScreen(vm: TraceRfViewModel, ui: SearchUiState, radio: RadioStatus) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val baseline by vm.baseline.collectAsStateWithLifecycle()
    @Suppress("UNUSED_VARIABLE") val refresh = devices.size + baseline.size
    val mode = ui.mode ?: return
    val list = vm.visibleDevices()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.finishSession() }) { Text("← SALIR") }
                Column(Modifier.weight(1f)) {
                    Text(mode.title, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text(mode.subtitle, color = Muted, fontSize = 12.sp)
                }
            }
            RadioStrip(radio)
        }

        if (mode == SearchMode.CAMERA) item {
            Info("CÁMARAS / IoT", "Busca firmas RF compatibles y desconocidos muy fuertes. Una cámara cableada, apagada o silenciosa puede no aparecer.")
        }
        if (mode == SearchMode.TRACKER) item {
            Info("TRACKERS", "Muestra trackers y beacons reconocibles. Identificadores rotativos o protocolos no visibles pueden impedir la detección.")
        }
        if (mode == SearchMode.DRONE) item {
            Info("DRONES", "Prioriza anuncios BLE Remote ID (UUID FFFA) y nombres compatibles. No todos los drones emiten por BLE.")
        }

        if (mode == SearchMode.HOME || mode == SearchMode.VEHICLE || mode == SearchMode.QUICK || mode == SearchMode.CAMERA || mode == SearchMode.UNKNOWN) {
            item { BaselineCard(vm, ui) }
        }
        if (mode == SearchMode.VEHICLE) item { VehicleZones(vm, ui) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = ui.newOnly, onClick = { vm.setNewOnly(!ui.newOnly) }, label = { Text("Solo nuevos") })
                FilterChip(selected = ui.strongOnly, onClick = { vm.setStrongOnly(!ui.strongOnly) }, label = { Text("Solo fuertes") })
            }
        }
        item { Text(list.size.toString() + " señales · ordenadas por score RF", color = Muted, fontSize = 13.sp) }
        if (list.isEmpty()) item { Info("Sin coincidencias", "Todavía no hay señales que cumplan el filtro. Movete lentamente y mantené el escaneo activo.") }
        items(list, key = { it.key }) { d -> DeviceCard(vm, d, mode) }
        item {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.finishSession() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Panel2, contentColor = Cyan)
            ) { Text("FINALIZAR Y BORRAR SESIÓN") }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun BaselineCard(vm: TraceRfViewModel, ui: SearchUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel2), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("REFERENCIA DEL ENTORNO", color = Cyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (ui.baselineActive) {
                Text("Aprendiendo entorno… " + ui.baselineSecondsLeft + "s")
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { ((20 - ui.baselineSecondsLeft) / 20f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            } else if (ui.baselineReady) {
                Text("Referencia lista. 'Solo nuevos' oculta lo que ya estaba presente.", color = Muted)
                TextButton(onClick = { vm.startBaseline(20) }) { Text("REPETIR REFERENCIA") }
            } else {
                Text("Aprendé el entorno antes de entrar o acercarte.", color = Muted)
                Button(onClick = { vm.startBaseline(20) }) { Text("APRENDER ENTORNO · 20s") }
            }
        }
    }
}

@Composable
private fun VehicleZones(vm: TraceRfViewModel, ui: SearchUiState) {
    val zones = listOf("Entorno", "Frente", "Conductor", "Acompañante", "Habitáculo", "Baúl")
    Column {
        Text("SECTORES DEL VEHÍCULO", color = Cyan, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            zones.forEach { zone ->
                FilterChip(
                    selected = ui.zoneSnapshots.containsKey(zone),
                    onClick = { vm.captureVehicleZone(zone) },
                    label = { Text(zone + if (ui.zoneSnapshots.containsKey(zone)) " ✓" else "") }
                )
            }
        }
        Text("Capturá cada sector mientras estás junto a él. La comparación dura solo esta sesión.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun DeviceCard(vm: TraceRfViewModel, d: DeviceReading, mode: SearchMode) {
    val delta = vm.baselineDelta(d)
    val best = if (mode == SearchMode.VEHICLE) vm.bestZoneFor(d.key) else null
    Card(
        modifier = Modifier.fillMaxWidth().clickable { vm.follow(d.key) },
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(d.classification.category.label + " · " + d.kind.name, color = Muted, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(d.emaRssi.roundToInt().toString() + " dBm", color = Cyan, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(d.trend().symbol + " " + d.trend().label, color = Muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { ProximityEngine.progress(d.emaRssi) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("Score " + vm.score(d) + "/100 · Estabilidad " + d.stability().label, fontSize = 12.sp)
            if (delta != null) Text("Cambio vs referencia: " + (if (delta >= 0) "+" else "") + delta.roundToInt() + " dB", color = if (delta >= 8) Warn else Muted, fontSize = 12.sp)
            if (d.classification.manufacturer != null) Text("Fabricante probable: " + d.classification.manufacturer + " · Confianza " + d.classification.confidence.label, color = Muted, fontSize = 12.sp)
            if (best != null) Text("Mayor intensidad capturada: " + best.first + " (" + best.second.roundToInt() + " dBm)", color = Warn, fontSize = 12.sp)
            if (mode == SearchMode.EXPERT) ExpertDetails(d)
            Row {
                TextButton(onClick = { vm.follow(d.key) }) { Text("SEGUIR") }
                TextButton(onClick = { vm.ignore(d.key) }) { Text("IGNORAR") }
            }
        }
    }
}

@Composable
private fun ExpertDetails(d: DeviceReading) {
    Spacer(Modifier.height(6.dp))
    Text("ID: " + d.address, color = Muted, fontSize = 11.sp)
    if (d.frequencyMhz != null) Text("Frecuencia: " + d.frequencyMhz + " MHz · Canal " + (d.channel ?: "—"), color = Muted, fontSize = 11.sp)
    if (d.manufacturerId != null) Text("Manufacturer ID: 0x" + d.manufacturerId.toString(16).uppercase(), color = Muted, fontSize = 11.sp)
    if (d.serviceUuids.isNotEmpty()) Text("UUID: " + d.serviceUuids.joinToString(), color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (d.manufacturerDataHex.isNotBlank()) Text("MFG: " + d.manufacturerDataHex.take(80), color = Muted, fontSize = 11.sp)
    if (d.rawHex.isNotBlank()) Text("RAW: " + d.rawHex.take(120), color = Muted, fontSize = 11.sp)
}

@Composable
private fun FollowScreen(vm: TraceRfViewModel, d: DeviceReading, ui: SearchUiState) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.stopFollowing() }) { Text("← VOLVER") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.ignore(d.key) }) { Text("IGNORAR") }
        }
        Spacer(Modifier.height(20.dp))
        Text("SEGUIR SEÑAL", color = Cyan, fontWeight = FontWeight.Bold)
        Text(d.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(d.classification.category.label, color = Muted)
        Spacer(Modifier.height(35.dp))
        Text(d.emaRssi.roundToInt().toString(), fontSize = 84.sp, fontWeight = FontWeight.Black, color = Cyan)
        Text("dBm", fontSize = 22.sp, color = Muted)
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(progress = { ProximityEngine.progress(d.emaRssi) }, modifier = Modifier.fillMaxWidth().height(14.dp))
        Spacer(Modifier.height(16.dp))
        Text(ProximityEngine.strengthLabel(d.emaRssi), fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(d.trend().symbol + " " + d.trend().label + " · estabilidad " + d.stability().label, color = Muted)
        Spacer(Modifier.height(28.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel2), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Toggle("Vibración de proximidad", ui.vibrationEnabled) { vm.setVibration(it) }
                Toggle("Sonido de proximidad", ui.soundEnabled) { vm.setSound(it) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("RSSI indica intensidad, no metros ni dirección exacta. Buscá cambios repetibles mientras te movés lentamente.", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = change)
    }
}

@Composable
private fun RadioStrip(radio: RadioStatus) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Status("BLE", radio.bleScanning)
        Status("Wi‑Fi", radio.wifiAvailable)
        if (radio.message.isNotBlank()) Text(radio.message, color = Warn, fontSize = 11.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Status(label: String, active: Boolean) {
    Text(label + if (active) " ON" else " OFF", color = if (active) Cyan else Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Info(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel2), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Cyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = Muted, fontSize = 13.sp)
        }
    }
}
