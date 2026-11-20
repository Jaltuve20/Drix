package com.drix.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.drix.app.auth.AuthRepo
import com.drix.app.auth.Profile
import com.drix.app.data.*
import com.drix.app.service.DrixAccessibilityService
import com.drix.app.update.Updater
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

/** Campo de texto LIGERO (BasicTextField). OutlinedTextField de Material3 es muy pesado de componer y Parámetros tiene 8. [suffix] = unidad (km, S/, ms…). */
@Composable
fun LabeledField(label: String, value: String, mod: Modifier = Modifier.fillMaxWidth(), decimal: Boolean = true, keyboard: KeyboardType? = null, suffix: String? = null, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val border by animateColorAsState(if (focused) YellowDeep else Line, tween(150), label = "fb")
    Column(mod) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink.copy(alpha = .8f), modifier = Modifier.padding(bottom = 6.dp), maxLines = 1)
        androidx.compose.foundation.text.BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Ink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard ?: if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().height(56.dp)
                        .background(if (focused) Color.White else Fog, shape)
                        .border(if (focused) 2.dp else 1.dp, border, shape)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) { inner() }
                    if (suffix != null) Text(suffix, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/** Fila con interruptor sobre fondo suave (mismo estilo en toda la app). */
@Composable
fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Fog, RoundedCornerShape(16.dp)).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 18.sp)
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Go, checkedThumbColor = Color.White))
    }
}

/** Botón secundario (solo borde). Jerarquía: relleno = acción principal, borde = secundaria, rojo = destructiva. */
@Composable
fun GhostButton(text: String, mod: Modifier = Modifier.fillMaxWidth(), tone: Color = Ink, icon: ImageVector? = null, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Row(
        mod.height(54.dp).pressScale(src, .97f).clip(shape)
            .border(1.5.dp, if (tone == Ink) Line else tone.copy(alpha = .45f), shape)
            .clickable(interactionSource = src, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(icon, null, tint = tone, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = tone, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

/** Dato de solo lectura: icono, etiqueta pequeña y valor. Se apilan con una línea fina entre ellos. */
@Composable
fun InfoRow(icon: ImageVector, label: String, value: String, divider: Boolean = true) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(Tint, CircleShape), Alignment.Center) { Icon(icon, null, tint = Amber, modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, color = Muted)
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
    }
}

/** Selector de 2-3 opciones con una píldora amarilla que DESLIZA hasta la opción elegida. */
@Composable
fun SegmentedChoice(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().background(Fog, shape).padding(4.dp)) {
        val seg = maxWidth / labels.size
        val x by animateDpAsState(seg * selected, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "segx")
        Box(Modifier.offset(x = x).width(seg).height(44.dp).background(Yellow, RoundedCornerShape(12.dp)))
        Row {
            labels.forEachIndexed { i, l ->
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    Alignment.Center
                ) { Text(l, fontWeight = if (i == selected) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 13.sp, color = Ink, maxLines = 1) }
            }
        }
    }
}

/** Fila superior de cada cabecera amarilla: marca + estado global (Activa/Pausada) para saber siempre si Drix está trabajando. */
@Composable
fun HeroTop() {
    val cfg by DrixPrefs.cfg.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Brand()
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.background(Ink.copy(alpha = .9f), RoundedCornerShape(50)).padding(start = 11.dp, end = 13.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(cfg.enabled)
            Spacer(Modifier.width(7.dp))
            AnimatedContent(
                cfg.enabled,
                transitionSpec = { (fadeIn(tween(200)) + slideInVertically { it / 2 }) togetherWith (fadeOut(tween(120)) + slideOutVertically { -it / 2 }) },
                label = "chip"
            ) { on -> Text(if (on) "Activa" else "Pausada", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, mod: Modifier = Modifier, icon: ImageVector? = null) =
    Tile(label, { value }, mod, icon)

/** [value] se lee dentro del Text: si cambia 60 veces/seg (contador animado) solo se recompone este texto. */
@Composable
private fun Tile(label: String, value: () -> String, mod: Modifier = Modifier, icon: ImageVector? = null) = Column(
    mod.background(Fog, RoundedCornerShape(18.dp)).padding(14.dp)
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Box(Modifier.size(26.dp).background(Tint, CircleShape), Alignment.Center) { Icon(icon, null, tint = Amber, modifier = Modifier.size(15.dp)) }
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontSize = 12.sp, color = Muted, maxLines = 1)
    }
    Text(value(), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink, letterSpacing = (-0.3).sp, modifier = Modifier.padding(top = 6.dp))
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
        Text(label, fontSize = 12.sp, color = Muted)
    }
}

/** Texto cuyo contenido viene de un lambda: se recompone él solo. */
@Composable
private fun CountText(text: () -> String, modifier: Modifier = Modifier, fontSize: androidx.compose.ui.unit.TextUnit, fontWeight: FontWeight? = null, color: Color, letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified) =
    Text(text(), modifier, color = color, fontSize = fontSize, fontWeight = fontWeight, letterSpacing = letterSpacing)

