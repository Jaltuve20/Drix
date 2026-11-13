package com.drix.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drix.app.auth.AuthRepo
import com.drix.app.auth.Profile
import com.drix.app.data.*
import com.drix.app.service.DrixAccessibilityService
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/* ═════════════ COMPONENTES BASE ═════════════ */

@Composable
fun DCard(mod: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = Card(
    modifier = mod.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(1.dp)
) { Column(Modifier.padding(18.dp), content = content) }

/** Pantalla con cabecera naranja degradada (como la app de referencia) y contenido en tarjetas. */
@Composable
fun Screen(hero: @Composable ColumnScope.() -> Unit, body: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Fog).verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Brush.linearGradient(listOf(YellowLight, YellowDeep)))
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 28.dp),
            content = hero
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = body)
    }
}

@Composable
fun HeroTitle(line: String, chip: String? = null, sub: String? = null) {
    Text(line, color = Ink, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold)
    if (chip != null) Surface(color = Ink, shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(top = 6.dp)) {
        Text(chip, color = Yellow, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
    }
    if (sub != null) Text(sub, color = Ink.copy(alpha = .75f), fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 10.dp))
}

@Composable
fun PillButton(text: String, mod: Modifier = Modifier.fillMaxWidth(), color: Color = Yellow, onClick: () -> Unit) =
    Button(onClick, mod.height(52.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(color, if (color == Yellow) Ink else Color.White)) {
        Text(text, fontWeight = FontWeight.Bold)
    }

@Composable
fun LabeledField(label: String, value: String, mod: Modifier = Modifier.fillMaxWidth(), decimal: Boolean = true, keyboard: KeyboardType? = null, onChange: (String) -> Unit) {
    Column(mod) {
        Text(label, fontSize = 14.sp, color = Ink, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value, onChange, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard ?: if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = Yellow, cursorColor = Yellow)
        )
    }
}

@Composable
private fun Tile(label: String, value: String, mod: Modifier = Modifier) = Column(
    mod.background(Fog, RoundedCornerShape(16.dp)).padding(14.dp)
) {
    Text(label, fontSize = 12.sp, color = Muted)
    Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.padding(top = 2.dp))
}

private fun num(v: Float): String = java.math.BigDecimal(v.toString()).stripTrailingZeros().toPlainString()
private fun String.asFloat(): Float? = trim().replace(',', '.').toFloatOrNull()

/* ═════════════ INICIO ═════════════ */

