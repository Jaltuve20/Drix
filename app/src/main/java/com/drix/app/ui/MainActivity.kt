package com.drix.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
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
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Yellow, onPrimary = Ink, secondary = Charcoal,
                background = Fog, surface = Color.White, onSurface = Ink
            )) { DrixRoot() }
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
    when {
        splash -> SplashScreen()
        user == null && ex != null -> ExpiredScreen(ex) { expiredEmail = null }
        user == null -> AuthFlow()
        busy || st is PState.Loading -> SplashScreen()
        st is PState.Denied -> NoAccessScreen(user?.email.orEmpty(), st.expired, { AuthRepo.version.value++ }, { AuthRepo.signOut() })
        st is PState.Failed -> ProfileLoadErrorScreen(st.msg, { AuthRepo.version.value++ }, { AuthRepo.signOut() })
        p == null || !p.complete -> CompleteProfileScreen(user?.email.orEmpty())
        !p.tutorialDone -> TutorialScreen(p.firstName.orEmpty()) {
            ps = PState.Loaded(p.copy(tutorialDone = true))
            scope.launch { AuthRepo.markTutorialDone(ctx) }
        }
        else -> DrixApp(p)
    }
}

@Composable
fun DrixApp(profile: Profile) {
    var tab by remember { mutableIntStateOf(0) }
    var showAdmin by remember { mutableStateOf(false) }
    if (showAdmin && profile.isAdmin) { AdminScreen { showAdmin = false }; return }
    Scaffold(
        containerColor = Fog,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(t.icon, t.label) }, label = { Text(t.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Ink, selectedTextColor = Ink,
                            indicatorColor = Yellow,
                            unselectedIconColor = Color(0xFF8B929B), unselectedTextColor = Color(0xFF8B929B)
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen { tab = it }
                1 -> ParamsScreen()
                2 -> ZonesScreen()
                3 -> ExtrasScreen()
                else -> ProfileScreen(profile) { showAdmin = true }
            }
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
