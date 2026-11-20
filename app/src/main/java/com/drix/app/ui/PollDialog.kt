package com.drix.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drix.app.notify.Polls
import kotlinx.coroutines.launch

/** Ventana de la encuesta del administrador: título, descripción y votar Sí / No. */
@Composable
fun PollDialog() {
    val poll by Polls.pending.collectAsStateWithLifecycle()
    val p = poll ?: return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sending by remember(p.id) { mutableStateOf(false) }
    fun vote(yes: Boolean) {
        sending = true
        scope.launch {
            Polls.vote(p, yes)
                .onSuccess { Toast.makeText(ctx, "¡Gracias por votar!", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(ctx, "No se pudo enviar tu voto. Revisa tu internet e inténtalo de nuevo.", Toast.LENGTH_LONG).show() }
            sending = false
        }
    }
    AlertDialog(
        onDismissRequest = { if (!sending) Polls.skipForNow() },
        containerColor = Color.White,
        title = { Text("📊 " + p.title.ifBlank { "Encuesta" }, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (p.description.isNotBlank()) Text(p.description, fontSize = 14.sp, color = Ink, lineHeight = 20.sp)
                Spacer(Modifier.height(10.dp))
                Text("Tu voto es de una sola vez y no se puede cambiar.", fontSize = 12.sp, color = Muted)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button({ vote(false) }, enabled = !sending, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = Fog, contentColor = Ink)) {
                    Text("No", fontWeight = FontWeight.ExtraBold)
                }
                Button({ vote(true) }, enabled = !sending, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Ink)) {
                    Text(if (sending) "Enviando…" else "Sí", fontWeight = FontWeight.ExtraBold)
                }
            }
        },
        dismissButton = { TextButton({ Polls.skipForNow() }, enabled = !sending) { Text("Ahora no", color = Muted) } }
    )
}
