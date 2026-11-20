package com.drix.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drix.app.auth.AdminRepo
import com.drix.app.auth.AuthRepo
import com.drix.app.auth.PollRow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val NoRed = Color(0xFFDC2626)

/**
 * Encuestas Sí/No para los clientes (dentro del panel de administrador):
 *  1) «Nueva encuesta»: título + descripción → les llega a todos.
 *  2) «Historial de encuestas»: resultados (Sí / No / %), quién votó, cerrar/reabrir y borrar.
 */
@Composable
fun AdminPolls() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var polls by remember { mutableStateOf<List<PollRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()) }

    fun reload() {
        scope.launch {
            loading = true
            AdminRepo.loadPolls().onSuccess { polls = it; error = null }.onFailure { error = AuthRepo.message(it) ?: "No se pudo cargar el historial." }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    val fieldColors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = Yellow, cursorColor = Yellow)

    DCard(index = 1) {
        SectionTitle(Icons.Outlined.HowToVote, "Nueva encuesta (Sí / No)", "Propón una idea: a todos les sale una ventana para votar Sí o No. Cada cuenta vota una sola vez.")
        OutlinedTextField(title, { title = it.take(80) }, singleLine = true, label = { Text("Título (la pregunta)") }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), colors = fieldColors)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(desc, { desc = it.take(500) }, label = { Text("Descripción de la idea") }, minLines = 3, maxLines = 6, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), colors = fieldColors)
        Text("${desc.length}/500", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        PillButton("Enviar encuesta", icon = Icons.Outlined.HowToVote) {
            if (title.isBlank()) Toast.makeText(ctx, "Escribe el título de la encuesta", Toast.LENGTH_SHORT).show() else confirm = true
        }
    }

    DCard(index = 1) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Historial de encuestas (${polls.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            TextButton({ reload() }, enabled = !loading) {
                Icon(Icons.Outlined.Refresh, null, tint = Ink, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                Text(if (loading) "Cargando…" else "Actualizar", color = Ink, fontWeight = FontWeight.SemiBold)
            }
        }
        error?.let { Text(it, color = NoRed, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp)) }
        if (!loading && polls.isEmpty() && error == null) Text("Todavía no has enviado encuestas.", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
        polls.forEach { p ->
            var showVoters by remember(p.id) { mutableStateOf(false) }
            val total = p.voters.size
            val yesPct = if (total == 0) 0 else Math.round(p.yes * 100f / total)
            val noPct = if (total == 0) 0 else 100 - yesPct
            Column(Modifier.fillMaxWidth().padding(top = 12.dp).background(Fog, RoundedCornerShape(16.dp)).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.title, Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(if (p.closed) "Cerrada" else "Abierta", color = if (p.closed) Muted else Go, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text(fmt.format(Date(p.createdAt)), fontSize = 11.sp, color = Muted)
                if (p.description.isNotBlank()) Text(p.description, fontSize = 13.sp, color = Ink, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(10.dp))
                ResultBar("Sí", p.yes, yesPct, total, Go)
                Spacer(Modifier.height(6.dp))
                ResultBar("No", p.no, noPct, total, NoRed)
                Text("$total ${if (total == 1) "voto" else "votos"}", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
                if (total > 0) TextButton({ showVoters = !showVoters }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (showVoters) "Ocultar quién votó" else "Ver quién votó", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                if (showVoters) p.voters.forEach { v ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (v.yes) "✓ Sí" else "✗ No", Modifier.width(52.dp), color = if (v.yes) Go else NoRed, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        Text(v.name.ifBlank { v.email } + if (v.name.isNotBlank()) " · ${v.email}" else "", fontSize = 12.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(Modifier.padding(top = 4.dp)) {
                    TextButton({
                        scope.launch {
                            AdminRepo.setPollClosed(p.id, !p.closed).onSuccess { reload() }
                                .onFailure { Toast.makeText(ctx, AuthRepo.message(it) ?: "No se pudo actualizar", Toast.LENGTH_LONG).show() }
                        }
                    }, contentPadding = PaddingValues(0.dp)) { Text(if (p.closed) "Reabrir" else "Cerrar encuesta", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                    Spacer(Modifier.width(16.dp))
                    TextButton({ deleteId = p.id }, contentPadding = PaddingValues(0.dp)) { Text("Borrar", color = NoRed, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                }
            }
        }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { if (!sending) confirm = false },
        containerColor = Color.White,
        title = { Text("¿Enviar esta encuesta a todos?", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = { Column { Text(title.trim(), fontWeight = FontWeight.Bold); if (desc.isNotBlank()) Text(desc.trim(), modifier = Modifier.padding(top = 6.dp)) } },
        confirmButton = {
            TextButton({
                sending = true
                scope.launch {
                    AdminRepo.createPoll(title, desc)
                        .onSuccess { Toast.makeText(ctx, "Encuesta enviada", Toast.LENGTH_LONG).show(); title = ""; desc = ""; confirm = false; reload() }
                        .onFailure { Toast.makeText(ctx, AuthRepo.message(it) ?: "No se pudo enviar", Toast.LENGTH_LONG).show() }
                    sending = false
                }
            }, enabled = !sending) { Text(if (sending) "Enviando…" else "Sí, enviar", color = Ink, fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = { TextButton({ confirm = false }, enabled = !sending) { Text("Cancelar", color = Muted) } }
    )

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            containerColor = Color.White,
            title = { Text("¿Borrar la encuesta?", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = { Text("Se borran también todos sus votos. No se puede deshacer.") },
            confirmButton = {
                TextButton({
                    deleteId = null
                    scope.launch {
                        AdminRepo.deletePoll(id).onSuccess { reload() }
                            .onFailure { Toast.makeText(ctx, AuthRepo.message(it) ?: "No se pudo borrar", Toast.LENGTH_LONG).show() }
                    }
                }) { Text("Borrar", color = NoRed, fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = { TextButton({ deleteId = null }) { Text("Cancelar", color = Muted) } }
        )
    }
}

@Composable
private fun ResultBar(label: String, count: Int, pct: Int, total: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(28.dp), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        Box(Modifier.weight(1f).height(10.dp).background(Color.White, RoundedCornerShape(50))) {
            if (total > 0 && count > 0) Box(Modifier.fillMaxWidth(count.toFloat() / total).fillMaxHeight().background(color, RoundedCornerShape(50)))
        }
        Text("$count · $pct%", Modifier.width(74.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
