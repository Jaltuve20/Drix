package com.drix.app.ui

import android.util.Patterns
import androidx.compose.foundation.background
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

@Composable
fun SplashScreen() = Box(
    Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(YellowLight, Yellow))), Alignment.Center
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DrixWordmark(72.dp)
        Spacer(Modifier.height(18.dp))
        Text("Entrega más.", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        Text("Gana más.", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
    }
}

/* ═════════════ COMPONENTES ═════════════ */

@Composable
private fun AuthField(
    value: String, onChange: (String) -> Unit, placeholder: String, icon: ImageVector,
    keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false
) {
    var show by remember { mutableStateOf(false) }
    val trailing: @Composable () -> Unit = {
        IconButton({ show = !show }) {
            Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Mostrar contraseña", tint = Muted)
        }
    }
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(placeholder, color = Muted, fontSize = 14.sp) },
        leadingIcon = { Icon(icon, null, tint = Muted) },
        trailingIcon = if (password) trailing else null,
        visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = YellowDeep, cursorColor = Ink)
    )
}

@Composable
private fun MainButton(text: String, loading: Boolean, onClick: () -> Unit) = Button(
    onClick, Modifier.fillMaxWidth().height(52.dp), enabled = !loading, shape = RoundedCornerShape(14.dp),
    colors = ButtonDefaults.buttonColors(Yellow, Ink, Yellow.copy(alpha = .6f), Ink)
) {
    if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Ink, strokeWidth = 2.5.dp)
    else Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
}

@Composable
private fun AuthPage(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().background(Color.White).systemBarsPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Spacer(Modifier.height(12.dp))
    LogoMark(64.dp)
    Spacer(Modifier.height(18.dp))
    Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
    Text(subtitle, fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp), textAlign = TextAlign.Center)
    content()
}

@Composable
private fun Note(text: String?, error: Boolean) {
    if (text != null) Text(text, color = if (error) Color(0xFFDC2626) else Color(0xFF15803D), fontSize = 13.sp,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
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
        AuthField(email, { email = it }, "Correo electrónico", Icons.Outlined.Email, KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        AuthField(pass, { pass = it }, "Contraseña", Icons.Outlined.Lock, password = true)
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
        Spacer(Modifier.height(20.dp))
        Text("o continúa con", fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                loading = true; note = null
                scope.launch { AuthRepo.google(ctx).onFailure(::fail); loading = false }
            },
            enabled = !loading, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
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
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    AuthPage("Crear Cuenta", "Únete a Drix y empieza a ganar") {
        AuthField(name, { name = it }, "Nombre completo", Icons.Outlined.Person)
        Spacer(Modifier.height(12.dp))
        AuthField(email, { email = it }, "Correo electrónico", Icons.Outlined.Email, KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        AuthField(phone, { phone = it }, "Teléfono", Icons.Outlined.Phone, KeyboardType.Phone)
        Spacer(Modifier.height(12.dp))
        AuthField(pass, { pass = it }, "Contraseña (mín. 6 caracteres)", Icons.Outlined.Lock, password = true)
        Spacer(Modifier.height(12.dp))
        AuthField(pass2, { pass2 = it }, "Confirmar contraseña", Icons.Outlined.Lock, password = true)
        Spacer(Modifier.height(20.dp))
        MainButton("Registrarse", loading) {
            note = when {
                name.isBlank() -> "Escribe tu nombre."
                !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Escribe un correo válido."
                phone.filter { it.isDigit() }.length < 7 -> "Escribe un teléfono válido."
                pass.length < 6 -> "La contraseña debe tener al menos 6 caracteres."
                pass != pass2 -> "Las contraseñas no coinciden."
                else -> null
            }
            if (note == null) {
                loading = true
                scope.launch { AuthRepo.signUp(name, email, phone, pass).onFailure { note = AuthRepo.message(it) }; loading = false }
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
