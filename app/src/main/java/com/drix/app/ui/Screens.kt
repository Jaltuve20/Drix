package com.drix.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drix.app.data.*
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

@Composable
fun DCard(mod: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = Card(
    modifier = mod.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(2.dp)
) { Column(Modifier.padding(16.dp), content = content) }

@Composable
fun Page(title: String, content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
) { Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink); content() }

/* ───────────── INICIO ───────────── */
@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val cfg by DrixPrefs.cfg.collectAsStateWithLifecycle()
    val zones by DrixPrefs.zones.collectAsStateWithLifecycle()
    var brush by remember { mutableStateOf("g") }

    Page("Drix") {
        DCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(if (cfg.enabled) Color(0xFF22C55E) else Color(0xFFEF4444), RoundedCornerShape(50)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (cfg.enabled) "Automatización activa" else "Automatización pausada", fontWeight = FontWeight.Bold)
                    Text("Acepta pedidos que cumplan tus filtros", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(checked = cfg.enabled, onCheckedChange = { on ->
                    if (!on || ctx.ensurePermissions()) DrixPrefs.update { it.copy(enabled = on) }
                }, colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF22C55E)))
            }
        }
        TargetAppCard()
        DCard {
            Text("Zonas GPS", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                listOf("g" to "🟢 Aceptar", "r" to "🔴 Rechazar", "e" to "🧽 Borrar").forEach { (k, l) ->
                    FilterChip(selected = brush == k, onClick = { brush = k }, label = { Text(l) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Yellow))
                }
            }
            ZoneMap(zones, brush)
            Text("Dibuja con el dedo. Sustituye este lienzo por Maps Compose u osmdroid para un mapa real.",
                fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
        }
        DCard {
            Text("Filtros rápidos", fontWeight = FontWeight.Bold)
            Text("Precio mín/km: S/ %.2f".format(cfg.minPerKm), fontSize = 13.sp)
            Slider(cfg.minPerKm, { v -> DrixPrefs.update { it.copy(minPerKm = (v * 10).toInt() / 10f) } }, valueRange = 0.5f..4f)
            Text("Recojo máx: %.1f km".format(cfg.maxPickupKm), fontSize = 13.sp)
            Slider(cfg.maxPickupKm, { v -> DrixPrefs.update { it.copy(maxPickupKm = (v * 10).toInt() / 10f) } }, valueRange = 0.3f..8f)
        }
    }
}

/** Lienzo interactivo: cada arrastre añade círculos verdes/rojos; "Borrar" quita los cercanos. */
@Composable
fun ZoneMap(zones: List<Zone>, brush: String) {
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFFEEF2F6), RoundedCornerShape(12.dp))
            .pointerInput(brush) {
                detectDragGestures { ch, _ ->
                    val p = Offset(ch.position.x / size.width, ch.position.y / size.height)
                    val cur = DrixPrefs.zones.value
                    DrixPrefs.setZones(
                        if (brush == "e") cur.filter { (Offset(it.x, it.y) - p).getDistance() > it.r }
                        else cur + Zone(p.x, p.y, 0.07f, brush == "g")
                    )
                }
            }
    ) {
        val s = size.width
        listOf(.17f, .42f, .67f, .9f).forEach { t ->            // calles
            drawLine(Color.White, Offset(t * s, 0f), Offset(t * s, s), 14f)
            drawLine(Color.White, Offset(0f, t * s), Offset(s, t * s), 14f)
        }
        zones.forEach { z ->
            drawCircle((if (z.accept) Color(0xFF22C55E) else Color(0xFFEF4444)).copy(alpha = .35f), z.r * s, Offset(z.x * s, z.y * s))
        }
        drawCircle(Color.White, 16f, center); drawCircle(Color(0xFF2563EB), 11f, center) // GPS simulado
    }
}

/* ───────────── ESTADÍSTICAS ───────────── */
@Composable
fun StatsScreen() = Page("Estadísticas") {
    val trips = listOf(8, 11, 7, 13, 15, 18, 10); val days = "LMXJVSD"
    DCard {
        Text("Ganancias de la semana", fontSize = 13.sp, color = Color.Gray)
        Text("S/ 842.50", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
    }
    DCard {
        Text("Viajes completados", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().height(170.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            trips.forEachIndexed { i, n ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$n", fontSize = 10.sp, color = Color.Gray)
                    Box(Modifier.width(28.dp).height((n * 6).dp).background(if (n == trips.max()) Yellow else Yellow.copy(.4f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)))
                    Text("${days[i]}", fontSize = 11.sp)
                }
            }
        }
    }
    DCard {
        Text("Rendimiento", fontWeight = FontWeight.Bold)
        Text("92% · Excelente", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF16A34A))
        LinearProgressIndicator(progress = { .92f }, Modifier.fillMaxWidth().padding(top = 8.dp), color = Yellow)
    }
}

