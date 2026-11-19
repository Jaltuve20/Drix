package com.drix.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.drix.app.notify.Announcements
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drix.app.auth.AccessDenied
import com.drix.app.auth.AuthRepo
import com.drix.app.auth.Profile
import kotlinx.coroutines.launch
import com.drix.app.data.DrixPrefs
import kotlinx.coroutines.delay

// Paleta Drix: amarillo + negro
val Yellow = Color(0xFFFFC800); val YellowLight = Color(0xFFFFDB4D); val YellowDeep = Color(0xFFF5B400)
val Amber = Color(0xFF9A6B00) // amarillo oscuro para texto/iconos sobre fondo blanco
val Charcoal = Color(0xFF1F2428); val Fog = Color(0xFFF4F5F7); val Line = Color(0xFFD5D9DF)
val Ink = Color(0xFF14181C); val Muted = Color(0xFF6B7280); val Go = Color(0xFF22C55E)

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        DrixPrefs.init(this)
        AuthRepo.init(this)
        com.drix.app.data.DrixStats.init(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Yellow, onPrimary = Ink, secondary = Charcoal,
                    background = Fog, surface = Color.White, onSurface = Ink,
                    surfaceTint = Color.Transparent, surfaceContainerHigh = Color.White, outline = Line
                ),
                shapes = Shapes(
                    small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
                    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)
                )
            ) { DrixRoot() }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Inicio", Icons.Filled.Home), Tab("Parámetros", Icons.Filled.DirectionsCar),
    Tab("Zonas", Icons.Filled.Map), Tab("Extras", Icons.Filled.BarChart), Tab("Perfil", Icons.Filled.Person)
)

private sealed interface PState {
    data object Loading : PState
    data class Loaded(val p: Profile?) : PState
    data class Failed(val msg: String) : PState
    data class Denied(val expired: Boolean) : PState
}

/** Flujo: splash → login/registro → (perfil incompleto) completar datos → (cuenta nueva) tutorial → app. */
@Composable
fun DrixRoot() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var splash by remember { mutableStateOf(true) }
    var user by remember { mutableStateOf(AuthRepo.user) }
    var ps by remember { mutableStateOf<PState>(PState.Loading) }
    val busy by AuthRepo.busy.collectAsStateWithLifecycle()
    val version by AuthRepo.version.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { val off = AuthRepo.listen { user = it }; onDispose { off() } }
    LaunchedEffect(Unit) { delay(1300); splash = false }
    // TIEMPO REAL: si el administrador te quita el acceso o te renueva, la pantalla cambia sola (sin pantalla de carga ni salir de la app).
    DisposableEffect(user?.uid) {
        val u = user
        if (u == null) onDispose { } else {
            val stop = AuthRepo.watchAccess {
                scope.launch {
                    AuthRepo.loadProfile(ctx, u.uid).fold(
                        { ps = PState.Loaded(it) },
                        { if (it is AccessDenied) ps = PState.Denied(it.expired) } // otros errores (red) se ignoran
                    )
                }
            }
            onDispose { stop() }
        }
    }
    LaunchedEffect(user?.uid, version) {
        ps = PState.Loading
        val u = user ?: return@LaunchedEffect
        ps = AuthRepo.loadProfile(ctx, u.uid).fold({ PState.Loaded(it) }, { if (it is AccessDenied) PState.Denied(it.expired) else PState.Failed(AuthRepo.message(it) ?: "Revisa tu conexión.") })
    }
    val st = ps
    val p = (st as? PState.Loaded)?.p
    // Al llegar el contador a 00: se cierra la sesión (y se apaga la automatización) y se muestra «renueva por WhatsApp».
    var expiredEmail by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(user?.uid) { if (user != null) expiredEmail = null }
    LaunchedEffect(p?.until, user?.uid) {
        val end = p?.until ?: return@LaunchedEffect
        while (System.currentTimeMillis() < end) delay((end - System.currentTimeMillis()).coerceIn(250L, 30_000L))
        expiredEmail = user?.email
        AuthRepo.signOut()
    }
    val ex = expiredEmail
    val stage = when {
        splash -> "splash"
        user == null && ex != null -> "expired"
        user == null -> "auth"
        busy || st is PState.Loading -> "splash"
        st is PState.Denied -> "denied"
        st is PState.Failed -> "failed"
        p == null || !p.complete -> "complete"
        !p.tutorialDone -> "tutorial"
        else -> "app"
    }
    AnimatedContent(
        targetState = stage,
        transitionSpec = { fadeIn(tween(380)) togetherWith fadeOut(tween(260)) },
        label = "root"
    ) { s ->
        when (s) {
            "splash" -> SplashScreen()
            "expired" -> ExpiredScreen(ex.orEmpty()) { expiredEmail = null }
            "auth" -> AuthFlow()
            "denied" -> NoAccessScreen(user?.email.orEmpty(), (st as? PState.Denied)?.expired ?: false, { AuthRepo.version.value++ }, { AuthRepo.signOut() })
            "failed" -> ProfileLoadErrorScreen((st as? PState.Failed)?.msg.orEmpty(), { AuthRepo.version.value++ }, { AuthRepo.signOut() })
            "complete" -> CompleteProfileScreen(user?.email.orEmpty())
            "tutorial" -> p?.let { pr ->
                TutorialScreen(pr.firstName.orEmpty()) {
                    ps = PState.Loaded(pr.copy(tutorialDone = true))
                    scope.launch { AuthRepo.markTutorialDone(ctx) }
                }
            }
            else -> p?.let { DrixApp(it) }
        }
    }
}

