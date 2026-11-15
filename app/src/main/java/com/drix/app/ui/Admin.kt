package com.drix.app.ui

import android.content.Intent
import android.net.Uri
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** Atajos de cobro: meses → soles. Cambia aquí tus precios. Para otros montos usa los campos "Meses" y "Pagó". */
private val PRESETS = listOf(1 to 7.0, 2 to 10.0)

private enum class St(val label: String) {
    NONE("Sin acceso"), EXPIRED("Vencida"), SOON("Por vencer"), TRIAL("Prueba gratis"),
    ACTIVE("Activa"), NOEXP("Sin vencimiento"), ADMIN("Admin")
}

private val Red = Color(0xFFDC2626); private val Orange = Color(0xFFD97706); private val Blue = Color(0xFF2563EB)

private fun Client.status(now: Long): St = when {
    !hasDoc -> St.NONE
    isAdminRow -> St.ADMIN
    untilMs == null -> St.NOEXP
    untilMs < now -> St.EXPIRED
    trial -> St.TRIAL
    untilMs - now < 7 * 86_400_000L -> St.SOON
    else -> St.ACTIVE
}

private fun St.color() = when (this) { St.NONE, St.EXPIRED -> Red; St.SOON -> Orange; St.TRIAL -> Blue; St.ACTIVE -> Go; else -> Muted }

private fun fmt(ms: Long) = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(ms))

private fun rel(ms: Long, now: Long): String {
    val d = ms - now
    val abs = kotlin.math.abs(d)
    val days = abs / 86_400_000L; val hours = abs % 86_400_000L / 3_600_000L
    val t = if (days > 0) "$days d $hours h" else "$hours h ${abs % 3_600_000L / 60_000L} min"
    return if (d >= 0) "en $t" else "hace $t"
}

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

    fun reload() {
        scope.launch {
            loading = true
            AdminRepo.load().onSuccess { clients = it; error = null }.onFailure { error = AuthRepo.message(it) ?: "No se pudo cargar." }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }
    BackHandler(onBack = onBack)

    val now = System.currentTimeMillis()
    val shown = clients
        .filter { c -> query.isBlank() || listOf(c.email, c.name, c.phone).any { it.contains(query.trim(), ignoreCase = true) } }
        .sortedWith(compareBy({ it.status(now).ordinal }, { it.untilMs ?: Long.MAX_VALUE }))
    val counts = clients.groupingBy { it.status(now) }.eachCount()

    Screen(hero = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Brand(); Spacer(Modifier.weight(1f))
            TextButton(onBack) { Text("← Volver", color = Ink, fontWeight = FontWeight.ExtraBold) }
        }
        Spacer(Modifier.height(14.dp))
        HeroTitle("Panel", "Admin", "Activa y renueva cuentas. Cada pago se suma desde hoy (o desde su vencimiento si aún le quedan días).")
    }) {
        DCard(index = 0) {
            SectionTitle(Icons.Outlined.WorkspacePremium, "Activar o renovar por correo")
            OutlinedTextField(
                newEmail, { newEmail = it }, singleLine = true, label = { Text("Correo del cliente") },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = Yellow, cursorColor = Yellow)
            )
            Spacer(Modifier.height(10.dp))
            PillButton("Elegir plan…", icon = Icons.Outlined.WorkspacePremium) {
                val e = newEmail.trim().lowercase()
                if (Patterns.EMAIL_ADDRESS.matcher(e).matches()) renewFor = e
                else Toast.makeText(ctx, "Escribe un correo válido", Toast.LENGTH_SHORT).show()
            }
        }
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
            val st = c.status(now)
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
                c.untilMs?.let {
                    Text("Vence: ${fmt(it)} (${rel(it, now)})", fontSize = 12.sp, color = Ink, modifier = Modifier.padding(top = 8.dp))
                }
                if (c.plan.isNotBlank() && !c.isAdminRow) Text("Plan: ${c.plan}" + if (c.paidTotal > 0) " · Pagado en total: S/ %.2f".format(c.paidTotal) else "", fontSize = 12.sp, color = Muted)
                if (!c.isAdminRow) {
                    Spacer(Modifier.height(12.dp))
                    PillButton(if (c.hasDoc) "Renovar" else "Activar") { renewFor = c.email }
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

    renewFor?.let { email ->
        RenewDialog(email, clients.firstOrNull { it.email == email }?.untilMs, { renewFor = null }) { months, paid ->
            scope.launch {
                AdminRepo.grant(email, months, paid)
                    .onSuccess { Toast.makeText(ctx, "Listo. Vence el ${fmt(it)}", Toast.LENGTH_LONG).show(); newEmail = ""; renewFor = null; reload() }
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
private fun RenewDialog(email: String, currentUntil: Long?, onDismiss: () -> Unit, onConfirm: (months: Int, paid: Double?) -> Unit) {
    var months by remember { mutableStateOf("1") }
    var paid by remember { mutableStateOf(PRESETS.first().second.toInt().toString()) }
    val m = months.toIntOrNull()?.takeIf { it in 1..36 }
    val newUntil = m?.let { AdminRepo.addMonths(maxOf(System.currentTimeMillis(), currentUntil ?: 0L), it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renovar a $email", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column {
                Text("Elige cuánto pagó:", fontSize = 13.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { (mm, soles) ->
                        val sel = months == mm.toString() && paid == soles.toInt().toString()
                        Button(
                            { months = mm.toString(); paid = soles.toInt().toString() }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(if (sel) Yellow else Fog, Ink), contentPadding = PaddingValues(4.dp)
                        ) { Text("${if (mm == 1) "1 mes" else "$mm meses"} · S/${soles.toInt()}", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledField("Meses", months, Modifier.weight(1f), decimal = false) { months = it.filter(Char::isDigit).take(2) }
                    LabeledField("Pagó (S/)", paid, Modifier.weight(1f), decimal = true) { paid = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7) }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (newUntil != null) "Nuevo vencimiento: ${fmt(newUntil)}" else "Escribe de 1 a 36 meses",
                    fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (newUntil != null) Ink else Red
                )
                if (currentUntil != null && currentUntil > System.currentTimeMillis())
                    Text("Aún le quedan días: se suman al vencimiento actual.", fontSize = 11.sp, color = Muted)
            }
        },
        confirmButton = {
            TextButton({ if (m != null) onConfirm(m, paid.replace(',', '.').toDoubleOrNull()) }, enabled = m != null) {
                Text("Confirmar", color = Ink, fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar", color = Muted) } }
    )
}
