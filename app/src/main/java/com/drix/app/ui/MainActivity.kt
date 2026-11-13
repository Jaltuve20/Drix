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
import com.drix.app.data.DrixPrefs

// Paleta naranja
val Orange = Color(0xFFFF6B1A); val OrangeLight = Color(0xFFFF8A3D); val OrangeDeep = Color(0xFFF0501E)
val Charcoal = Color(0xFF2B3338); val Fog = Color(0xFFF4F5F7); val Line = Color(0xFFC9CED6)
val Ink = Color(0xFF14181C); val Muted = Color(0xFF6B7280); val Go = Color(0xFF22C55E)

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        DrixPrefs.init(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Orange, onPrimary = Color.White, secondary = Charcoal,
                background = Fog, surface = Color.White, onSurface = Ink
            )) { DrixApp() }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Inicio", Icons.Filled.Home), Tab("Parámetros", Icons.Filled.DirectionsCar),
    Tab("Zonas", Icons.Filled.Map), Tab("Extras", Icons.Filled.AutoAwesome), Tab("Perfil", Icons.Filled.Person)
)

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
                            selectedIconColor = Orange, selectedTextColor = Orange,
                            indicatorColor = Orange.copy(alpha = .12f),
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
