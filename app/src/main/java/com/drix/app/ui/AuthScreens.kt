package com.drix.app.ui

import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drix.app.auth.AuthRepo
import kotlinx.coroutines.launch

/* ═════════════ SPLASH ═════════════ */

/** Entrada orquestada: anillos de radar, logo que rebota, lema en dos tiempos y puntos de carga. */
@Composable
fun SplashScreen() {
    val logo = remember { Animatable(0f) }
    val l1 = remember { Animatable(0f) }
    val l2 = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { logo.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
        delay(420); launch { l1.animateTo(1f, tween(520)) }
        delay(160); launch { l2.animateTo(1f, tween(520)) }
    }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(YellowLight, Yellow))).heroMotion(),
        Alignment.Center
    ) {
        RadarRings(Ink, Modifier.size(380.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.graphicsLayer { val v = logo.value; scaleX = .55f + .45f * v; scaleY = .55f + .45f * v; alpha = v.coerceIn(0f, 1f) }) {
                DrixWordmark(72.dp)
            }
            Spacer(Modifier.height(20.dp))
            Text("Entrega más.", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink,
                modifier = Modifier.graphicsLayer { alpha = l1.value; translationY = (1f - l1.value) * 24.dp.toPx() })
            Text("Gana más.", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink,
                modifier = Modifier.graphicsLayer { alpha = l2.value; translationY = (1f - l2.value) * 24.dp.toPx() })
        }
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 48.dp)) { LoadingDots(Ink) }
    }
}

/* ═════════════ COMPONENTES ═════════════ */

@Composable
private fun AuthField(
    value: String, onChange: (String) -> Unit, placeholder: String, icon: ImageVector,
    keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false, index: Int = 0
) {
    var show by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val iconTint by animateColorAsState(if (focused) Amber else Muted, tween(200), label = "ft")
    val trailing: @Composable () -> Unit = {
        IconButton({ show = !show }) {
            Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Mostrar contraseña", tint = Muted)
        }
    }
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth().enter(index).onFocusChanged { focused = it.isFocused }, singleLine = true,
        placeholder = { Text(placeholder, color = Muted, fontSize = 14.sp) },
        leadingIcon = { Icon(icon, null, tint = iconTint) },
        trailingIcon = if (password) trailing else null,
        visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Line, focusedBorderColor = YellowDeep, cursorColor = Ink,
            unfocusedContainerColor = Fog, focusedContainerColor = Color.White
        )
    )
}

@Composable
private fun MainButton(text: String, loading: Boolean, onClick: () -> Unit) =
    PillButton(text, loading = loading, onClick = onClick)

/** Cabecera amarilla viva con logo que «salta» al entrar, y el formulario debajo sobre blanco. */
@Composable
private fun AuthPage(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().background(Color.White).imePadding().verticalScroll(rememberScrollState())
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(Brush.linearGradient(listOf(YellowLight, YellowDeep)))
            .heroMotion()
            .statusBarsPadding()
            .padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LogoMark(68.dp, Modifier.popIn().shadow(12.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)))
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Ink, letterSpacing = (-0.5).sp, textAlign = TextAlign.Center, modifier = Modifier.enter(1))
        Text(subtitle, fontSize = 14.sp, color = Ink.copy(alpha = .75f), lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp).enter(2), textAlign = TextAlign.Center)
    }
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 26.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally, content = content
    )
}

/** Mensaje de error/éxito: aparece desplegándose; los errores «tiemblan» una vez. */
@Composable
private fun Note(text: String?, error: Boolean) {
    AnimatedContent(
        text, Modifier.fillMaxWidth(),
        transitionSpec = { (fadeIn(tween(250)) + expandVertically()) togetherWith fadeOut(tween(120)) },
        label = "note"
    ) { t ->
        if (t != null) {
            val shake = remember { Animatable(0f) }
            LaunchedEffect(t) {
                if (error) {
                    repeat(3) { shake.animateTo(10f, tween(50)); shake.animateTo(-10f, tween(50)) }
                    shake.animateTo(0f, tween(50))
                }
            }
            Text(t, color = if (error) Danger else Color(0xFF15803D), fontSize = 13.sp, lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).graphicsLayer { translationX = shake.value })
        } else Spacer(Modifier.height(0.dp))
    }
}

/** Separador «o continúa con» con líneas a los lados. */
@Composable
private fun OrDivider(text: String) = Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.weight(1f).height(1.dp).background(Line))
    Text(text, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(horizontal = 12.dp))
    Box(Modifier.weight(1f).height(1.dp).background(Line))
}

/* ═════════════ INICIAR SESIÓN ═════════════ */