@Composable
fun HomeScreen(onGo: (Int) -> Unit) {
    val ctx = LocalContext.current
    val cfg by DrixPrefs.cfg.collectAsStateWithLifecycle()
    val zones by DrixPrefs.zones.collectAsStateWithLifecycle()

    Screen(hero = {
        Brand(); Spacer(Modifier.height(20.dp))
        HeroTitle("Drix trabaja bajo tus", "reglas")
    }) {
        DCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(if (cfg.enabled) Go else Color(0xFFEF4444), CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (cfg.enabled) "Automatización activa" else "Automatización pausada", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Acepta pedidos que cumplan tus filtros", fontSize = 12.sp, color = Muted)
                }
                Switch(checked = cfg.enabled, onCheckedChange = { on ->
                    if (on && !DrixPrefs.accessValid()) Toast.makeText(ctx, "Tu acceso no está vigente. Renueva tu cuenta por WhatsApp.", Toast.LENGTH_LONG).show()
                    else if (!on || ctx.ensurePermissions()) DrixPrefs.update { it.copy(enabled = on) }
                }, colors = SwitchDefaults.colors(checkedTrackColor = Go, checkedThumbColor = Color.White))
            }
        }
        DCard {
            Text("Tus parámetros actuales", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Recogida máx.", "${num(cfg.maxPickupKm)} km", Modifier.weight(1f))
                Tile("Destino máx.", "${num(cfg.maxTripKm)} km", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Precio por km", "S/ %.2f".format(cfg.minPerKm), Modifier.weight(1f))
                Tile("Precio mínimo", "S/ %.2f".format(cfg.minFare), Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            PillButton("Editar parámetros") { onGo(1) }
        }
        DCard {
            Text("Evita zonas que no te convienen", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            val blocked = zones.count { !it.accept }; val liked = zones.count { it.accept }
            Text("$blocked bloqueadas · $liked preferidas", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            PillButton("Abrir mapa de zonas", color = Charcoal) { onGo(2) }
        }
        RefreshCard()
        TargetAppCard()
    }
}

/* ═════════════ PARÁMETROS ═════════════ */

@Composable
fun ParamsScreen() {
    val ctx = LocalContext.current
    val cfg by DrixPrefs.cfg.collectAsStateWithLifecycle()
    var pk by remember(cfg) { mutableStateOf(num(cfg.maxPickupKm)) }
    var tr by remember(cfg) { mutableStateOf(num(cfg.maxTripKm)) }
    var km by remember(cfg) { mutableStateOf("%.2f".format(cfg.minPerKm)) }
    var fr by remember(cfg) { mutableStateOf(num(cfg.minFare)) }
    var h1 by remember(cfg) { mutableStateOf(cfg.startHour.toString()) }
    var h2 by remember(cfg) { mutableStateOf(cfg.endHour.toString()) }
    var d1 by remember(cfg) { mutableStateOf(cfg.minDelayMs.toString()) }
    var d2 by remember(cfg) { mutableStateOf(cfg.maxDelayMs.toString()) }

    Screen(hero = {
        Brand(); Spacer(Modifier.height(20.dp))
        HeroTitle("Tus parámetros actuales", sub = "Configuración basada en tus preferencias guardadas.")
    }) {
        DCard {
            LabeledField("Distancia máxima de recogida (km)", pk) { pk = it }
            Spacer(Modifier.height(16.dp))
            LabeledField("Distancia máxima de destino (km)", tr) { tr = it }
            Spacer(Modifier.height(16.dp))
            LabeledField("Precio por kilómetro (S/)", km) { km = it }
            Spacer(Modifier.height(16.dp))
            LabeledField("Precio mínimo (S/)", fr) { fr = it }
        }
        DCard {
            Text("Horario y ritmo", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledField("Desde (h)", h1, Modifier.weight(1f), decimal = false) { h1 = it }
                LabeledField("Hasta (h)", h2, Modifier.weight(1f), decimal = false) { h2 = it }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledField("Retraso mín (ms)", d1, Modifier.weight(1f), decimal = false) { d1 = it }
                LabeledField("Retraso máx (ms)", d2, Modifier.weight(1f), decimal = false) { d2 = it }
            }
            Text("Tiempo al azar entre ambos antes de pulsar Aceptar. Los pedidos caducan en segundos: no lo subas demasiado.",
                fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Aceptar si no se identifica el distrito", Modifier.weight(1f), fontSize = 14.sp)
                Switch(cfg.unknownOk, { v -> DrixPrefs.update { it.copy(unknownOk = v) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = Go, checkedThumbColor = Color.White))
            }
        }
        PillButton("Guardar cambios") {
            val lo = (d1.toIntOrNull() ?: cfg.minDelayMs).coerceIn(0, 5000)
            val hi = (d2.toIntOrNull() ?: cfg.maxDelayMs).coerceIn(lo, 5000)
            DrixPrefs.update {
                it.copy(
                    maxPickupKm = pk.asFloat() ?: it.maxPickupKm, maxTripKm = tr.asFloat() ?: it.maxTripKm,
                    minPerKm = km.asFloat() ?: it.minPerKm, minFare = fr.asFloat() ?: it.minFare,
                    startHour = h1.toIntOrNull()?.coerceIn(0, 23) ?: it.startHour,
                    endHour = h2.toIntOrNull()?.coerceIn(0, 24) ?: it.endHour,
                    minDelayMs = lo, maxDelayMs = hi
                )
            }
            Toast.makeText(ctx, "Parámetros guardados", Toast.LENGTH_SHORT).show()
        }
        Spacer(Modifier.height(8.dp))
    }
}

/* ═════════════ BLOQUEO DE ZONAS (MAPA) ═════════════ */

enum class ZoneMode(val label: String) { Navigate("Navegar"), Create("Crear zona"), Delete("Eliminar zona") }

@Composable
fun ZonesScreen() {
    val zones by DrixPrefs.zones.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(ZoneMode.Navigate) }
    var prefer by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var fly by remember { mutableStateOf<Pair<District, Int>?>(null) }
    var locate by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(YellowDeep))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ZoneMap(zones, mode, prefer, fly, locate, Modifier.fillMaxSize())

            // Barra superior flotante
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 6.dp) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(mode.label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        BadgedBox(badge = { if (zones.isNotEmpty()) Badge(containerColor = Ink, contentColor = Yellow) { Text("${zones.size}") } }) {
                            Icon(Icons.Filled.LocationOn, null, tint = Ink)
                        }
                    }
                }
                Box {
                    Surface(onClick = { menu = true }, modifier = Modifier.size(52.dp), shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 6.dp) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Tune, "Opciones", tint = Ink) }
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("📍 Mi ubicación") }, onClick = { menu = false; locate++ })
                        DropdownMenuItem(text = { Text("🗑 Borrar todas las zonas") }, onClick = { menu = false; DrixPrefs.setZones(emptyList()) })
                    }
                }
            }

            // Panel flotante de herramientas
            Surface(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 8.dp) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ToolBtn(Icons.Outlined.NearMe, "Navegar", mode == ZoneMode.Navigate) { mode = ZoneMode.Navigate }
                    ToolBtn(Icons.Outlined.AddCircleOutline, "Crear", mode == ZoneMode.Create) { mode = ZoneMode.Create }
                    ToolBtn(Icons.Outlined.Delete, "Eliminar", mode == ZoneMode.Delete) { mode = ZoneMode.Delete }
                    ToolBtn(Icons.Outlined.Search, "Buscar", searching) { searching = true }
                }
            }

            // Selector de tipo de zona al crear
            if (mode == ZoneMode.Create) Surface(
                Modifier.align(Alignment.BottomCenter).padding(16.dp), shape = RoundedCornerShape(50), color = Color.White, shadowElevation = 8.dp
            ) {
                Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Seg("🚫 Bloquear", !prefer) { prefer = false }
                    Seg("✅ Preferir", prefer) { prefer = true }
                }
            }
        }
    }
    if (searching) DistrictSearchDialog(zones, { searching = false }) { d -> fly = d to ((fly?.second ?: 0) + 1); searching = false }
}

