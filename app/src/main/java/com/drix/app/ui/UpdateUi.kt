package com.drix.app.ui

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drix.app.update.Updater
import kotlinx.coroutines.launch

/** Avisos de actualización: nueva versión, descarga con barra de progreso, permiso e instalación. */
@Composable
fun UpdateDialogs() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by Updater.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { Updater.check(ctx, manual = false) }

    when (val s = st) {
        is Updater.State.Available -> AlertDialog(
            onDismissRequest = { if (!s.info.mandatory) Updater.dismiss() },
            containerColor = Color.White,
            icon = { Icon(Icons.Outlined.CloudDownload, null, tint = Amber) },
            title = { Text("Una nueva actualización disponible", fontWeight = FontWeight.ExtraBold) },
            text = {
                // Si te saltaste versiones, aquí aparece lo nuevo de TODAS (la más reciente primero) y se instala directo la última.
                val installed = remember { Updater.installedCode(ctx) }
                val missed = remember(s.info) {
                    s.info.history.filter { it.versionCode > installed && it.versionCode <= s.info.versionCode }.sortedByDescending { it.versionCode }
                }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Versión ${s.info.versionName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (missed.size > 1) Text("Se instala directo la última versión; incluye los cambios de las ${missed.size - 1} anteriores.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Qué hay de nuevo", color = Amber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    if (missed.size > 1) missed.forEach { n ->
                        Text("Versión ${n.versionName}", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        Text(n.notes.ifBlank { "Mejoras y correcciones." }, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                    } else Text(s.info.notes.ifBlank { "Mejoras y correcciones." }, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                }
            },
            confirmButton = {
                TextButton({ scope.launch { Updater.download(ctx, s.info) } }) {
                    Text("Actualizar ahora", color = Ink, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                if (!s.info.mandatory) TextButton({ Updater.dismiss() }) { Text("Más tarde", color = Muted) }
            }
        )

        is Updater.State.Downloading -> {
            val p by animateFloatAsState(s.progress, tween(200), label = "dl")
            AlertDialog(
                onDismissRequest = {},
                containerColor = Color.White,
                title = { Text("Descargando Drix ${s.info.versionName}…", fontWeight = FontWeight.ExtraBold) },
                text = {
                    Column {
                        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Fog)) {
                            Box(Modifier.fillMaxWidth(p.coerceIn(0.02f, 1f)).fillMaxHeight().background(Yellow, RoundedCornerShape(50)))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("${(p * 100).toInt()}%", color = Muted, fontSize = 13.sp)
                    }
                },
                confirmButton = {}
            )
        }

        is Updater.State.NeedPermission -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            containerColor = Color.White,
            title = { Text("Un último permiso", fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "Android necesita que permitas a Drix instalar su propia actualización. " +
                        "Activa «Permitir de esta fuente», vuelve y toca «Instalar».",
                    color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp)
                )
            },
            confirmButton = {
                TextButton({ Updater.openPermissionSettings(ctx) }) { Text("Abrir ajustes", color = Ink, fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = {
                TextButton({ Updater.install(ctx, s.file) }) { Text("Instalar", color = Ink) }
            }
        )

        is Updater.State.Ready -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            containerColor = Color.White,
            title = { Text("Actualización lista", fontWeight = FontWeight.ExtraBold) },
            text = { Text("Si el instalador no se abrió o lo cancelaste, toca «Instalar».", color = Muted, fontSize = 14.sp) },
            confirmButton = {
                TextButton({ Updater.install(ctx, s.file) }) { Text("Instalar", color = Ink, fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = { TextButton({ Updater.dismiss() }) { Text("Cerrar", color = Muted) } }
        )

        is Updater.State.Error -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            containerColor = Color.White,
            title = { Text("No se pudo actualizar", fontWeight = FontWeight.ExtraBold) },
            text = { Text(s.msg, color = Muted, fontSize = 14.sp) },
            confirmButton = {
                val info = s.info
                if (info != null) TextButton({ scope.launch { Updater.download(ctx, info) } }) {
                    Text("Reintentar", color = Ink, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = { TextButton({ Updater.dismiss() }) { Text("Cerrar", color = Muted) } }
        )

        is Updater.State.UpToDate -> LaunchedEffect(s) {
            Toast.makeText(ctx, "Ya tienes la última versión", Toast.LENGTH_SHORT).show()
            Updater.dismiss()
        }

        is Updater.State.Idle -> Unit
    }
}

/** Historial de actualizaciones: lo nuevo de cada versión, de la más reciente a la más antigua. Queda guardado en el teléfono. */
@Composable
fun UpdateHistoryDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(Updater.history(ctx)) }
    val installed = remember { Updater.installedCode(ctx) }
    LaunchedEffect(Unit) { list = Updater.refreshHistory(ctx) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color.White,
        title = { Text("Historial de actualizaciones", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (list.isEmpty()) Text("Aún no hay historial guardado.", color = Muted, fontSize = 14.sp)
                list.forEachIndexed { i, n ->
                    if (i > 0) Spacer(Modifier.height(14.dp))
                    val tag = buildString {
                        append("Versión ${n.versionName.ifBlank { n.versionCode.toString() }}")
                        if (n.date.isNotBlank()) append(" · ${n.date}")
                        if (n.versionCode == installed) append(" · instalada")
                    }
                    Text(tag, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Amber)
                    Text(n.notes.ifBlank { "Mejoras y correcciones." }, color = Muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Cerrar", color = Ink, fontWeight = FontWeight.ExtraBold) } }
    )
}