@Composable
fun LoginScreen(onSignUp: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(true) }

    fun fail(t: Throwable) { note = AuthRepo.message(t); isError = true }

    AuthPage("Iniciar Sesión", "Bienvenido de vuelta") {
        AuthField(email, { email = it }, "Correo electrónico", Icons.Outlined.Email, KeyboardType.Email, index = 0)
        Spacer(Modifier.height(12.dp))
        AuthField(pass, { pass = it }, "Contraseña", Icons.Outlined.Lock, password = true, index = 1)
        Spacer(Modifier.height(20.dp))
        MainButton("Ingresar", loading) {
            if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { note = "Escribe un correo válido."; isError = true }
            else if (pass.isEmpty()) { note = "Escribe tu contraseña."; isError = true }
            else {
                loading = true; note = null
                scope.launch { AuthRepo.signIn(email, pass).onFailure(::fail); loading = false }
            }
        }
        Note(note, isError)
        OrDivider("o continúa con")
        OutlinedButton(
            onClick = {
                loading = true; note = null
                scope.launch { AuthRepo.google(ctx).onFailure(::fail); loading = false }
            },
            enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp).enter(3), shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line)
        ) {
            Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text("Continuar con Google", color = Ink, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))
        Text("¿Olvidaste tu contraseña?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink,
            modifier = Modifier.clickable {
                if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { note = "Escribe tu correo arriba y vuelve a tocar aquí."; isError = true }
                else scope.launch {
                    AuthRepo.reset(email).onSuccess { note = "Te enviamos un correo para restablecer tu contraseña."; isError = false }.onFailure(::fail)
                }
            }.padding(8.dp))
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("¿No tienes cuenta? ", fontSize = 13.sp, color = Muted)
            Text("Regístrate", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.clickable(onClick = onSignUp).padding(6.dp))
        }
    }
}

/* ═════════════ CREAR CUENTA ═════════════ */

@Composable
fun SignUpScreen(onLogin: () -> Unit) {
    val scope = rememberCoroutineScope()
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    AuthPage("Crear Cuenta", "Únete a Drix y empieza a ganar") {
        AuthField(first, { first = it }, "Nombres", Icons.Outlined.Person, index = 0)
        Spacer(Modifier.height(12.dp))
        AuthField(last, { last = it }, "Apellidos", Icons.Outlined.Person, index = 1)
        Spacer(Modifier.height(12.dp))
        AuthField(email, { email = it }, "Correo electrónico", Icons.Outlined.Email, KeyboardType.Email, index = 2)
        Spacer(Modifier.height(12.dp))
        AuthField(phone, { phone = it }, "Celular (ej. 987654321)", Icons.Outlined.Phone, KeyboardType.Phone, index = 3)
        LockNote()
        Spacer(Modifier.height(12.dp))
        AuthField(pass, { pass = it }, "Contraseña (mín. 6 caracteres)", Icons.Outlined.Lock, password = true, index = 4)
        Spacer(Modifier.height(12.dp))
        AuthField(pass2, { pass2 = it }, "Confirmar contraseña", Icons.Outlined.Lock, password = true, index = 5)
        Spacer(Modifier.height(20.dp))
        MainButton("Registrarse", loading) {
            note = when {
                !validName(first) -> "Escribe tus nombres (solo letras)."
                !validName(last) -> "Escribe tus apellidos (solo letras)."
                !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Escribe un correo válido."
                normalizePhone(phone) == null -> "Escribe un celular válido (9 dígitos, ej. 987654321)."
                pass.length < 6 -> "La contraseña debe tener al menos 6 caracteres."
                pass != pass2 -> "Las contraseñas no coinciden."
                else -> null
            }
            if (note == null) {
                loading = true
                scope.launch { AuthRepo.signUp(cleanName(first), cleanName(last), email, normalizePhone(phone)!!, pass).onFailure { note = AuthRepo.message(it) }; loading = false }
            }
        }
        Note(note, true)
        Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("¿Ya tienes cuenta? ", fontSize = 13.sp, color = Muted)
            Text("Inicia sesión", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.clickable(onClick = onLogin).padding(6.dp))
        }
    }
}

@Composable
fun AuthFlow() {
    var signup by remember { mutableStateOf(false) }
    if (signup) SignUpScreen { signup = false } else LoginScreen { signup = true }
}

/* ═════════ DATOS DEL PERFIL (validación) ═════════ */

private val NAME_RX = Regex("^[\\p{L}][\\p{L} '\\-]{1,39}$")
fun validName(s: String) = NAME_RX.matches(s.trim())
fun cleanName(s: String) = s.trim().split(Regex("\\s+")).joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }

/** Celular peruano de 9 dígitos (9XXXXXXXX) → +51…; o número internacional con "+". Devuelve null si no es válido. */
fun normalizePhone(raw: String): String? {
    val plus = raw.trim().startsWith("+")
    val d = raw.filter { it.isDigit() }
    return when {
        !plus && d.length == 9 && d.startsWith("9") -> "+51$d"
        !plus && d.length == 11 && d.startsWith("51") -> "+$d"
        plus && d.length in 9..15 -> "+$d"
        else -> null
    }
}
fun prettyPhone(p: String) = if (p.startsWith("+51") && p.length == 12) "+51 ${p.substring(3, 6)} ${p.substring(6, 9)} ${p.substring(9)}" else p