@Composable
private fun ToolBtn(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = if (selected) Ink else Color(0xFFA0A6AE)
    Column(Modifier.width(68.dp).clickable(onClick = onClick).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = c, modifier = Modifier.size(28.dp))
        Text(label, fontSize = 11.sp, color = c, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun Seg(text: String, selected: Boolean, onClick: () -> Unit) = Surface(
    onClick = onClick, shape = RoundedCornerShape(50), color = if (selected) Yellow else Color.Transparent
) { Text(text, Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }

@Composable
private fun DistrictSearchDialog(zones: List<Zone>, onDismiss: () -> Unit, onPick: (District) -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = Districts.all.filter { Districts.norm(it.name).contains(Districts.norm(q)) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text("Buscar distrito") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Ej. Miraflores") }, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = Yellow, cursorColor = Yellow))
                LazyColumn(Modifier.heightIn(max = 340.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.name }) { d ->
                        val z = zones.firstOrNull { it.name == d.name }
                        Row(Modifier.fillMaxWidth().clickable { onPick(d) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(d.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            if (z != null) Text(if (z.accept) "✅" else "🚫")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cerrar", color = Amber) } }
    )
}

/** Mapa real (OpenStreetMap, sin API key). Dibuja cada distrito marcado como un círculo naranja. */
@Composable
fun ZoneMap(zones: List<Zone>, mode: ZoneMode, prefer: Boolean, fly: Pair<District, Int>?, locate: Int, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val modeNow by rememberUpdatedState(mode)
    val preferNow by rememberUpdatedState(prefer)
    val map = remember {
        Configuration.getInstance().apply {
            load(ctx, ctx.getSharedPreferences("osm", 0))
            userAgentValue = ctx.packageName // OSM exige identificar la app
            osmdroidBasePath = File(ctx.cacheDir, "osm"); osmdroidTileCache = File(ctx.cacheDir, "osm/tiles")
        }
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(11.0); controller.setCenter(GeoPoint(-12.0464, -77.0428)) // Lima
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    val m = modeNow
                    if (m == ZoneMode.Navigate) return false
                    val d = Districts.at(p.latitude, p.longitude)
                    if (d == null) { Toast.makeText(ctx, "Aquí no hay distrito de Lima/Callao", Toast.LENGTH_SHORT).show(); return true }
                    val cur = DrixPrefs.zones.value.filter { it.name != d.name }
                    if (m == ZoneMode.Delete) {
                        DrixPrefs.setZones(cur)
                        Toast.makeText(ctx, "${d.name}: sin marcar", Toast.LENGTH_SHORT).show()
                    } else {
                        val acc = preferNow
                        DrixPrefs.setZones(cur + Zone(d.name, acc))
                        Toast.makeText(ctx, d.name + if (acc) ": preferido (aceptar)" else ": bloqueado", Toast.LENGTH_SHORT).show()
                    }
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
    LaunchedEffect(locate) {
        if (locate > 0) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) goMe()
            else perm.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    LaunchedEffect(fly) {
        fly?.let { (d, _) -> map.controller.setZoom(12.5); map.controller.animateTo(GeoPoint(d.lat, d.lon)) }
    }
    DisposableEffect(map) { map.onResume(); onDispose { map.onPause(); me.disableMyLocation() } }

    AndroidView(
        factory = { map }, modifier = modifier,
        update = { m ->
            m.overlays.removeAll { it is Polygon }
            zones.forEach { z ->
                val d = Districts.all.firstOrNull { it.name == z.name } ?: return@forEach
                m.overlays.add(Polygon(m).apply {
                    points = Polygon.pointsAsCircle(GeoPoint(d.lat, d.lon), d.r.toDouble())
                    fillPaint.color = (if (z.accept) 0x4022C55E else 0x40FF6B1A).toInt()
                    outlinePaint.color = (if (z.accept) 0xFF16A34A else 0xFFE8541E).toInt()
                    outlinePaint.strokeWidth = 5f
                    setOnClickListener { _, _, _ -> false } // deja pasar el toque al mapa
                })
            }
            m.invalidate()
        }
    )
}

/* ═════════════ PERFIL ═════════════ */

@Composable
fun ProfileScreen(profile: Profile) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val email = AuthRepo.user?.email.orEmpty()
    val name = profile.fullName
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
        .ifEmpty { email.take(1).uppercase() }
    fun whatsapp(text: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/51900385006?text=" + Uri.encode(text))))

    Screen(hero = {
        Brand(); Spacer(Modifier.height(20.dp))
        HeroTitle("Tu cuenta", "Drix")
    }) {
        DCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).background(Yellow, CircleShape), Alignment.Center) { Text(initials, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(name.ifBlank { "Conductor Drix" }, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(email, fontSize = 12.sp, color = Muted)
                }
            }
        }
        profile.until?.let { SubscriptionCard(it) { whatsapp("Hola Drix, quiero renovar mi acceso.\nCorreo: $email") } }
        DCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, tint = Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Datos de la cuenta", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Text("Los registraste una sola vez y no se pueden editar desde la app.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            Tile("Nombres", profile.firstName.orEmpty(), Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Tile("Apellidos", profile.lastName.orEmpty(), Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Tile("Teléfono", prettyPhone(profile.phone.orEmpty()), Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Tile("Correo", email.ifBlank { "—" }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            PillButton("💬 Solicitar cambio de datos", color = Go) {
                whatsapp("Hola Drix, quiero cambiar mis datos.\nCorreo: $email\nNombre actual: $name\nTeléfono actual: ${profile.phone.orEmpty()}\nCambio que necesito: ")
            }
            Spacer(Modifier.height(10.dp))
            PillButton("💬 Ayuda por WhatsApp", color = Go) { whatsapp("Hola Drix") }
        }
        DCard {
            Text("Seguridad", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Te enviamos un enlace a tu correo para cambiar la contraseña.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            PillButton("Cambiar contraseña", color = Charcoal) {
                scope.launch {
                    AuthRepo.resetForCurrentUser()
                        .onSuccess { Toast.makeText(ctx, "Enlace enviado a $email", Toast.LENGTH_LONG).show() }
                        .onFailure { Toast.makeText(ctx, AuthRepo.message(it) ?: "No se pudo enviar", Toast.LENGTH_LONG).show() }
                }
            }
        }
        DebugCard()
        PillButton("Cerrar sesión", color = Charcoal) { AuthRepo.signOut() }
        Text("Drix v1.5.3", fontSize = 12.sp, color = Muted)
    }
}

/* ═════════════ EXTRAS (estadísticas de hoy) ═════════════ */

@Composable
fun ExtrasScreen() {
    val days by DrixStats.days.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf(DrixStats.dayKey()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); key = DrixStats.dayKey() } } // cambia solo a medianoche
    val t = days[key] ?: DayStats()
    var confirm by remember { mutableStateOf(false) }
    val avg = if (t.orders > 0) t.fare / t.orders else 0f
    val perKm = if (t.km > 0f) t.fare / t.km else 0f

    Screen(hero = {
        Brand(); Spacer(Modifier.height(20.dp))
        HeroTitle("Tus extras", "Hoy", "Lo que Drix te ha aceptado desde las 00:00.")
    }) {
        DCard {
            Text("Dinero de hoy", fontSize = 13.sp, color = Muted)
            Text("S/ %.2f".format(t.fare), fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Pedidos", t.orders.toString(), Modifier.weight(1f))
                Tile("Km recorridos", "%.1f km".format(t.km), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Promedio por pedido", "S/ %.2f".format(avg), Modifier.weight(1f))
                Tile("Ganancia por km", "S/ %.2f".format(perKm), Modifier.weight(1f))
            }
        }
        DCard {
            Text("¿Cómo se calcula?", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                "Cuenta cada pedido que Drix acepta. El dinero es la tarifa del pedido y los km suman recojo + viaje. " +
                    "Son valores estimados: no descuentan comisión, combustible ni pedidos que se cancelen, y no incluyen los que aceptes tú a mano.",
                fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            PillButton("Reiniciar contador de hoy", color = Charcoal) { confirm = true }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("¿Reiniciar hoy?", fontWeight = FontWeight.Bold) },
        text = { Text("Dinero, km y pedidos de hoy volverán a cero. No se puede deshacer.") },
        confirmButton = { TextButton({ DrixStats.resetDay(key); confirm = false }) { Text("Sí, reiniciar", color = Ink, fontWeight = FontWeight.ExtraBold) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancelar", color = Muted) } }
    )
}

/** Contador de vencimiento del acceso: meses · días · horas · min · seg. */
@Composable
fun SubscriptionCard(untilMs: Long, onRenew: () -> Unit) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); nowMs = System.currentTimeMillis() } }
    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone)
    val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(untilMs), zone)
    val expired = !end.isAfter(now)
    val months = if (expired) 0 else ChronoUnit.MONTHS.between(now, end)
    val a = now.plusMonths(months)
    val days = if (expired) 0 else ChronoUnit.DAYS.between(a, end)
    val b = a.plusDays(days)
    val hours = if (expired) 0 else ChronoUnit.HOURS.between(b, end)
    val c = b.plusHours(hours)
    val mins = if (expired) 0 else ChronoUnit.MINUTES.between(c, end)
    val secs = if (expired) 0 else ChronoUnit.SECONDS.between(c.plusMinutes(mins), end)
    val soon = !expired && ChronoUnit.DAYS.between(now, end) < 7
    val tone = if (expired || soon) Color(0xFFDC2626) else Ink
    val date = remember(untilMs) { SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(untilMs)) }
    DCard {
        Text(if (expired) "Tu cuenta venció" else "Tu cuenta vence en:", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = tone)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountTile(months, "Meses", tone, Modifier.weight(1f))
            CountTile(days, "Días", tone, Modifier.weight(1f))
            CountTile(hours, "Horas", tone, Modifier.weight(1f))
            CountTile(mins, "Min", tone, Modifier.weight(1f))
            CountTile(secs, "Seg", tone, Modifier.weight(1f))
        }
        Text("Vence el $date", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 10.dp))
        if (expired || soon) {
            Spacer(Modifier.height(12.dp))
            PillButton("💬 Renovar por WhatsApp", color = Go, onClick = onRenew)
        }
    }
}

@Composable
private fun CountTile(value: Long, label: String, color: Color, mod: Modifier) =
    Column(mod.background(Fog, RoundedCornerShape(12.dp)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString().padStart(2, '0'), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = color)
        Text(label, fontSize = 11.sp, color = Muted)
    }

/* ═════════════ SELECTOR DE APP OBJETIVO ═════════════ */

data class AppItem(val label: String, val pkg: String)

@Composable
fun TargetAppCard() {
    val ctx = LocalContext.current
    val cfg by DrixPrefs.cfg.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val label = remember(cfg.targetPkg) {
        runCatching { ctx.packageManager.getApplicationInfo(cfg.targetPkg, 0).loadLabel(ctx.packageManager).toString() }.getOrNull()
    }
    DCard {
        Text("App objetivo", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label ?: "⚠️ No instalada", fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
        Text(cfg.targetPkg, fontSize = 11.sp, color = Muted)
        PillButton("Elegir app instalada", Modifier.fillMaxWidth().padding(top = 12.dp), Charcoal) { open = true }
    }
    if (open) AppPickerDialog({ open = false }) { pkg -> DrixPrefs.update { it.copy(targetPkg = pkg) }; open = false }
}

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
                OutlinedTextField(q, { q = it }, singleLine = true, label = { Text("Buscar (ej. indrive)") }, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = Yellow, cursorColor = Yellow))
                LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.pkg }) { a ->
                        val icon = remember(a.pkg) { runCatching { pm.getApplicationIcon(a.pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
                        Row(Modifier.fillMaxWidth().clickable { onPick(a.pkg) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            icon?.let { Image(it, null, Modifier.size(40.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Column { Text(a.label, fontWeight = FontWeight.SemiBold); Text(a.pkg, fontSize = 11.sp, color = Muted) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancelar", color = Amber) } }
    )
}

/* ═════════════ REFRESCAR + DIAGNÓSTICO ═════════════ */

@Composable
fun RefreshCard() {
    val ctx = LocalContext.current
    val cfg by DrixPrefs.cfg.collectAsStateWithLifecycle()
    DCard {
        Text("Refrescar lista", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Abre la app objetivo, cierra el detalle y recarga los pedidos", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
        PillButton("🔄 Refrescar") {
            val svc = DrixAccessibilityService.instance
            if (svc == null) Toast.makeText(ctx, "Activa Drix Automático en Accesibilidad", Toast.LENGTH_LONG).show()
            else {
                ctx.packageManager.getLaunchIntentForPackage(cfg.targetPkg)?.let { ctx.startActivity(it) }
                svc.refresh(1500)
            }
        }
    }
}

@Composable
fun DebugCard() {
    val ctx = LocalContext.current
    val dbg by DrixPrefs.debug.collectAsStateWithLifecycle()
    DCard {
        Text("Diagnóstico", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Última lectura de la app objetivo:", fontSize = 11.sp, color = Muted)
        Text(dbg.ifBlank { "— aún no hay lecturas —" }, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        PillButton("Copiar texto", Modifier.fillMaxWidth().padding(top = 12.dp), Charcoal) {
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                .setPrimaryClip(android.content.ClipData.newPlainText("drix", dbg))
            Toast.makeText(ctx, "Copiado", Toast.LENGTH_SHORT).show()
        }
    }
}
