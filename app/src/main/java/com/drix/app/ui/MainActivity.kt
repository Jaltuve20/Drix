package com.drix.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.drix.app.data.DrixPrefs

val Yellow = Color(0xFFFACC15); val Bg = Color(0xFFF1F5F9); val Ink = Color(0xFF0F172A)

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        DrixPrefs.init(this)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Yellow, onPrimary = Ink)) { DrixApp() } }
    }
}

private val tabs: List<Pair<String, ImageVector>> = listOf(
    "Inicio" to Icons.Filled.Home, "Estadísticas" to Icons.Filled.BarChart,
    "Configuración" to Icons.Filled.Settings, "Ayuda" to Icons.Filled.HelpOutline
)

@Composable
fun DrixApp() {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icon, label) }, label = { Text(label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Yellow)
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) { 0 -> HomeScreen(); 1 -> StatsScreen(); 2 -> SettingsScreen(); else -> ProfileScreen() }
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