private fun num(v: Float): String = java.math.BigDecimal(v.toString()).stripTrailingZeros().toPlainString()
private fun String.asFloat(): Float? = trim().replace(',', '.').toFloatOrNull()

/* ═════════════ INICIO ═════════════ */

/** Mini-dato del resumen de hoy dentro de la tarjeta oscura. [value] se lee dentro del Text (solo se recompone él). */
@Composable
private fun MiniStat(value: () -> String, label: String, modifier: Modifier = Modifier) = Column(modifier) {
    CountText(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = .55f))
}

/** Tarjeta oscura de estado: resplandor verde si está activa, interruptor y resumen de HOY (dinero · pedidos · km). */
@Composable
private fun StatusCard(enabled: Boolean, today: DayStats, onToggle: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val glow by animateFloatAsState(if (enabled) 1f else 0f, tween(700), label = "glow")
    val dot by animateColorAsState(if (enabled) Go else Color(0xFFEF4444), tween(400), label = "dot")
    val fareA = rememberCountUp(today.fare); val ordA = rememberCountUp(today.orders.toFloat()); val kmA = rememberCountUp(today.km)
    Column(
        Modifier.fillMaxWidth().enter(0)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Charcoal, Ink)))
            .drawBehind {
                val c = Offset(size.width * .10f, size.height * .30f)
                drawCircle(
                    Brush.radialGradient(listOf(Go.copy(alpha = .38f * glow), Color.Transparent), center = c, radius = size.width * .55f),
                    radius = size.width * .55f, center = c
                )
            }
    ) {
        Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusPulse(dot, enabled)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    enabled,
                    transitionSpec = { (fadeIn(tween(250)) + slideInVertically { it / 3 }) togetherWith (fadeOut(tween(150)) + slideOutVertically { -it / 3 }) },
                    label = "st"
                ) { on ->
                    Column {
                        Text(if (on) "Automatización activa" else "Automatización pausada", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Text(
                            if (on) "Acepta pedidos que cumplan tus filtros" else "Enciéndela para empezar a aceptar pedidos",
                            fontSize = 12.sp, color = Color.White.copy(alpha = .62f), modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
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
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(Color.White.copy(alpha = .10f)))
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp)) {
            MiniStat({ "S/ %.2f".format(fareA.value) }, "Hoy ganado", Modifier.weight(1.3f))
            MiniStat({ ordA.value.toInt().toString() }, "Pedidos", Modifier.weight(1f))
            MiniStat({ "%.1f km".format(kmA.value) }, "Recorridos", Modifier.weight(1f))
        }
    }
}