@Composable
private fun LockNote() = Row(
    Modifier.fillMaxWidth().padding(top = 14.dp).background(Tint, RoundedCornerShape(16.dp)).padding(14.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Icon(Icons.Outlined.Lock, null, tint = Amber, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(10.dp))
    Text("Tus nombres y tu teléfono no se podrán cambiar después. Si necesitas corregirlos, será por WhatsApp.", fontSize = 12.sp, color = Ink)
}

/* ═════════ COMPLETAR REGISTRO (cuentas de Google / sin perfil) ═════════ */

@Composable
fun CompleteProfileScreen(email: String) {
    val scope = rememberCoroutineScope()
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    AuthPage("Completa tu registro", "Un último paso para activar tu cuenta") {
        if (email.isNotBlank()) Text(email, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(bottom = 14.dp))
        AuthField(first, { first = it }, "Nombres", Icons.Outlined.Person, index = 0)
        Spacer(Modifier.height(12.dp))
        AuthField(last, { last = it }, "Apellidos", Icons.Outlined.Person, index = 1)
        Spacer(Modifier.height(12.dp))
        AuthField(phone, { phone = it }, "Celular (ej. 987654321)", Icons.Outlined.Phone, KeyboardType.Phone, index = 2)
        LockNote()
        Spacer(Modifier.height(20.dp))
        MainButton("Continuar", loading) {
            note = when {
                !validName(first) -> "Escribe tus nombres (solo letras)."
                !validName(last) -> "Escribe tus apellidos (solo letras)."
                normalizePhone(phone) == null -> "Escribe un celular válido (9 dígitos, ej. 987654321)."
                else -> null
            }
            if (note == null) confirm = true
        }
        Note(note, true)
        Text("Usar otra cuenta", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink,
            modifier = Modifier.padding(top = 18.dp).clickable { AuthRepo.signOut() }.padding(8.dp))
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, containerColor = Color.White,
        title = { Text("¿Son correctos tus datos?", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text("${cleanName(first)} ${cleanName(last)}", fontWeight = FontWeight.Bold)
                Text(prettyPhone(normalizePhone(phone).orEmpty()), color = Ink)
                Text("No podrás cambiarlos desde la app.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton({
                confirm = false; loading = true
                scope.launch {
                    AuthRepo.createProfile(cleanName(first), cleanName(last), normalizePhone(phone)!!)
                        .onSuccess { AuthRepo.version.value++ }
                        .onFailure { note = AuthRepo.message(it) }
                    loading = false
                }
            }) { Text("Sí, guardar", fontWeight = FontWeight.ExtraBold, color = Ink) }
        },
        dismissButton = { TextButton({ confirm = false }) { Text("Corregir", color = Muted) } }
    )
}

@Composable
fun ProfileLoadErrorScreen(msg: String, onRetry: () -> Unit, onSignOut: () -> Unit) =
    AuthPage("No pudimos cargar tu perfil", msg) {
        MainButton("Reintentar", false, onRetry)
        TextButton(onSignOut, Modifier.padding(top = 10.dp)) { Text("Cerrar sesión", color = Ink) }
    }

/** Pantalla para cuentas cuyo correo aún no fue verificado/habilitado por el administrador. */
@Composable
fun NoAccessScreen(email: String, expired: Boolean, onRetry: () -> Unit, onSignOut: () -> Unit) {
    val ctx = LocalContext.current
    AuthPage(
        "Acceso pendiente",
        "Tu correo $email todavía no está habilitado. Escríbenos por WhatsApp con ese correo, verificamos tu compra y te damos acceso."
    ) {
        MainButton("Ya me habilitaron · Reintentar", false, onRetry)
        TextButton({
            val msg = "Hola Drix, quiero acceso a la app.\nCorreo: $email"
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/51900385006?text=" + Uri.encode(msg))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, Modifier.padding(top = 10.dp)) { Text("💬 Pedir acceso por WhatsApp", color = Ink, fontWeight = FontWeight.Bold) }
        TextButton(onSignOut) { Text("Cerrar sesión", color = Ink) }
    }
}

/** Se muestra cuando la cuenta se abrió en otro teléfono: esta sesión ya se cerró. */
@Composable
fun KickedScreen(email: String, onLogin: () -> Unit) {
    AuthPage("Sesión cerrada", "La cuenta $email se abrió en otro teléfono, por eso cerramos esta sesión. Una cuenta solo puede estar abierta en un teléfono a la vez.") {
        MainButton("Iniciar sesión de nuevo", false, onLogin)
    }
}
