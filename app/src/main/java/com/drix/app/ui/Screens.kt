package com.drix.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
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

/** Tarjeta blanca con borde fino y sombra suave. [index] escalona su aparición. */
@Composable
fun DCard(mod: Modifier = Modifier, index: Int = 0, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        mod.fillMaxWidth().enter(index)
            .shadow(10.dp, shape, ambientColor = Ink.copy(alpha = .06f), spotColor = Ink.copy(alpha = .14f))
            .clip(shape).background(Color.White).border(1.dp, Hairline, shape).padding(18.dp),
        content = content
    )
}

/** Pantalla con cabecera amarilla viva (burbujas y líneas de velocidad) y tarjetas que la solapan. */
@Composable
fun Screen(hero: @Composable ColumnScope.() -> Unit, body: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Fog).verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                .background(Brush.linearGradient(listOf(YellowLight, YellowDeep)))
                .heroMotion()
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 48.dp),
            content = hero
        )
        Column(
            Modifier.offset(y = (-24).dp).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = body
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun HeroTitle(line: String, chip: String? = null, sub: String? = null) {
    Column(Modifier.enter(1)) {
        Text(line, color = Ink, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
        if (chip != null) Surface(color = Ink, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 6.dp).popIn(300)) {
            Text(chip, color = Yellow, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
        }
        if (sub != null) Text(sub, color = Ink.copy(alpha = .75f), fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
fun SectionTitle(icon: ImageVector, title: String, sub: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(Tint, RoundedCornerShape(12.dp)), Alignment.Center) {
            Icon(icon, null, tint = Amber, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (sub != null) Text(sub, fontSize = 12.sp, color = Muted, lineHeight = 16.sp)
        }
    }
    Spacer(Modifier.height(14.dp))
}

/** Botón con degradado sutil, rebote al pulsar, vibración ligera, icono opcional y estado de carga. */
@Composable
fun PillButton(
    text: String, mod: Modifier = Modifier.fillMaxWidth(), color: Color = Yellow, icon: ImageVector? = null,
    iconRotation: Float = 0f, loading: Boolean = false, onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(color, tween(300), label = "pbg")
    val fg = if (color == Yellow) Ink else Color.White
    val shape = RoundedCornerShape(16.dp)
    Row(
        mod.height(54.dp).pressScale(src)
            .shadow(if (color == Yellow) 8.dp else 0.dp, shape, ambientColor = Yellow.copy(alpha = .5f), spotColor = YellowDeep.copy(alpha = .6f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(bg, Color.White, .14f), bg)))
            .clickable(interactionSource = src, indication = LocalIndication.current, enabled = !loading, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick()
            }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = fg, strokeWidth = 2.5.dp)
        else {
            if (icon != null) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = iconRotation })
                Spacer(Modifier.width(8.dp))
            }
            AnimatedContent(text, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "ptext") { s ->
                Text(s, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun LabeledField(label: String, value: String, mod: Modifier = Modifier.fillMaxWidth(), decimal: Boolean = true, keyboard: KeyboardType? = null, onChange: (String) -> Unit) {
    Column(mod) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink.copy(alpha = .8f), modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value, onChange, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard ?: if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Line, focusedBorderColor = YellowDeep, cursorColor = Ink,
                unfocusedContainerColor = Fog, focusedContainerColor = Color.White
            )
        )
    }
}

@Composable
private fun Tile(label: String, value: String, mod: Modifier = Modifier, icon: ImageVector? = null) = Column(
    mod.background(Fog, RoundedCornerShape(18.dp)).padding(14.dp)
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Box(Modifier.size(26.dp).background(Tint, CircleShape), Alignment.Center) { Icon(icon, null, tint = Amber, modifier = Modifier.size(15.dp)) }
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontSize = 12.sp, color = Muted, maxLines = 1)
    }
    Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink, letterSpacing = (-0.3).sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun CountPill(icon: ImageVector, n: Int, label: String, tone: Color, mod: Modifier = Modifier) = Row(
    mod.background(tone.copy(alpha = .1f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Icon(icon, null, tint = tone, modifier = Modifier.size(22.dp))
    Spacer(Modifier.width(10.dp))
    Column {
        AnimatedContent(n, transitionSpec = { (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut()) }, label = "cp") { v ->
            Text(v.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Ink)
        }
        Text(label, fontSize = 11.sp, color = Muted)
    }
}

private fun num(v: Float): String = java.math.BigDecimal(v.toString()).stripTrailingZeros().toPlainString()
private fun String.asFloat(): Float? = trim().replace(',', '.').toFloatOrNull()

/* ═════════════ INICIO ═════════════ */

/** Tarjeta oscura de estado: ondas tipo radar y resplandor verde cuando la automatización está activa. */
@Composable
private fun StatusCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val glow by animateFloatAsState(if (enabled) 1f else 0f, tween(700), label = "glow")
    val dot by animateColorAsState(if (enabled) Go else Color(0xFFEF4444), tween(400), label = "dot")
    Row(
        Modifier.fillMaxWidth().enter(0)
            .shadow(14.dp, shape, ambientColor = Ink.copy(alpha = .15f), spotColor = Ink.copy(alpha = .3f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(Charcoal, Ink)))
            .drawBehind {
                val c = Offset(size.width * .10f, size.height * .5f)
                drawCircle(
                    Brush.radialGradient(listOf(Go.copy(alpha = .38f * glow), Color.Transparent), center = c, radius = size.width * .55f),
                    radius = size.width * .55f, center = c
                )
            }
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusPulse(dot, enabled)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            AnimatedContent(
                enabled,
                transitionSpec = { (fadeIn(tween(250)) + slideInVertically { it / 3 }) togetherWith (fadeOut(tween(150)) + slideOutVertically { -it / 3 }) },
                label = "st"
            ) { on ->
                Text(if (on) "Automatización activa" else "Automatización pausada", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }
            Text("Acepta pedidos que cumplan tus filtros", fontSize = 12.sp, color = Color.White.copy(alpha = .62f), modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = enabled, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Go, checkedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = .15f), uncheckedThumbColor = Color.White.copy(alpha = .85f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun HomeScreen(onGo: (Int) -> Unit) {
    val ctx = LocalContext.current
    val cfg by DrixPrefs.cfg.collectAsStateWithLifecycle()
    val zones by DrixPrefs.zones.collectAsStateWithLifecycle()

    Screen(hero = {
        Brand(); Spacer(Modifier.height(20.dp))
        HeroTitle("Drix trabaja bajo tus", "reglas")
    }) {
        StatusCard(cfg.enabled) { on ->
            if (on && !DrixPrefs.accessValid()) Toast.makeText(ctx, "Tu acceso no está vigente. Renueva tu cuenta por WhatsApp.", Toast.LENGTH_LONG).show()
            else if (!on || ctx.ensurePermissions()) DrixPrefs.update { it.copy(enabled = on) }
        }
        DCard(index = 1) {
            SectionTitle(Icons.Outlined.Tune, "Tus parámetros actuales")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Recogida máx.", "${num(cfg.maxPickupKm)} km", Modifier.weight(1f), Icons.Outlined.NearMe)
                Tile("Destino máx.", "${num(cfg.maxTripKm)} km", Modifier.weight(1f), Icons.Outlined.Flag)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Precio por km", "S/ %.2f".format(cfg.minPerKm), Modifier.weight(1f), Icons.Outlined.Speed)
                Tile("Precio mínimo", "S/ %.2f".format(cfg.minFare), Modifier.weight(1f), Icons.Outlined.Payments)
            }
            Spacer(Modifier.height(14.dp))
            PillButton("Editar parámetros", icon = Icons.Outlined.Tune) { onGo(1) }
        }
        DCard(index = 2) {
            SectionTitle(Icons.Outlined.Map, "Evita zonas que no te convienen")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CountPill(Icons.Outlined.Block, zones.count { !it.accept }, "bloqueadas", Danger, Modifier.weight(1f))
                CountPill(Icons.Outlined.CheckCircle, zones.count { it.accept }, "preferidas", Go, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            PillButton("Abrir mapa de zonas", color = Charcoal, icon = Icons.Outlined.Map) { onGo(2) }
        }
        RefreshCard(3)
        TargetAppCard(4)
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
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) { delay(1600); saved = false } }

    Screen(hero = {
        Brand(); Spacer(Modifier.height(20.dp))
        HeroTitle("Tus parámetros actuales", sub = "Configuración basada en tus preferencias guardadas.")
    }) {
        DCard(index = 0) {
            SectionTitle(Icons.Outlined.DirectionsCar, "Reglas de rentabilidad", "Drix acepta solo si cumple todas")
            LabeledField("Distancia máxima de recogida (km)", pk) { pk = it }
            Spacer(Modifier.height(16.dp))
            LabeledField("Distancia máxima de destino (km)", tr) { tr = it }
            Spacer(Modifier.height(16.dp))
            LabeledField("Precio por kilómetro (S/)", km) { km = it }
            Spacer(Modifier.height(16.dp))
            LabeledField("Precio mínimo (S/)", fr) { fr = it }
        }
        DCard(index = 1) {
            SectionTitle(Icons.Outlined.Schedule, "Horario y ritmo")
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
                fontSize = 11.sp, color = Muted, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 14.dp).background(Fog, RoundedCornerShape(16.dp)).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Aceptar si no se identifica el distrito", Modifier.weight(1f), fontSize = 14.sp)
                Switch(cfg.unknownOk, { v -> DrixPrefs.update { it.copy(unknownOk = v) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = Go, checkedThumbColor = Color.White))
            }
        }
        Box(Modifier.enter(2)) {
            PillButton(if (saved) "¡Guardado!" else "Guardar cambios", color = if (saved) Go else Yellow, icon = Icons.Outlined.CheckCircle) {
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
                saved = true
                Toast.makeText(ctx, "Parámetros guardados", Toast.LENGTH_SHORT).show()
            }
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
                Surface(Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 8.dp) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(
                            mode, Modifier.weight(1f),
                            transitionSpec = { (fadeIn(tween(220)) + slideInVertically { it / 2 }) togetherWith (fadeOut(tween(120)) + slideOutVertically { -it / 2 }) },
                            label = "mode"
                        ) { m -> Text(m.label, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                        BadgedBox(badge = { if (zones.isNotEmpty()) Badge(containerColor = Ink, contentColor = Yellow) { Text("${zones.size}") } }) {
                            Icon(Icons.Filled.LocationOn, null, tint = Ink)
                        }
                    }
                }
                Box {
                    Surface(onClick = { menu = true }, modifier = Modifier.size(52.dp), shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 8.dp) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Tune, "Opciones", tint = Ink) }
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Mi ubicación") }, leadingIcon = { Icon(Icons.Outlined.MyLocation, null, tint = Ink) }, onClick = { menu = false; locate++ })
                        DropdownMenuItem(text = { Text("Borrar todas las zonas") }, leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null, tint = Danger) }, onClick = { menu = false; DrixPrefs.setZones(emptyList()) })
                    }
                }
            }

            // Panel flotante de herramientas
            Surface(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), shape = RoundedCornerShape(26.dp), color = Color.White, shadowElevation = 10.dp) {
                Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ToolBtn(Icons.Outlined.NearMe, "Navegar", mode == ZoneMode.Navigate) { mode = ZoneMode.Navigate }
                    ToolBtn(Icons.Outlined.AddCircleOutline, "Crear", mode == ZoneMode.Create) { mode = ZoneMode.Create }
                    ToolBtn(Icons.Outlined.Delete, "Eliminar", mode == ZoneMode.Delete) { mode = ZoneMode.Delete }
                    ToolBtn(Icons.Outlined.Search, "Buscar", searching) { searching = true }
                }
            }

            // Selector de tipo de zona al crear (entra y sale deslizando)
            AnimatedVisibility(
                visible = mode == ZoneMode.Create,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                enter = slideInVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Surface(shape = RoundedCornerShape(50), color = Color.White, shadowElevation = 10.dp) {
                    Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Seg(Icons.Outlined.Block, "Bloquear", !prefer) { prefer = false }
                        Seg(Icons.Outlined.CheckCircle, "Preferir", prefer) { prefer = true }
                    }
                }
            }
        }
    }
    if (searching) DistrictSearchDialog(zones, { searching = false }) { d -> fly = d to ((fly?.second ?: 0) + 1); searching = false }
}