/** Una línea de la lista «Falta configurar»: el icono pasa de aviso a ✓ verde con rebote cuando se cumple. */
@Composable
private fun CheckRow(ok: Boolean, icon: ImageVector, title: String, hint: String) {
    val c by animateColorAsState(if (ok) Go else Danger, tween(300), label = "ck")
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(c.copy(alpha = .12f), CircleShape), Alignment.Center) {
            AnimatedContent(
                ok,
                transitionSpec = { (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(tween(150))) togetherWith (scaleOut(tween(120)) + fadeOut(tween(120))) },
                label = "cki"
            ) { v -> Icon(if (v) Icons.Outlined.CheckCircle else icon, null, tint = c, modifier = Modifier.size(19.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(if (ok) "Listo" else hint, fontSize = 12.sp, color = if (ok) Go else Muted, lineHeight = 16.sp)
        }
    }
}

/**
 * «Falta configurar»: solo aparece si falta un permiso o la app de pedidos. Se vuelve a comprobar al regresar de Ajustes
 * y desaparece sola (con animación) cuando todo está listo.
 */
@Composable
private fun SetupCard() {
    val ctx = LocalContext.current
    val cfg by DrixPrefs.cfg.collectAsStateWithLifecycle()
    val active = LocalTabActive.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(active) { tick++; onPauseOrDispose { } }
    val overlay = remember(tick) { Settings.canDrawOverlays(ctx) }
    val access = remember(tick) { ctx.accessibilityOn() }
    val installed by produceState(true, cfg.targetPkg, tick) {
        value = withContext(Dispatchers.IO) { ctx.packageManager.getLaunchIntentForPackage(cfg.targetPkg) != null }
    }
    val missing = listOf(overlay, access, installed).count { !it }
    AnimatedVisibility(
        missing > 0,
        enter = fadeIn(tween(300)) + expandVertically(tween(300)),
        exit = fadeOut(tween(200)) + shrinkVertically(tween(250))
    ) {
        DCard(Modifier.padding(top = 14.dp), index = 1) {
            SectionTitle(Icons.Outlined.Settings, "Falta configurar", "Con esto Drix puede trabajar por ti")
            CheckRow(overlay, Icons.Outlined.Layers, "Mostrar sobre otras apps", "Para ver el aviso de estado")
            CheckRow(access, Icons.Outlined.Accessibility, "Accesibilidad · Drix Automático", "Para leer y aceptar pedidos")
            CheckRow(installed, Icons.Outlined.Apps, "App de pedidos instalada", "Elige una más abajo")
            if (!overlay || !access) {
                Spacer(Modifier.height(10.dp))
                PillButton("Configurar ahora", icon = Icons.Outlined.Settings) { ctx.ensurePermissions() }
            }
        }
    }
}

@Composable
fun HomeScreen(onGo: (Int) -> Unit) {
    val ctx = LocalContext.current
    val cfg by DrixPrefs.cfg.collectAsStateWithLifecycle()
    val zones by DrixPrefs.zones.collectAsStateWithLifecycle()
    val days by DrixStats.days.collectAsStateWithLifecycle()
    val today = days[DrixStats.dayKey()] ?: DayStats()

    Screen(hero = {
        HeroTop()
        HeroTitle("Drix trabaja bajo tus", "reglas")
    }) {
        Column {
            StatusCard(cfg.enabled, today) { on ->
                if (on && !DrixPrefs.accessValid()) Toast.makeText(ctx, "Tu acceso no está vigente. Renueva tu cuenta por WhatsApp.", Toast.LENGTH_LONG).show()
                else if (!on || ctx.ensurePermissions()) DrixPrefs.update { it.copy(enabled = on) }
            }
            SetupCard()
        }
        DCard(index = 2) {
            SectionTitle(Icons.Outlined.Tune, "Tus filtros actuales", "Drix acepta solo lo que cumpla todo esto")
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
        DCard(index = 3) {
            SectionTitle(Icons.Outlined.Map, "Zonas", "Evita o prefiere distritos en el mapa")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CountPill(Icons.Outlined.Block, zones.count { !it.accept }, "bloqueadas", Danger, Modifier.weight(1f))
                CountPill(Icons.Outlined.CheckCircle, zones.count { it.accept }, "preferidas", Go, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            PillButton("Abrir mapa de zonas", color = Charcoal, icon = Icons.Outlined.Map) { onGo(2) }
        }
        AppCard(4)
    }
}

/* ═════════════ PARÁMETROS ═════════════ */

@Composable
fun ParamsScreen() {
    val ctx = LocalContext.current
    val cfg by DrixPrefs.cfg.collectAsStateWithLifecycle()
    // Cada campo se reinicia SOLO cuando cambia su propio valor guardado (antes, tocar un interruptor borraba lo que escribías).
    var pk by remember(cfg.maxPickupKm) { mutableStateOf(num(cfg.maxPickupKm)) }
    var tr by remember(cfg.maxTripKm) { mutableStateOf(num(cfg.maxTripKm)) }
    var km by remember(cfg.minPerKm) { mutableStateOf("%.2f".format(cfg.minPerKm)) }
    var fr by remember(cfg.minFare) { mutableStateOf(num(cfg.minFare)) }
    var h1 by remember(cfg.startHour) { mutableStateOf(cfg.startHour.toString()) }
    var h2 by remember(cfg.endHour) { mutableStateOf(cfg.endHour.toString()) }
    var d1 by remember(cfg.minDelayMs) { mutableStateOf(cfg.minDelayMs.toString()) }
    var d2 by remember(cfg.maxDelayMs) { mutableStateOf(cfg.maxDelayMs.toString()) }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) { delay(1600); saved = false } }

    // Valores ya validados (los mismos que se guardarán) → sirven para el resumen en vivo y para saber si hay cambios.
    val nPk = pk.asFloat() ?: cfg.maxPickupKm
    val nTr = tr.asFloat() ?: cfg.maxTripKm
    val nKm = km.asFloat() ?: cfg.minPerKm
    val nFr = fr.asFloat() ?: cfg.minFare
    val nH1 = h1.toIntOrNull()?.coerceIn(0, 23) ?: cfg.startHour
    val nH2 = h2.toIntOrNull()?.coerceIn(0, 24) ?: cfg.endHour
    val nD1 = (d1.toIntOrNull() ?: cfg.minDelayMs).coerceIn(0, 5000)
    val nD2 = (d2.toIntOrNull() ?: cfg.maxDelayMs).coerceIn(nD1, 5000)
    val dirty = nPk != cfg.maxPickupKm || nTr != cfg.maxTripKm || nKm != cfg.minPerKm || nFr != cfg.minFare ||
        nH1 != cfg.startHour || nH2 != cfg.endHour || nD1 != cfg.minDelayMs || nD2 != cfg.maxDelayMs
    val showBar = dirty || saved
    val bottomPad by animateDpAsState(if (showBar) 88.dp else 8.dp, tween(250), label = "bp")

    Box(Modifier.fillMaxSize()) {
        Screen(hero = {
            HeroTop()
            HeroTitle("Ajusta tus filtros", sub = "Drix acepta un pedido solo si cumple todo lo de abajo.")
        }) {
            DCard(index = 0) {
                SectionTitle(Icons.Outlined.DirectionsCar, "Reglas de rentabilidad", "Recogida = distancia hasta el cliente · Destino = largo del viaje")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledField("Recogida máx.", pk, Modifier.weight(1f), suffix = "km") { pk = it }
                    LabeledField("Destino máx.", tr, Modifier.weight(1f), suffix = "km") { tr = it }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledField("Precio por km", km, Modifier.weight(1f), suffix = "S/") { km = it }
                    LabeledField("Precio mínimo", fr, Modifier.weight(1f), suffix = "S/") { fr = it }
                }
                Spacer(Modifier.height(14.dp))
                // Resumen en vivo: se actualiza mientras escribes, para entender qué hará Drix con estos números.
                Row(Modifier.fillMaxWidth().background(Tint, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Amber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            fun b(t: String) = withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(t) }
                            append("Aceptará pedidos con recogida hasta "); b("${num(nPk)} km")
                            append(" y viaje hasta "); b("${num(nTr)} km")
                            append(", que paguen al menos "); b("S/ %.2f por km".format(nKm))
                            append(" y "); b("S/ ${num(nFr)}"); append(" en total, de "); b("$nH1:00 a $nH2:00"); append(".")
                        },
                        fontSize = 13.sp, lineHeight = 18.sp, color = Ink
                    )
                }
            }
            DCard(index = 1) {
                SectionTitle(Icons.Outlined.Schedule, "Horario y ritmo", "Cuándo trabaja Drix y cuánto espera antes de aceptar")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledField("Desde", h1, Modifier.weight(1f), decimal = false, suffix = "h") { h1 = it }
                    LabeledField("Hasta", h2, Modifier.weight(1f), decimal = false, suffix = "h") { h2 = it }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledField("Retraso mín.", d1, Modifier.weight(1f), decimal = false, suffix = "ms") { d1 = it }
                    LabeledField("Retraso máx.", d2, Modifier.weight(1f), decimal = false, suffix = "ms") { d2 = it }
                }
                Text("Drix espera un tiempo al azar entre ambos antes de pulsar Aceptar. Los pedidos caducan en segundos: no lo subas demasiado.",
                    fontSize = 12.sp, color = Muted, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp, bottom = 14.dp))
                SwitchRow("Aceptar si no se identifica el distrito", cfg.unknownOk) { v -> DrixPrefs.update { it.copy(unknownOk = v) } }
            }
            GenderCard(2)
            ReadDelayCard(3)
            BlockedWordsCard(4)
            Spacer(Modifier.height(bottomPad))
        }
        // Barra de guardado flotante: solo aparece cuando hay cambios sin guardar.
        AnimatedVisibility(
            showBar, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160))
        ) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                PillButton(
                    if (saved) "¡Guardado!" else "Guardar cambios",
                    Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(16.dp)),
                    color = if (saved) Go else Yellow, icon = Icons.Outlined.CheckCircle
                ) {
                    DrixPrefs.update {
                        it.copy(
                            maxPickupKm = nPk, maxTripKm = nTr, minPerKm = nKm, minFare = nFr,
                            startHour = nH1, endHour = nH2, minDelayMs = nD1, maxDelayMs = nD2
                        )
                    }
                    saved = true
                    Toast.makeText(ctx, "Parámetros guardados", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/* ═════════════ PALABRAS BLOQUEADAS ═════════════ */

private val WORD_SUGGESTIONS = listOf("casco doble", "doble casco", "doble protección", "2 cascos", "mascota")

/** El usuario escribe las palabras o frases que quiere rechazar (ej. «casco doble»). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockedWordsCard(index: Int) {
    val ctx = LocalContext.current
    val words by DrixPrefs.words.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }

    fun add(raw: String) {
        val w = raw.replace(Regex("\\s+"), " ").trim().take(40)
        when {
            w.length < 2 -> Toast.makeText(ctx, "Escribe al menos 2 letras", Toast.LENGTH_SHORT).show()
            words.any { Districts.norm(it).trim() == Districts.norm(w).trim() } -> { text = ""; Toast.makeText(ctx, "Esa palabra ya está en la lista", Toast.LENGTH_SHORT).show() }
            words.size >= 50 -> Toast.makeText(ctx, "Máximo 50 palabras", Toast.LENGTH_SHORT).show()
            else -> { DrixPrefs.setWords(words + w); text = "" }
        }
    }

    DCard(index = index) {
        SectionTitle(Icons.Outlined.Block, "Palabras bloqueadas", "Si el pedido o su comentario contiene alguna, Drix lo rechaza. Ej.: casco doble.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                text, { text = it }, Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Escribe una palabra o frase", fontSize = 14.sp, color = Muted) },
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add(text) }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Line, focusedBorderColor = YellowDeep, cursorColor = Ink,
                    unfocusedContainerColor = Fog, focusedContainerColor = Color.White
                )
            )
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Yellow).clickable { add(text) },
                Alignment.Center
            ) { Icon(Icons.Outlined.AddCircleOutline, "Agregar", tint = Ink) }
        }
        Spacer(Modifier.height(14.dp))
        if (words.isEmpty()) {
            Text("Aún no bloqueas ninguna palabra.", fontSize = 12.sp, color = Muted)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                words.forEach { w ->
                    key(w) {
                        Row(
                            Modifier.popIn().clip(RoundedCornerShape(50)).background(Danger.copy(alpha = .1f))
                                .clickable { DrixPrefs.setWords(words - w) }.padding(start = 14.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(w, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Outlined.Close, "Quitar $w", tint = Danger, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        val left = WORD_SUGGESTIONS.filter { sug -> words.none { Districts.norm(it).trim() == Districts.norm(sug) } }
        if (left.isNotEmpty()) {
            Text("Sugerencias", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                left.forEach { sug ->
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Tint).clickable { add(sug) }.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.AddCircleOutline, null, tint = Amber, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(sug, fontSize = 13.sp, color = Ink)
                    }
                }
            }
        }
        Text("No distingue mayúsculas ni tildes. Toca una palabra para quitarla.", fontSize = 12.sp, color = Muted, lineHeight = 15.sp, modifier = Modifier.padding(top = 14.dp))
    }
}

/* ═════════════ BLOQUEO DE ZONAS (MAPA) ═════════════ */

enum class ZoneMode(val label: String) { Navigate("Navegar"), Create("Crear zona"), Delete("Eliminar zona") }

@Composable
fun ZonesScreen(active: Boolean = true) {
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
            ZoneMap(zones, mode, prefer, fly, locate, Modifier.fillMaxSize(), active)

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

            // Selector de tipo de zona al crear (entra y sale deslizando con rebote)
            ZonePicker(mode == ZoneMode.Create, prefer, { prefer = it }, Modifier.align(Alignment.BottomCenter))
        }
    }
    if (searching) DistrictSearchDialog(zones, { searching = false }) { d -> fly = d to ((fly?.second ?: 0) + 1); searching = false }
}

