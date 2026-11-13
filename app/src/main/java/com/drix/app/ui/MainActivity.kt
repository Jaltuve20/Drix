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
import com.drix.app.auth.AuthRepo
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
    Tab("Zonas", Icons.Filled.Map), Tab("Perfil", Icons.Filled.Person)
)

/** Flujo principal: splash → (sin sesión) login / registro → (con sesión) app. */
@Composable
fun DrixRoot() {
    var splash by remember { mutableStateOf(true) }
    var user by remember { mutableStateOf(AuthRepo.user) }
    DisposableEffect(Unit) { val off = AuthRepo.listen { user = it }; onDispose { off() } }
    LaunchedEffect(Unit) { delay(1300); splash = false }
    LaunchedEffect(user?.uid) { if (user != null) AuthRepo.ensureProfile() }
    when {
        splash -> SplashScreen()
        user == null -> AuthFlow()
        else -> DrixApp()
    }
}

@Composable
fun DrixApp() {
    var tab by remember { mutableIntStateOf(0) }
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
                else -> ProfileScreen()
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
