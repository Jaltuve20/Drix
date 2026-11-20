package com.drix.app.ui

import android.content.Intent
import android.net.Uri
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drix.app.auth.AdminRepo
import com.drix.app.auth.AuthRepo
import com.drix.app.auth.Client
import com.drix.app.auth.RankRow
import com.drix.app.data.MonthlyRank
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** Precio de venta (pago único, en soles). Cambia aquí tu precio; en el diálogo también puedes escribir otro monto. */
private const val PRICE = 20.0

private enum class St(val label: String) { NONE("Pendiente"), ACTIVE("Activa"), ADMIN("Admin") }

private val Red = Color(0xFFDC2626); private val Orange = Color(0xFFD97706)

private fun Client.status(): St = when {
    !hasDoc -> St.NONE
    isAdminRow -> St.ADMIN
    else -> St.ACTIVE
}

private fun St.color() = when (this) { St.NONE -> Orange; St.ACTIVE -> Go; else -> Muted }

/** Panel de administrador: ver clientes, activar y renovar sin abrir la consola de Firebase. */
@Composable
fun AdminScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var clients by remember { mutableStateOf<List<Client>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var renewFor by remember { mutableStateOf<String?>(null) }
    var revokeFor by remember { mutableStateOf<Client?>(null) }
    var annTitle by remember { mutableStateOf("") }
    var annMsg by remember { mutableStateOf("") }
    var annConfirm by remember { mutableStateOf(false) }
    var annSending by remember { mutableStateOf(false) }
    var monthOffset by remember { mutableStateOf(0) }            // 0 = este mes, -1 = mes pasado…
    var rank by remember { mutableStateOf<List<RankRow>>(emptyList()) }
    var rankLoading by remember { mutableStateOf(true) }
    var rankError by remember { mutableStateOf<String?>(null) }
    var showAllRank by remember { mutableStateOf(false) }
    val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, monthOffset) }
    val monthKey = MonthlyRank.monthKey(cal.timeInMillis)
    val monthLabel = SimpleDateFormat("MMMM yyyy", Locale("es", "PE")).format(cal.time).replaceFirstChar { it.uppercase() }

    fun reload() {
        scope.launch {
            loading = true
            AdminRepo.load().onSuccess { clients = it; error = null }.onFailure { error = AuthRepo.message(it) ?: "No se pudo cargar." }
            loading = false
        }
    }
    fun reloadRank() {
        scope.launch {
            rankLoading = true
            AdminRepo.loadRanking(monthKey).onSuccess { rank = it; rankError = null }.onFailure { rankError = AuthRepo.message(it) ?: "No se pudo cargar el ranking." }
            rankLoading = false
        }
    }
    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(monthKey) { showAllRank = false; reloadRank() }
    val ordersByEmail = rank.associate { it.email.lowercase() to it.orders }
    BackHandler(onBack = onBack)

    val shown = clients
        .filter { c -> query.isBlank() || listOf(c.email, c.name, c.phone).any { it.contains(query.trim(), ignoreCase = true) } }
        .sortedWith(compareBy({ it.status().ordinal }, { it.name.ifBlank { it.email }.lowercase() })) // pendientes primero
    val counts = clients.groupingBy { it.status() }.eachCount()

    Screen(hero = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Brand(); Spacer(Modifier.weight(1f))
            TextButton(onBack) { Text("← Volver", color = Ink, fontWeight = FontWeight.ExtraBold) }
        }
        Spacer(Modifier.height(14.dp))
        HeroTitle("Panel", "Admin", "Verifica el pago y activa cada correo una sola vez: el acceso es de por vida. Sin tu activación nadie entra.")
    }) {
        DCard(index = 0) {
            SectionTitle(Icons.Outlined.WorkspacePremium, "Activar por correo (de por vida)")
            OutlinedTextField(
                newEmail, { newEmail = it }, singleLine = true, label = { Text("Correo del cliente") },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = Yellow, cursorColor = Yellow)
            )
            Spacer(Modifier.height(10.dp))
            PillButton("Activar…", icon = Icons.Outlined.WorkspacePremium) {
                val e = newEmail.trim().lowercase()
                if (Patterns.EMAIL_ADDRESS.matcher(e).matches()) renewFor = e
                else Toast.makeText(ctx, "Escribe un correo válido", Toast.LENGTH_SHORT).show()
            }
        }
        DCard(index = 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle(Icons.Outlined.EmojiEvents, "Top Drix del mes", "Pedidos que Drix aceptó a cada cliente.")
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ monthOffset -= 1 }) { Text("‹", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) }
                Text(monthLabel, Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton({ monthOffset += 1 }, enabled = monthOffset < 0) { Text("›", color = if (monthOffset < 0) Ink else Muted, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) }
            }
            val total = rank.sumOf { it.orders }
            Text(
                if (rankLoading) "Cargando…" else "$total pedidos · ${rank.size} conductores",
                fontSize = 12.sp, color = Muted, modifier = Modifier.padding(bottom = 10.dp)
            )
            rankError?.let { Text(it, color = Red, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            if (!rankLoading && rank.isEmpty() && rankError == null) Text("Aún no hay pedidos aceptados en este mes.", fontSize = 13.sp, color = Muted)
            val top = rank.firstOrNull()?.orders ?: 1
            (if (showAllRank) rank else rank.take(10)).forEachIndexed { i, r ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." },
                            Modifier.width(38.dp), fontWeight = FontWeight.ExtraBold, fontSize = if (i < 3) 20.sp else 15.sp, color = Ink
                        )
                        Column(Modifier.weight(1f)) {
                            Text(r.name.ifBlank { r.email }, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            if (r.name.isNotBlank()) Text(r.email, fontSize = 11.sp, color = Muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Text("${r.orders}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Ink)
                    }
                    Box(Modifier.padding(start = 38.dp, top = 5.dp).fillMaxWidth().height(5.dp).background(Fog, RoundedCornerShape(50))) {
                        Box(Modifier.fillMaxWidth((r.orders.toFloat() / top).coerceIn(0.03f, 1f)).fillMaxHeight().background(if (i < 3) Yellow else YellowDeep.copy(alpha = .55f), RoundedCornerShape(50)))
                    }
                }
            }
            if (rank.size > 10) TextButton({ showAllRank = !showAllRank }) { Text(if (showAllRank) "Ver solo el top 10" else "Ver todos (${rank.size})", color = Ink, fontWeight = FontWeight.SemiBold) }
            TextButton({ reloadRank() }, enabled = !rankLoading) { Icon(Icons.Outlined.Refresh, null, tint = Ink, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(if (rankLoading) "Cargando…" else "Actualizar ranking", color = Ink, fontWeight = FontWeight.SemiBold) }
        }
        DCard(index = 1) {
            SectionTitle(Icons.Outlined.Campaign, "Enviar notificación a todos", "Les llega a todos los que tengan la app. Solo tú, como administrador, puedes enviarla.")
            OutlinedTextField(
                annTitle, { annTitle = it.take(80) }, singleLine = true, label = { Text("Título") },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = Yellow, cursorColor = Yellow)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                annMsg, { annMsg = it.take(500) }, label = { Text("Mensaje") }, minLines = 3, maxLines = 6,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = Yellow, cursorColor = Yellow)
            )
            Text("${annMsg.length}/500", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            PillButton("Enviar a todos", icon = Icons.Outlined.Campaign) {
                if (annTitle.isBlank() || annMsg.isBlank()) Toast.makeText(ctx, "Escribe el título y el mensaje", Toast.LENGTH_SHORT).show()
                else annConfirm = true
            }
        }
        AdminPolls()
        DCard(index = 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Clientes (${clients.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                TextButton({ reload() }, enabled = !loading) { Icon(Icons.Outlined.Refresh, null, tint = Ink, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(if (loading) "Cargando…" else "Actualizar", color = Ink, fontWeight = FontWeight.SemiBold) }
            }
            Text(
                St.entries.filter { (counts[it] ?: 0) > 0 }.joinToString("  ·  ") { "${it.label} ${counts[it]}" },
                fontSize = 12.sp, color = Muted, modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                query, { query = it }, singleLine = true, label = { Text("Buscar nombre, correo o celular") }, leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Muted) },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = Yellow, cursorColor = Yellow)
            )
            error?.let { Text(it, color = Red, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
        shown.forEachIndexed { idx, c ->
            val st = c.status()
            DCard(index = idx + 2) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name.ifBlank { c.email }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (c.name.isNotBlank()) Text(c.email, fontSize = 12.sp, color = Muted)
                        if (c.phone.isNotBlank()) Text(prettyPhone(c.phone), fontSize = 12.sp, color = Muted)
                    }
                    Surface(color = st.color().copy(alpha = .12f), shape = RoundedCornerShape(50)) {
                        Text(st.label, color = st.color(), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                    }
                }
                ordersByEmail[c.email.lowercase()]?.let { Text("Pedidos en $monthLabel: $it", fontSize = 12.sp, color = Ink, fontWeight = FontWeight.SemiBold) }
                if (c.plan.isNotBlank() && !c.isAdminRow) Text("Plan: ${c.plan}" + if (c.paidTotal > 0) " · Pagado en total: S/ %.2f".format(c.paidTotal) else "", fontSize = 12.sp, color = Muted)
                if (!c.isAdminRow) {
                    Spacer(Modifier.height(12.dp))
                    if (!c.hasDoc) PillButton("Verificar y activar") { renewFor = c.email }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (c.phone.isNotBlank()) PillButton("WhatsApp", Modifier.weight(1f), Go, Icons.Outlined.Forum) {
                            val msg = "Hola ${c.name.ifBlank { "" }}, te escribo de Drix."
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${c.phone.removePrefix("+")}?text=" + Uri.encode(msg))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        if (c.hasDoc) PillButton("Quitar acceso", Modifier.weight(1f), Charcoal, Icons.Outlined.PersonOff) { revokeFor = c }
                    }
                }
            }
        }
        if (!loading && shown.isEmpty()) Text("Sin resultados.", color = Muted, fontSize = 13.sp)
    }

    if (annConfirm) AlertDialog(
        onDismissRequest = { if (!annSending) annConfirm = false },
        title = { Text("¿Enviar a todos?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(annTitle.trim(), fontWeight = FontWeight.Bold)
                Text(annMsg.trim(), modifier = Modifier.padding(top = 6.dp))
                Text("Se enviará a todos los usuarios que tengan la app.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = {
            TextButton({
                annSending = true
                scope.launch {
                    AdminRepo.sendAnnouncement(annTitle, annMsg)
                        .onSuccess { Toast.makeText(ctx, "Notificación enviada", Toast.LENGTH_LONG).show(); annTitle = ""; annMsg = ""; annConfirm = false }
                        .onFailure { Toast.makeText(ctx, AuthRepo.message(it) ?: "No se pudo enviar", Toast.LENGTH_LONG).show() }
                    annSending = false
                }
            }, enabled = !annSending) { Text(if (annSending) "Enviando…" else "Sí, enviar", color = Ink, fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = { TextButton({ annConfirm = false }, enabled = !annSending) { Text("Cancelar", color = Muted) } }
    )
    renewFor?.let { email ->
        ActivateDialog(email, { renewFor = null }) { paid ->
            scope.launch {
                AdminRepo.grant(email, paid)
                    .onSuccess { Toast.makeText(ctx, "Listo. $email ya tiene acceso de por vida", Toast.LENGTH_LONG).show(); newEmail = ""; renewFor = null; reload() }
                    .onFailure { Toast.makeText(ctx, AuthRepo.message(it) ?: "No se pudo guardar", Toast.LENGTH_LONG).show() }
            }
        }
    }
    revokeFor?.let { c ->
        AlertDialog(
            onDismissRequest = { revokeFor = null },
            title = { Text("¿Quitar acceso?", fontWeight = FontWeight.Bold) },
            text = { Text("${c.name.ifBlank { c.email }} dejará de poder usar la app. Podrás activarlo de nuevo cuando quieras.") },
            confirmButton = {
                TextButton({
                    scope.launch {
                        AdminRepo.revoke(c.email)
                            .onSuccess { Toast.makeText(ctx, "Acceso quitado", Toast.LENGTH_SHORT).show(); revokeFor = null; reload() }
                            .onFailure { Toast.makeText(ctx, AuthRepo.message(it) ?: "No se pudo quitar", Toast.LENGTH_LONG).show() }
                    }
                }) { Text("Sí, quitar", color = Red, fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = { TextButton({ revokeFor = null }) { Text("Cancelar", color = Muted) } }
        )
    }
}

@Composable
private fun ActivateDialog(email: String, onDismiss: () -> Unit, onConfirm: (paid: Double?) -> Unit) {
    var paid by remember { mutableStateOf(PRICE.toInt().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Activar a $email", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column {
                Text("Confirma que ya te pagó. El acceso será de por vida y solo para este correo.", fontSize = 13.sp, color = Muted)
                Spacer(Modifier.height(12.dp))
                LabeledField("Pagó (S/)", paid, Modifier.fillMaxWidth(), decimal = true) { paid = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7) }
            }
        },
        confirmButton = {
            TextButton({ onConfirm(paid.replace(',', '.').toDoubleOrNull()) }) { Text("Activar", color = Ink, fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar", color = Muted) } }
    )
}