/** La animación se lee aquí dentro: solo este composable se recompone mientras entra/sale. */
@Composable
private fun ZonePicker(visible: Boolean, prefer: Boolean, onPrefer: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val pick = animateFloatAsState(
        if (visible) 1f else 0f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "pick"
    )
    if (visible || pick.value > 0.01f) Surface(
        modifier.padding(16.dp).graphicsLayer {
            val v = pick.value
            alpha = v.coerceIn(0f, 1f); translationY = (1f - v) * 90.dp.toPx()
        },
        shape = RoundedCornerShape(50), color = Color.White, shadowElevation = 10.dp
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Seg(Icons.Outlined.Block, "Bloquear", !prefer) { onPrefer(false) }
            Seg(Icons.Outlined.CheckCircle, "Preferir", prefer) { onPrefer(true) }
        }
    }
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
    val shown = remember(q) { val nq = Districts.norm(q); Districts.all.filter { Districts.normName(it).contains(nq) } }
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

// Posición del mapa recordada entre visitas a la pestaña (el MapView se destruye al salir).
private var lastCenter: GeoPoint = GeoPoint(-12.0464, -77.0428) // Lima
private var lastZoom: Double = 11.0
private var osmReady = false

private fun initOsm(ctx: Context) {
    if (osmReady) return
    Configuration.getInstance().apply {
        load(ctx, ctx.getSharedPreferences("osm", 0))
        userAgentValue = ctx.packageName // OSM exige identificar la app
        osmdroidBasePath = File(ctx.cacheDir, "osm"); osmdroidTileCache = File(ctx.cacheDir, "osm/tiles")
        tileDownloadThreads = 4; tileFileSystemThreads = 4
        cacheMapTileCount = 24
    }
    osmReady = true
}