@Composable
private fun ToolBtn(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Yellow else Color.Transparent, tween(250), label = "tb")
    val c by animateColorAsState(if (selected) Ink else Color(0xFFA0A6AE), tween(250), label = "tc")
    val sc by animateFloatAsState(if (selected) 1.1f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "ts")
    Column(
        Modifier.padding(horizontal = 6.dp, vertical = 2.dp).width(60.dp).clip(RoundedCornerShape(18.dp)).background(bg)
            .clickable(onClick = onClick).padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = c, modifier = Modifier.size(26.dp).graphicsLayer { scaleX = sc; scaleY = sc })
        Text(label, fontSize = 11.sp, color = c, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun Seg(icon: ImageVector, text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Yellow else Color.Transparent, tween(250), label = "sg")
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun DistrictSearchDialog(zones: List<Zone>, onDismiss: () -> Unit, onPick: (District) -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = Districts.all.filter { Districts.norm(it.name).contains(Districts.norm(q)) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text("Buscar distrito", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Ej. Miraflores") }, shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Muted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = YellowDeep, cursorColor = Ink))
                LazyColumn(Modifier.heightIn(max = 340.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.name }) { d ->
                        val z = zones.firstOrNull { it.name == d.name }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(d) }.padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(d.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            if (z != null) Icon(if (z.accept) Icons.Outlined.CheckCircle else Icons.Outlined.Block, null, tint = if (z.accept) Go else Danger, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cerrar", color = Amber, fontWeight = FontWeight.Bold) } }
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
fun ProfileScreen(profile: Profile, onAdmin: () -> Unit = {}) {
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
        DCard(index = 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).popIn(200).background(Brush.linearGradient(listOf(YellowLight, YellowDeep)), CircleShape)
                        .border(3.dp, Color.White, CircleShape),
                    Alignment.Center
                ) { Text(initials, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(name.ifBlank { "Conductor Drix" }, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text(email, fontSize = 12.sp, color = Muted)
                }
            }
        }
        profile.until?.let { SubscriptionCard(it, profile.isTrial) { whatsapp("Hola Drix, quiero renovar mi acceso.\nCorreo: $email") } }
        DCard(index = 2) {
            SectionTitle(Icons.Outlined.Lock, "Datos de la cuenta", "Los registraste una sola vez y no se pueden editar desde la app.")
            Tile("Nombres", profile.firstName.orEmpty(), Modifier.fillMaxWidth(), Icons.Outlined.Person)
            Spacer(Modifier.height(10.dp))
            Tile("Apellidos", profile.lastName.orEmpty(), Modifier.fillMaxWidth(), Icons.Outlined.Person)
            Spacer(Modifier.height(10.dp))
            Tile("Teléfono", prettyPhone(profile.phone.orEmpty()), Modifier.fillMaxWidth(), Icons.Outlined.Phone)
            Spacer(Modifier.height(10.dp))
            Tile("Correo", email.ifBlank { "—" }, Modifier.fillMaxWidth(), Icons.Outlined.Email)
            Spacer(Modifier.height(16.dp))
            PillButton("Solicitar cambio de datos", color = Go, icon = Icons.Outlined.Edit) {
                whatsapp("Hola Drix, quiero cambiar mis datos.\nCorreo: $email\nNombre actual: $name\nTeléfono actual: ${profile.phone.orEmpty()}\nCambio que necesito: ")
            }
            Spacer(Modifier.height(10.dp))
            PillButton("Ayuda por WhatsApp", color = Go, icon = Icons.Outlined.SupportAgent) { whatsapp("Hola Drix") }
        }
        DCard(index = 3) {
            SectionTitle(Icons.Outlined.Shield, "Seguridad", "Te enviamos un enlace a tu correo para cambiar la contraseña.")
            PillButton("Cambiar contraseña", color = Charcoal, icon = Icons.Outlined.Key) {
                scope.launch {
                    AuthRepo.resetForCurrentUser()
                        .onSuccess { Toast.makeText(ctx, "Enlace enviado a $email", Toast.LENGTH_LONG).show() }
                        .onFailure { Toast.makeText(ctx, AuthRepo.message(it) ?: "No se pudo enviar", Toast.LENGTH_LONG).show() }
                }
            }
        }
        if (profile.isAdmin) PillButton("Panel de administrador", icon = Icons.Outlined.AdminPanelSettings, onClick = onAdmin)
        DebugCard(4)
        PillButton("Cerrar sesión", color = Charcoal, icon = Icons.Outlined.PowerSettingsNew) { AuthRepo.signOut() }
        Text("Drix v1.6.1", fontSize = 12.sp, color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
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
    val fare = t.fare.toFloat(); val km = t.km.toFloat()
    val avg = if (t.orders > 0) fare / t.orders else 0f
    val perKm = if (km > 0f) fare / km else 0f
    val shape = RoundedCornerShape(26.dp)

    Screen(hero = {
        Brand(); Spacer(Modifier.height(20.dp))
        HeroTitle("Tus extras", "Hoy", "Lo que Drix te ha aceptado desde las 00:00.")
    }) {
        Column(
            Modifier.fillMaxWidth().enter(0)
                .shadow(14.dp, shape, ambientColor = Ink.copy(alpha = .15f), spotColor = Ink.copy(alpha = .3f))
                .clip(shape).background(Brush.linearGradient(listOf(Charcoal, Ink))).padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Payments, null, tint = Yellow, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Dinero de hoy", fontSize = 13.sp, color = Color.White.copy(alpha = .7f))
            }
            Text("S/ %.2f".format(rememberCountUp(fare)), fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = Yellow, letterSpacing = (-1).sp, modifier = Modifier.padding(top = 6.dp))
            Text("${rememberCountUp(t.orders.toFloat()).toInt()} pedidos aceptados", fontSize = 12.sp, color = Color.White.copy(alpha = .6f))
        }
        DCard(index = 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Pedidos", rememberCountUp(t.orders.toFloat()).toInt().toString(), Modifier.weight(1f), Icons.Outlined.Receipt)
                Tile("Km recorridos", "%.1f km".format(rememberCountUp(km)), Modifier.weight(1f), Icons.Outlined.Route)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Promedio/pedido", "S/ %.2f".format(rememberCountUp(avg)), Modifier.weight(1f), Icons.Outlined.Payments)
                Tile("Ganancia por km", "S/ %.2f".format(rememberCountUp(perKm)), Modifier.weight(1f), Icons.Outlined.Speed)
            }
        }
        DCard(index = 2) {
            SectionTitle(Icons.Outlined.Info, "¿Cómo se calcula?")
            Text(
                "Cuenta cada pedido que Drix acepta. El dinero es la tarifa del pedido y los km suman recojo + viaje. " +
                    "Son valores estimados: no descuentan comisión, combustible ni pedidos que se cancelen, y no incluyen los que aceptes tú a mano.",
                fontSize = 12.sp, color = Muted, lineHeight = 17.sp
            )
            Spacer(Modifier.height(14.dp))
            PillButton("Reiniciar contador de hoy", color = Charcoal, icon = Icons.Outlined.Refresh) { confirm = true }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, containerColor = Color.White,
        title = { Text("¿Reiniciar hoy?", fontWeight = FontWeight.ExtraBold) },
        text = { Text("Dinero, km y pedidos de hoy volverán a cero. No se puede deshacer.") },
        confirmButton = { TextButton({ DrixStats.resetDay(key); confirm = false }) { Text("Sí, reiniciar", color = Ink, fontWeight = FontWeight.ExtraBold) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancelar", color = Muted) } }
    )
}

/** Contador de vencimiento del acceso: meses · días · horas · min · seg (los dígitos ruedan al cambiar). */
@Composable
fun SubscriptionCard(untilMs: Long, trial: Boolean = false, onRenew: () -> Unit) {
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
    val tone = if (expired || soon) Danger else Ink
    val date = remember(untilMs) { SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(untilMs)) }
    DCard(index = 1) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(if (expired || soon) Danger.copy(alpha = .1f) else Tint, RoundedCornerShape(12.dp)), Alignment.Center) {
                Icon(Icons.Outlined.Schedule, null, tint = if (expired || soon) Danger else Amber, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(if (expired) "Tu cuenta venció" else if (trial) "Tu prueba gratis termina en:" else "Tu cuenta vence en:", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = tone)
        }
        Spacer(Modifier.height(14.dp))
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
            PillButton("Renovar por WhatsApp", color = Go, icon = Icons.Outlined.SupportAgent, onClick = onRenew)
        }
    }
}

@Composable
private fun CountTile(value: Long, label: String, color: Color, mod: Modifier) {
    Column(mod.background(Fog, RoundedCornerShape(14.dp)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            value,
            transitionSpec = { (slideInVertically { -it } + fadeIn(tween(200))) togetherWith (slideOutVertically { it } + fadeOut(tween(150))) },
            label = "count"
        ) { v -> Text(v.toString().padStart(2, '0'), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = color) }
        Text(label, fontSize = 11.sp, color = Muted)
    }
}

/* ═════════════ SELECTOR DE APP OBJETIVO ═════════════ */

data class AppItem(val label: String, val pkg: String)

@Composable
fun TargetAppCard(index: Int = 0) {
    val ctx = LocalContext.current
    val cfg by DrixPrefs.cfg.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val label = remember(cfg.targetPkg) {
        runCatching { ctx.packageManager.getApplicationInfo(cfg.targetPkg, 0).loadLabel(ctx.packageManager).toString() }.getOrNull()
    }
    val icon = remember(cfg.targetPkg) { runCatching { ctx.packageManager.getApplicationIcon(cfg.targetPkg).toBitmap(112, 112).asImageBitmap() }.getOrNull() }
    DCard(index = index) {
        SectionTitle(Icons.Outlined.Apps, "App objetivo")
        Row(Modifier.fillMaxWidth().background(Fog, RoundedCornerShape(18.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Image(icon, null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
            else Box(Modifier.size(44.dp).background(Tint, RoundedCornerShape(12.dp)), Alignment.Center) { Icon(Icons.Outlined.ErrorOutline, null, tint = Amber) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label ?: "No instalada", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(cfg.targetPkg, fontSize = 11.sp, color = Muted)
            }
        }
        PillButton("Elegir app instalada", Modifier.fillMaxWidth().padding(top = 12.dp), Charcoal, Icons.Outlined.Apps) { open = true }
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
fun RefreshCard(index: Int = 0) {
    val ctx = LocalContext.current
    val cfg by DrixPrefs.cfg.collectAsStateWithLifecycle()
    var spins by remember { mutableIntStateOf(0) }
    val rot by animateFloatAsState(spins * 360f, tween(800, easing = FastOutSlowInEasing), label = "spin")
    DCard(index = index) {
        SectionTitle(Icons.Outlined.Sync, "Refrescar lista", "Abre la app objetivo, cierra el detalle y recarga los pedidos")
        PillButton("Refrescar", icon = Icons.Outlined.Refresh, iconRotation = rot) {
            spins++
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
fun DebugCard(index: Int = 0) {
    val ctx = LocalContext.current
    val dbg by DrixPrefs.debug.collectAsStateWithLifecycle()
    DCard(index = index) {
        SectionTitle(Icons.Outlined.BugReport, "Diagnóstico", "Última lectura de la app objetivo")
        Text(
            dbg.ifBlank { "— aún no hay lecturas —" }, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp,
            modifier = Modifier.fillMaxWidth().background(Fog, RoundedCornerShape(14.dp)).padding(12.dp)
        )
        PillButton("Copiar texto", Modifier.fillMaxWidth().padding(top = 12.dp), Charcoal, Icons.Outlined.ContentCopy) {
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                .setPrimaryClip(android.content.ClipData.newPlainText("drix", dbg))
            Toast.makeText(ctx, "Copiado", Toast.LENGTH_SHORT).show()
        }
    }
}