/* ───────────── CONFIGURACIÓN ───────────── */
@Composable
fun SettingsScreen() {
    val cfg by DrixPrefs.cfg.collectAsStateWithLifecycle()
    var km by remember(cfg) { mutableStateOf(cfg.minPerKm.toString()) }
    var pk by remember(cfg) { mutableStateOf(cfg.maxPickupKm.toString()) }
    var fr by remember(cfg) { mutableStateOf(cfg.minFare.toString()) }
    var h1 by remember(cfg) { mutableStateOf(cfg.startHour.toString()) }
    var h2 by remember(cfg) { mutableStateOf(cfg.endHour.toString()) }

    Page("Filtros de rentabilidad") {
        DCard {
            Field("Precio mínimo por km (S/)", km) { km = it }
            Spacer(Modifier.height(8.dp)); Field("Distancia máx. de recojo (km)", pk) { pk = it }
            Spacer(Modifier.height(8.dp)); Field("Tarifa mínima por viaje (S/)", fr) { fr = it }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Desde (h)", h1, Modifier.weight(1f)) { h1 = it }
                Field("Hasta (h)", h2, Modifier.weight(1f)) { h2 = it }
            }
        }
        Button({
            DrixPrefs.update {
                it.copy(minPerKm = km.toFloatOrNull() ?: it.minPerKm, maxPickupKm = pk.toFloatOrNull() ?: it.maxPickupKm,
                    minFare = fr.toFloatOrNull() ?: it.minFare, startHour = h1.toIntOrNull()?.coerceIn(0, 23) ?: it.startHour,
                    endHour = h2.toIntOrNull()?.coerceIn(0, 24) ?: it.endHour)
            }
        }, Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(Yellow, Ink)) { Text("Guardar cambios", fontWeight = FontWeight.Bold) }
        DCard { Text("Las zonas delimitadas se editan dibujando sobre el mapa en Inicio.", fontSize = 12.sp, color = Color.Gray) }
    }
}

@Composable
private fun Field(label: String, value: String, mod: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = mod)

/* ───────────── PERFIL Y AYUDA ───────────── */
@Composable
fun ProfileScreen() {
    val ctx = LocalContext.current
    Page("Perfil y ayuda") {
        DCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).background(Yellow, RoundedCornerShape(50)), Alignment.Center) { Text("CM", fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.width(12.dp))
                Column { Text("Carlos Mendoza", fontWeight = FontWeight.Bold); Text("✔ Conductor verificado", fontSize = 12.sp, color = Color(0xFF16A34A)) }
            }
        }
        DCard { Text("Vehículo", fontWeight = FontWeight.Bold); Text("Moto lineal · Placa ABC-123", color = Color.Gray) }
        Button({
            // Reemplaza 51999999999 por el número de soporte (formato internacional sin +)
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/51999999999?text=Hola%20Drix")))
        }, Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(Color(0xFF22C55E), Color.White)) {
            Text("💬 Ayuda por WhatsApp", fontWeight = FontWeight.Bold)
        }
        Text("Drix v1.0.0", fontSize = 12.sp, color = Color.Gray)
    }
}

/* ───────────── SELECTOR DE APP OBJETIVO ───────────── */
data class AppItem(val label: String, val pkg: String)

/** Tarjeta que muestra la app elegida y abre el selector. */
@Composable
fun TargetAppCard() {
    val ctx = LocalContext.current
    val cfg by DrixPrefs.cfg.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val label = remember(cfg.targetPkg) {
        runCatching { ctx.packageManager.getApplicationInfo(cfg.targetPkg, 0).loadLabel(ctx.packageManager).toString() }.getOrNull()
    }
    DCard {
        Text("App objetivo", fontWeight = FontWeight.Bold)
        Text(label ?: "⚠️ No instalada", fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
        Text(cfg.targetPkg, fontSize = 11.sp, color = Color.Gray)
        Button({ open = true }, Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(Yellow, Ink)) { Text("Elegir app instalada", fontWeight = FontWeight.Bold) }
    }
    if (open) AppPickerDialog({ open = false }) { pkg -> DrixPrefs.update { it.copy(targetPkg = pkg) }; open = false }
}

/** Lista las apps con ícono de lanzador, con buscador. */
@Composable
fun AppPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    var q by remember { mutableStateOf("") }
    val apps = remember {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(i, 0)
            .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != ctx.packageName }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
    }
    val shown = apps.filter { it.label.contains(q, true) || it.pkg.contains(q, true) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text("Elige la app a automatizar") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, label = { Text("Buscar (ej. indrive)") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.pkg }) { a ->
                        val icon = remember(a.pkg) { runCatching { pm.getApplicationIcon(a.pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
                        Row(Modifier.fillMaxWidth().clickable { onPick(a.pkg) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            icon?.let { Image(it, null, Modifier.size(40.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Column { Text(a.label, fontWeight = FontWeight.SemiBold); Text(a.pkg, fontSize = 11.sp, color = Color.Gray) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancelar") } }
    )
}