/**
 * Mapa real (OpenStreetMap, sin API key). Dibuja cada distrito marcado como un círculo naranja.
 * Se crea UN frame después de entrar en la pestaña: así el cambio de pantalla no se atasca construyendo el MapView.
 */
@Composable
fun ZoneMap(zones: List<Zone>, mode: ZoneMode, prefer: Boolean, fly: Pair<District, Int>?, locate: Int, modifier: Modifier = Modifier, active: Boolean = true) {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { withFrameNanos { }; withFrameNanos { }; ready = true }
    if (!ready) Box(modifier.background(Color(0xFFE9ECEF)), Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), color = YellowDeep, strokeWidth = 3.dp)
    } else ZoneMapContent(zones, mode, prefer, fly, locate, modifier, active)
}

@Composable
private fun ZoneMapContent(zones: List<Zone>, mode: ZoneMode, prefer: Boolean, fly: Pair<District, Int>?, locate: Int, modifier: Modifier, active: Boolean) {
    val ctx = LocalContext.current
    val modeNow by rememberUpdatedState(mode)
    val preferNow by rememberUpdatedState(prefer)
    val map = remember {
        initOsm(ctx)
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true)
            isTilesScaledToDpi = true // tiles al tamaño de la pantalla: muchos menos tiles que descargar y dibujar
            isHorizontalMapRepetitionEnabled = false; isVerticalMapRepetitionEnabled = false
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(lastZoom); controller.setCenter(lastCenter)
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
    // El mapa se pausa cuando la pestaña no se ve (no carga tiles ni gasta GPS) y se reanuda al volver.
    LaunchedEffect(active) {
        if (active) map.onResume() else { map.onPause(); me.disableMyLocation() }
    }
    DisposableEffect(map) {
        onDispose {
            lastCenter = GeoPoint(map.mapCenter.latitude, map.mapCenter.longitude); lastZoom = map.zoomLevelDouble
            map.onPause(); me.disableMyLocation()
        }
    }

    // Último conjunto de zonas dibujado: si solo cambió el modo/«preferir» NO se reconstruyen los polígonos.
    val drawn = remember { arrayOfNulls<List<Zone>>(1) }
    AndroidView(
        factory = { map }, modifier = modifier,
        update = { m ->
            if (drawn[0] != zones) {
                drawn[0] = zones
                m.overlays.removeAll { it is Polygon }
                zones.forEach { z ->
                    val d = Districts.byName[z.name] ?: return@forEach
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
    val badge: Pair<String, Color>? = when {
        profile.isAdmin -> "Administrador" to Ink
        else -> "Cuenta activa" to Go
    }

    Screen(hero = {
        HeroTop()
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
                Column(Modifier.weight(1f)) {
                    Text(name.ifBlank { "Conductor Drix" }, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(email, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    badge?.let { (t, c) ->
                        Text(
                            t, Modifier.padding(top = 6.dp).background(c.copy(alpha = .12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
                            color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        DCard(index = 2) {
            SectionTitle(Icons.Outlined.Lock, "Datos de la cuenta", "Los registraste una sola vez y no se pueden editar desde la app.")
            InfoRow(Icons.Outlined.Person, "Nombres", profile.firstName.orEmpty().ifBlank { "—" })
            InfoRow(Icons.Outlined.Person, "Apellidos", profile.lastName.orEmpty().ifBlank { "—" })
            InfoRow(Icons.Outlined.Phone, "Teléfono", prettyPhone(profile.phone.orEmpty()))
            InfoRow(Icons.Outlined.Email, "Correo", email.ifBlank { "—" }, divider = false)
            Spacer(Modifier.height(14.dp))
            PillButton("Ayuda por WhatsApp", color = Go, icon = Icons.Outlined.SupportAgent) { whatsapp("Hola Drix") }
            Spacer(Modifier.height(10.dp))
            GhostButton("Solicitar cambio de datos", icon = Icons.Outlined.Edit) {
                whatsapp("Hola Drix, quiero cambiar mis datos.\nCorreo: $email\nNombre actual: $name\nTeléfono actual: ${profile.phone.orEmpty()}\nCambio que necesito: ")
            }
        }
        DCard(index = 3) {
            SectionTitle(Icons.Outlined.Shield, "Seguridad y versión", "Cambia tu contraseña por correo. Drix se actualiza solo desde internet.")
            InfoRow(Icons.Outlined.Info, "Versión instalada", Updater.installedName(ctx).ifBlank { "—" }, divider = false)
            Spacer(Modifier.height(12.dp))
            GhostButton("Cambiar contraseña", icon = Icons.Outlined.Key) {
                scope.launch {
                    AuthRepo.resetForCurrentUser()
                        .onSuccess { Toast.makeText(ctx, "Enlace enviado a $email", Toast.LENGTH_LONG).show() }
                        .onFailure { Toast.makeText(ctx, AuthRepo.message(it) ?: "No se pudo enviar", Toast.LENGTH_LONG).show() }
                }
            }
            Spacer(Modifier.height(10.dp))
            GhostButton("Buscar actualizaciones", icon = Icons.Outlined.Sync) {
                scope.launch { Updater.check(ctx, manual = true) }
            }
        }
        if (profile.isAdmin) PillButton("Panel de administrador", color = Charcoal, icon = Icons.Outlined.AdminPanelSettings, onClick = onAdmin)
        DebugCard(4)
        GhostButton("Cerrar sesión", tone = Danger, icon = Icons.Outlined.PowerSettingsNew) { AuthRepo.signOut() }
        Spacer(Modifier.height(4.dp))
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
    // States: se leen SOLO dentro de los Text/Tile de abajo (antes recomponían la pantalla entera en cada frame)
    val fareA = rememberCountUp(fare); val ordersA = rememberCountUp(t.orders.toFloat()); val kmA = rememberCountUp(km)
    val avgA = rememberCountUp(avg); val perKmA = rememberCountUp(perKm)

    Screen(hero = {
        HeroTop()
        HeroTitle("Tu resumen de", "hoy", "Lo que Drix te ha aceptado desde las 00:00.")
    }) {
        Column(
            Modifier.fillMaxWidth().enter(0)
                .clip(shape).background(Brush.linearGradient(listOf(Charcoal, Ink)))
                .sweepOnce(LocalTabActive.current to t.orders, Yellow.copy(alpha = .22f)).padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Payments, null, tint = Yellow, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Dinero de hoy", fontSize = 13.sp, color = Color.White.copy(alpha = .7f))
            }
            CountText({ "S/ %.2f".format(fareA.value) }, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = Yellow, letterSpacing = (-1).sp, modifier = Modifier.padding(top = 6.dp))
            if (t.orders > 0) CountText({ "${ordersA.value.toInt()} pedidos aceptados" }, fontSize = 12.sp, color = Color.White.copy(alpha = .6f))
            else Text("Aún no hay pedidos hoy. Enciende Drix en Inicio.", fontSize = 12.sp, color = Color.White.copy(alpha = .6f))
        }
        DCard(index = 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Pedidos", { ordersA.value.toInt().toString() }, Modifier.weight(1f), Icons.Outlined.Receipt)
                Tile("Km recorridos", { "%.1f km".format(kmA.value) }, Modifier.weight(1f), Icons.Outlined.Route)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Promedio/pedido", { "S/ %.2f".format(avgA.value) }, Modifier.weight(1f), Icons.Outlined.Payments)
                Tile("Ganancia por km", { "S/ %.2f".format(perKmA.value) }, Modifier.weight(1f), Icons.Outlined.Speed)
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

/* ═════════════ SELECTOR DE APP OBJETIVO ═════════════ */

class AppInfo(val label: String?, val icon: androidx.compose.ui.graphics.ImageBitmap?)

/** Caché en memoria de etiqueta + icono por paquete (cargarlos cuesta una llamada al sistema). */
object AppInfoCache {
    private val map = java.util.concurrent.ConcurrentHashMap<String, AppInfo>()
    fun peek(pkg: String): AppInfo? = map[pkg]
    fun load(ctx: Context, pkg: String): AppInfo {
        map[pkg]?.let { return it }
        val pm = ctx.packageManager
        val label = runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrNull()
        val icon = runCatching { pm.getApplicationIcon(pkg).toBitmap(112, 112).asImageBitmap() }.getOrNull()
        return AppInfo(label, icon).also { map[pkg] = it }
    }
}

data class AppItem(val label: String, val pkg: String)

/** App de pedidos: cuál es, para cambiarla, y el botón de refrescar la lista (antes eran dos tarjetas separadas). */
@Composable
fun AppCard(index: Int = 0) {
    val ctx = LocalContext.current
    val cfg by DrixPrefs.cfg.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var spins by remember { mutableIntStateOf(0) }
    val rot by animateFloatAsState(spins * 360f, tween(800, easing = FastOutSlowInEasing), label = "spin")
    // Etiqueta + icono se cargan fuera del hilo principal (IPC al PackageManager).
    val info by produceState(AppInfoCache.peek(cfg.targetPkg), cfg.targetPkg) {
        value = AppInfoCache.peek(cfg.targetPkg) ?: withContext(Dispatchers.IO) { AppInfoCache.load(ctx, cfg.targetPkg) }
    }
    val label = info?.label
    val icon = info?.icon
    DCard(index = index) {
        SectionTitle(Icons.Outlined.Apps, "App de pedidos", "Drix lee y acepta los pedidos en esta app")
        Row(Modifier.fillMaxWidth().background(Fog, RoundedCornerShape(18.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Image(icon, null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
            else Box(Modifier.size(44.dp).background(Tint, RoundedCornerShape(12.dp)), Alignment.Center) { Icon(Icons.Outlined.ErrorOutline, null, tint = Amber) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label ?: "No instalada", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(cfg.targetPkg, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Cambiar app", Modifier.weight(1f), icon = Icons.Outlined.Apps) { open = true }
            PillButton("Refrescar", Modifier.weight(1f), Charcoal, Icons.Outlined.Refresh, iconRotation = rot) {
                spins++
                val svc = DrixAccessibilityService.instance
                if (svc == null) Toast.makeText(ctx, "Activa Drix Automático en Accesibilidad", Toast.LENGTH_LONG).show()
                else {
                    ctx.packageManager.getLaunchIntentForPackage(cfg.targetPkg)?.let { ctx.startActivity(it) }
                    svc.refresh(1500)
                }
            }
        }
        Text("Refrescar abre la app, cierra el detalle y recarga la lista de pedidos.", fontSize = 12.sp, color = Muted, lineHeight = 16.sp, modifier = Modifier.padding(top = 10.dp))
    }
    if (open) AppPickerDialog({ open = false }) { pkg -> DrixPrefs.update { it.copy(targetPkg = pkg) }; open = false }
}

@Composable
fun AppPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    var q by remember { mutableStateOf("") }
    val apps by produceState<List<AppItem>>(emptyList()) {
        value = withContext(Dispatchers.Default) {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(i, 0)
                .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .filter { it.pkg != ctx.packageName }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
        }
    }
    val shown = remember(apps, q) { apps.filter { it.label.contains(q, true) || it.pkg.contains(q, true) } }
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
                        val icon by produceState(AppInfoCache.peek(a.pkg)?.icon, a.pkg) {
                            value = value ?: withContext(Dispatchers.IO) { AppInfoCache.load(ctx, a.pkg).icon }
                        }
                        Row(Modifier.fillMaxWidth().clickable { onPick(a.pkg) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            icon?.let { Image(it, null, Modifier.size(40.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Column { Text(a.label, fontWeight = FontWeight.SemiBold); Text(a.pkg, fontSize = 12.sp, color = Muted) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancelar", color = Amber) } }
    )
}

/* ═════════════ REFRESCAR + DIAGNÓSTICO ═════════════ */

/** Diagnóstico: plegado por defecto (es para soporte, no para el uso diario). Se despliega con animación. */
@Composable
fun DebugCard(index: Int = 0) {
    val ctx = LocalContext.current
    val dbg by DrixPrefs.debug.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 180f else 0f, tween(250, easing = FastOutSlowInEasing), label = "dbgrot")
    DCard(Modifier.animateContentSize(tween(250, easing = FastOutSlowInEasing)), index = index) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SectionTitle(Icons.Outlined.BugReport, "Diagnóstico", "Avanzado · para soporte") }
            Icon(Icons.Outlined.ExpandMore, if (open) "Ocultar" else "Mostrar", tint = Muted, modifier = Modifier.padding(bottom = 14.dp).graphicsLayer { rotationZ = rot })
        }
        if (open) {
            Text(
                dbg.ifBlank { "— aún no hay lecturas —" }, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp,
                modifier = Modifier.fillMaxWidth().background(Fog, RoundedCornerShape(14.dp)).padding(12.dp)
            )
            GhostButton("Copiar texto", Modifier.fillMaxWidth().padding(top = 12.dp), icon = Icons.Outlined.ContentCopy) {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("drix", dbg))
                Toast.makeText(ctx, "Copiado", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/* ═════════════ FILTRO POR GÉNERO ═════════════ */

/** Elige si Drix debe rechazar pedidos de hombres o de mujeres. Se guarda al tocar. */
@Composable
private fun GenderCard(index: Int) {
    val cfg by DrixPrefs.cfg.collectAsStateWithLifecycle()
    DCard(index = index) {
        SectionTitle(Icons.Outlined.Wc, "Género del pasajero", "Bloquea los pedidos de hombres o de mujeres. Drix lo deduce por el nombre del pasajero.")
        SegmentedChoice(listOf("Todos", "Sin hombres", "Sin mujeres"), cfg.genderBlock) { m -> DrixPrefs.update { it.copy(genderBlock = m) } }
        Text(
            "Si el nombre no permite saberlo con seguridad, el pedido NO se bloquea. Se aplica al instante.",
            fontSize = 12.sp, color = Muted, lineHeight = 16.sp, modifier = Modifier.padding(top = 10.dp)
        )
    }
}

/* ═════════════ PAUSA PARA LEER (3 s) ═════════════ */

/** Interruptor «Pausa para leer»: antes de aceptar, cuenta 3, 2, 1. Al activarlo pide confirmación. */
@Composable
private fun ReadDelayCard(index: Int) {
    val cfg by DrixPrefs.cfg.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    DCard(index = index) {
        SectionTitle(Icons.Outlined.Timer, "Pausa para leer", "Cuenta regresiva de 3 segundos antes de aceptar.")
        SwitchRow("Esperar 3 segundos antes de aceptar", cfg.readDelay) { v -> if (v) confirm = true else DrixPrefs.update { it.copy(readDelay = false) } }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, null, tint = Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "El pedido se abre normal, pero antes de aceptarlo aparece un contador de 3, 2, 1 en el aviso flotante y recién entonces se acepta. Así tienes tiempo de leerlo.",
                fontSize = 12.sp, color = Muted, lineHeight = 17.sp
            )
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        containerColor = Color.White,
        title = { Text("Pausa para leer", fontWeight = FontWeight.ExtraBold) },
        text = { Text("¿Estás consciente de que aceptando esta opción podrías perder pedidos por algunos segundos?", color = Muted) },
        confirmButton = {
            TextButton({ DrixPrefs.update { it.copy(readDelay = true) }; confirm = false }) {
                Text("Sí, activar", color = Ink, fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancelar", color = Muted) } }
    )
}