@Composable
fun DrixApp(profile: Profile) {
    var tab by remember { mutableIntStateOf(0) }
    var showAdmin by remember { mutableStateOf(false) }
    // Pestañas ya creadas. Se quedan VIVAS (como en Facebook): cambiar de pestaña solo muestra/oculta, no reconstruye la pantalla.
    // Además conservan el scroll y lo que estabas escribiendo.
    var loaded by remember { mutableStateOf(setOf(0)) }
    val live = loaded + tab
    LaunchedEffect(tab) { if (tab !in loaded) loaded = loaded + tab }
    // Precarga en segundo plano (ya con la app abierta y en reposo): Parámetros, Extras y Perfil. «Zonas» se crea al visitarla (lleva el mapa).
    LaunchedEffect(Unit) {
        delay(900)
        for (t in intArrayOf(1, 3, 4)) { if (t !in loaded) loaded = loaded + t; delay(450) }
    }
    // Avisos del administrador: permiso de notificaciones (Android 13+) y escucha mientras la app está abierta
    val appCtx = LocalContext.current
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appCtx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    DisposableEffect(Unit) { val off = Announcements.listen(appCtx); onDispose { off() } }
    UpdateDialogs()
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Fog,
            contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            bottomBar = { DrixNavBar(tab) { tab = it } }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                for (t in 0..4) if (t in live) key(t) {
                    KeepAlive(t == tab && !showAdmin) {
                        when (t) {
                            0 -> HomeScreen { tab = it }
                            1 -> ParamsScreen()
                            2 -> ZonesScreen(active = tab == 2 && !showAdmin)
                            3 -> ExtrasScreen()
                            else -> ProfileScreen(profile) { showAdmin = true }
                        }
                    }
                }
            }
        }
        // El panel de administrador va ENCIMA (antes sustituía a todo y se destruían las pestañas).
        if (showAdmin && profile.isAdmin) Box(Modifier.fillMaxSize().background(Fog).pointerInput(Unit) {}) {
            AdminScreen { showAdmin = false }
        }
    }
}

/**
 * Mantiene una pantalla compuesta y medida aunque no se vea. Si no está [active] no se coloca: ni se dibuja
 * ni recibe toques ni lee accesibilidad. Al activarse aparece al instante.
 */
@Composable
private fun KeepAlive(active: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        layout(p.width, p.height) { if (active) p.place(0, 0) }
    }) { content() }
}

/** Barra inferior flotante: la pestaña activa se expande en una píldora amarilla con su nombre. */
@Composable
private fun DrixNavBar(selected: Int, onSelect: (Int) -> Unit) {
    Surface(
        color = Color.White, shadowElevation = 18.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { i, t -> NavItem(t, i == selected) { onSelect(i) } }
        }
    }
}

@Composable
private fun NavItem(t: Tab, sel: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (sel) Yellow else Color.Transparent, tween(260), label = "nbg")
    val tint by animateColorAsState(if (sel) Ink else Color(0xFF8B929B), tween(260), label = "ntint")
    val iconScale by animateFloatAsState(if (sel) 1.12f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "nis")
    Row(
        Modifier.pressScale(src, .92f).clip(RoundedCornerShape(18.dp)).background(bg)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(t.icon, t.label, tint = tint, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
        AnimatedVisibility(sel, enter = expandHorizontally(tween(220)) + fadeIn(tween(220)), exit = shrinkHorizontally(tween(180)) + fadeOut(tween(120))) {
            Text(t.label, Modifier.padding(start = 6.dp), color = tint, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        }
    }
}

/** Comprueba si el servicio de accesibilidad de Drix está habilitado. */
fun android.content.Context.accessibilityOn(): Boolean =
    Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?.contains("com.drix.app/") == true

/** Guía al usuario por los permisos necesarios antes de activar el bot. */
fun android.content.Context.ensurePermissions(): Boolean {
    if (!Settings.canDrawOverlays(this)) {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return false
    }
    if (!accessibilityOn()) {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return false
    }
    return true
}
