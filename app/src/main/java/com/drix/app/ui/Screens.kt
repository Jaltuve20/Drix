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
import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

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
            Text("Toca un distrito para marcarlo completo (verde = aceptar, rojo = rechazar; «Borrar» lo quita). Si hay distritos verdes, solo acepta pedidos cuyo recojo esté en ellos.",
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

/** Mapa real (OpenStreetMap). Toca para crear círculos de 150 m; "Borrar" quita el tocado. Requiere internet. */
@Composable
fun ZoneMap(zones: List<Zone>, brush: String) {
    val ctx = LocalContext.current
    val brushNow by rememberUpdatedState(brush)
    val map = remember {
        Configuration.getInstance().apply {
            load(ctx, ctx.getSharedPreferences("osm", 0))
            userAgentValue = ctx.packageName // OSM exige identificar la app
            osmdroidBasePath = File(ctx.cacheDir, "osm"); osmdroidTileCache = File(ctx.cacheDir, "osm/tiles")
        }
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true)
            controller.setZoom(15.0); controller.setCenter(GeoPoint(-12.0464, -77.0428)) // Lima
            // Evita que el scroll de la pantalla robe los gestos del mapa
            setOnTouchListener { v, _ -> v.parent.requestDisallowInterceptTouchEvent(true); false }
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    val d = Districts.at(p.latitude, p.longitude)
                    if (d == null) { Toast.makeText(ctx, "Aquí no hay distrito de Lima/Callao", Toast.LENGTH_SHORT).show(); return true }
                    val cur = DrixPrefs.zones.value.filter { it.name != d.name }
                    val b = brushNow
                    DrixPrefs.setZones(if (b == "e") cur else cur + Zone(d.name, b == "g"))
                    Toast.makeText(ctx, d.name + ": " + when (b) { "g" -> "verde (aceptar)"; "r" -> "rojo (rechazar)"; else -> "sin marcar" }, Toast.LENGTH_SHORT).show()
                    return true
                }
                override fun longPressHelper(p: GeoPoint) = false
            }))
        }
    }
    val me = remember { MyLocationNewOverlay(GpsMyLocationProvider(ctx), map) }
    fun goMe() {
        me.enableMyLocation(); if (!map.overlays.contains(me)) map.overlays.add(me)
        me.runOnFirstFix { map.post { me.myLocation?.let { map.controller.animateTo(it) } } }
        me.myLocation?.let { map.controller.animateTo(it) }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) goMe() }
    DisposableEffect(map) { map.onResume(); onDispose { map.onPause(); me.disableMyLocation() } }

    Column {
        AndroidView(
            factory = { map }, modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(12.dp)),
            update = { m ->
                m.overlays.removeAll { it is Polygon }
                zones.forEach { z ->
                    val d = Districts.all.firstOrNull { it.name == z.name } ?: return@forEach
                    m.overlays.add(Polygon(m).apply {
                        points = Polygon.pointsAsCircle(GeoPoint(d.lat, d.lon), d.r.toDouble())
                        fillPaint.color = (if (z.accept) 0x5922C55E else 0x59EF4444).toInt()
                        outlinePaint.color = (if (z.accept) 0xFF16A34A else 0xFFDC2626).toInt()
                        outlinePaint.strokeWidth = 3f
                        setOnClickListener { _, _, _ -> false } // deja pasar el toque al mapa
                    })
                }
                m.invalidate()
            }
        )
        OutlinedButton({
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) goMe()
            else perm.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }, Modifier.padding(top = 8.dp)) { Text("📍 Mi ubicación") }
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
    var d1 by remember(cfg) { mutableStateOf(cfg.minDelayMs.toString()) }
    var d2 by remember(cfg) { mutableStateOf(cfg.maxDelayMs.toString()) }

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
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Retraso mín (ms)", d1, Modifier.weight(1f)) { d1 = it }
                Field("Retraso máx (ms)", d2, Modifier.weight(1f)) { d2 = it }
            }
            Text("Tiempo al azar entre ambos antes de pulsar Aceptar. Los pedidos caducan en segundos: no lo subas demasiado.",
                fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
        }
        Button({
            val lo = (d1.toIntOrNull() ?: cfg.minDelayMs).coerceIn(0, 5000)
            val hi = (d2.toIntOrNull() ?: cfg.maxDelayMs).coerceIn(lo, 5000)
            DrixPrefs.update {
                it.copy(minPerKm = km.toFloatOrNull() ?: it.minPerKm, maxPickupKm = pk.toFloatOrNull() ?: it.maxPickupKm,
                    minFare = fr.toFloatOrNull() ?: it.minFare, startHour = h1.toIntOrNull()?.coerceIn(0, 23) ?: it.startHour,
                    endHour = h2.toIntOrNull()?.coerceIn(0, 24) ?: it.endHour,
                    minDelayMs = lo, maxDelayMs = hi)
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
