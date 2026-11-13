package com.drix.app.auth

import android.content.Context
import android.provider.Settings
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.drix.app.data.DrixPrefs
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Perfil guardado en Firestore (users/{uid}). Nombres y teléfono se piden UNA vez y quedan BLOQUEADOS:
 * las reglas de Firestore (firestore.rules) impiden modificarlos desde la app. Cambios → por WhatsApp.
 */
data class Profile(
    val firstName: String? = null, val lastName: String? = null, val phone: String? = null,
    val tutorialDone: Boolean = true,
    /** Fecha de vencimiento del acceso (ms desde 1970), o null si no vence. Viene de allowed/{correo}. */
    val until: Long? = null,
    /** true si el correo está en admins/{correo}: acceso sin vencimiento + panel de administrador. */
    val isAdmin: Boolean = false,
    /** true mientras la cuenta está en su prueba gratis (1 día). */
    val isTrial: Boolean = false
) {
    val complete get() = !firstName.isNullOrBlank() && !lastName.isNullOrBlank() && !phone.isNullOrBlank()
    val fullName get() = listOfNotNull(firstName, lastName).joinToString(" ").trim()
}

/** El correo no está en la lista de acceso (allowed/{correo}) o su acceso venció. */
class AccessDenied(val expired: Boolean) : Exception(if (expired) "Acceso vencido" else "Sin acceso")

object AuthRepo {
    private val auth get() = FirebaseAuth.getInstance()
    private lateinit var app: Context
    fun init(ctx: Context) { app = ctx.applicationContext }

    /** Vencimiento (ms) de un documento allowed/{correo}: campo "until"; si es prueba gratis, createdAt + 1 día; si no, null (no vence). */
    fun untilOf(d: DocumentSnapshot): Long? =
        d.getTimestamp("until")?.toDate()?.time
            ?: if (d.getBoolean("trial") == true) (d.getTimestamp("createdAt")?.toDate()?.time ?: 0L) + 86_400_000L else null
    val user: FirebaseUser? get() = auth.currentUser

    /** true mientras se crea una cuenta (evita mostrar el formulario de perfil por un instante). */
    val busy = MutableStateFlow(false)
    /** Súbelo para volver a cargar el perfil desde Firestore. */
    val version = MutableStateFlow(0)

    private suspend fun <T> Task<T>.wait(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
    }

    /** Avisa cuando el usuario inicia o cierra sesión. Devuelve una función para dejar de escuchar. */
    fun listen(onChange: (FirebaseUser?) -> Unit): () -> Unit {
        val l = FirebaseAuth.AuthStateListener { onChange(it.currentUser) }
        auth.addAuthStateListener(l)
        return { auth.removeAuthStateListener(l) }
    }

    suspend fun signIn(email: String, pass: String): Result<Unit> =
        runCatching { auth.signInWithEmailAndPassword(email.trim(), pass).wait(); Unit }

    suspend fun signUp(first: String, last: String, email: String, phone: String, pass: String): Result<Unit> = runCatching {
        busy.value = true
        try {
            val u = auth.createUserWithEmailAndPassword(email.trim(), pass).wait().user ?: return@runCatching
            // Si Firestore falla, la cuenta ya existe: al entrar, la app volverá a pedir estos datos.
            createProfile(first, last, phone)
            u.reload().wait()
        } finally { busy.value = false; version.value++ }
    }

    suspend fun reset(email: String): Result<Unit> =
        runCatching { auth.sendPasswordResetEmail(email.trim()).wait(); Unit }

    /** "Continuar con Google" con Credential Manager. Requiere activar Google en Firebase + SHA-1 (ver README). */
    suspend fun google(ctx: Context): Result<Unit> = runCatching {
        val id = ctx.resources.getIdentifier("default_web_client_id", "string", ctx.packageName)
        if (id == 0) error("Falta activar Google en Firebase y volver a descargar google-services.json (ver README, paso 6).")
        val option = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(ctx.getString(id)).build()
        val req = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val cred = CredentialManager.create(ctx).getCredential(ctx, req).credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val g = GoogleIdTokenCredential.createFrom(cred.data)
            auth.signInWithCredential(GoogleAuthProvider.getCredential(g.idToken, null)).wait()
            Unit
        } else error("Google no devolvió una cuenta válida.")
    }

    suspend fun reload() { runCatching { user?.reload()?.wait() } }

    /** Carga el perfil. Éxito con null = la cuenta aún no tiene perfil (hay que pedir los datos). */
    suspend fun loadProfile(ctx: Context, uid: String): Result<Profile?> = runCatching {
        val db = FirebaseFirestore.getInstance()
        val email = user?.email?.trim()?.lowercase().orEmpty()
        fun deny(expired: Boolean): Nothing { DrixPrefs.revokeAccess(); throw AccessDenied(expired) }
        if (email.isBlank()) deny(false)
        // 1) Control de acceso. Se lee SIEMPRE del servidor (Source.SERVER) para que un acceso revocado no siga funcionando desde la caché.
        //    - admins/{correo}  → administrador: sin vencimiento + panel.
        //    - allowed/{correo} → cliente: vence en "until" (o 1 día después de createdAt si es prueba gratis).
        val admin = runCatching { db.collection("admins").document(email).get(Source.SERVER).wait().exists() }.getOrDefault(false)
        var until: Long? = null
        var trial = false
        if (admin) DrixPrefs.grantAccess(null)
        else {
            val a = db.collection("allowed").document(email).get(Source.SERVER).wait()
            if (!a.exists()) deny(false)
            val u = untilOf(a)
            if (u != null && u < System.currentTimeMillis()) deny(true)
            until = u
            trial = a.getBoolean("trial") == true && a.getTimestamp("until") == null
            DrixPrefs.grantAccess(u) // el servicio de accesibilidad solo trabaja mientras haya acceso vigente
        }
        // 2) Perfil
        val d = db.collection("users").document(uid).get().wait()
        if (!d.exists()) null else {
            val local = ctx.getSharedPreferences("drix_auth", Context.MODE_PRIVATE).getBoolean("tut_$uid", false)
            // Cuentas antiguas (sin el campo tutorialDone) cuentan como "tutorial ya visto": solo lo ven los nuevos.
            Profile(d.getString("firstName"), d.getString("lastName"), d.getString("phone"),
                (d.getBoolean("tutorialDone") ?: true) || local, until, admin, trial)
        }
    }

    /** Guarda nombres + teléfono. Solo funciona mientras el perfil esté incompleto (lo exigen las reglas). */
    suspend fun createProfile(first: String, last: String, phone: String): Result<Unit> = runCatching {
        val u = user ?: error("No hay sesión iniciada.")
        val ref = FirebaseFirestore.getInstance().collection("users").document(u.uid)
        val existed = ref.get().wait().exists()
        val data = mutableMapOf<String, Any>(
            "firstName" to first, "lastName" to last, "name" to "$first $last", "phone" to phone, "email" to u.email.orEmpty()
        )
        if (!existed) { data["tutorialDone"] = false; data["createdAt"] = FieldValue.serverTimestamp() } // nuevo → verá el tutorial
        ref.set(data, SetOptions.merge()).wait()
        if (!existed) claimTrial(phone) // cuenta nueva → prueba gratis de 1 día (una por dispositivo y por celular)
        runCatching { u.updateProfile(UserProfileChangeRequest.Builder().setDisplayName("$first $last").build()).wait() }
        Unit
    }

    @android.annotation.SuppressLint("HardwareIds")
    private fun deviceId(): String {
        val raw = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val src = if (raw.isBlank() || raw == "9774d56d682e549c") {
            val sp = app.getSharedPreferences("drix_auth", Context.MODE_PRIVATE)
            "fallback-" + (sp.getString("dev", null) ?: java.util.UUID.randomUUID().toString().also { sp.edit().putString("dev", it).apply() })
        } else raw
        return MessageDigest.getInstance("SHA-256").digest(src.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
    }

    /**
     * Prueba gratis de 1 día: crea allowed/{correo} (trial) + dos "marcas" (dispositivo y celular) en un solo paso atómico.
     * Las reglas impiden repetirla: si el dispositivo o el celular ya la usaron, falla y la cuenta queda sin acceso.
     */
    private suspend fun claimTrial(phone: String) {
        if (!::app.isInitialized) return
        val email = user?.email?.trim()?.lowercase() ?: return
        val db = FirebaseFirestore.getInstance()
        val allowedRef = db.collection("allowed").document(email)
        if (runCatching { allowedRef.get(Source.SERVER).wait().exists() }.getOrDefault(true)) return
        val dev = deviceId()
        val claim = mapOf("email" to email, "createdAt" to FieldValue.serverTimestamp())
        db.batch()
            .set(db.collection("trialClaims").document("d_$dev"), claim)
            .set(db.collection("trialClaims").document("p_$phone"), claim)
            .set(allowedRef, mapOf("plan" to "prueba", "trial" to true, "deviceId" to dev, "phone" to phone, "createdAt" to FieldValue.serverTimestamp()))
            .commit().wait()
    }

    /** Marca el tutorial como visto (local + Firestore). */
    suspend fun markTutorialDone(ctx: Context) {
        val uid = user?.uid ?: return
        ctx.getSharedPreferences("drix_auth", Context.MODE_PRIVATE).edit().putBoolean("tut_$uid", true).apply()
        runCatching { FirebaseFirestore.getInstance().collection("users").document(uid).update("tutorialDone", true).wait() }
    }

    /** Envía el enlace para cambiar la contraseña al correo de la cuenta actual. */
    suspend fun resetForCurrentUser(): Result<Unit> {
        val mail = user?.email ?: return Result.failure(IllegalStateException("Tu cuenta no tiene correo."))
        return reset(mail)
    }

    /** Cerrar sesión también apaga la automatización. */
    fun signOut() { DrixPrefs.revokeAccess(); auth.signOut() }

    /** Traduce los errores de Firebase a mensajes claros en español. */
    fun message(e: Throwable): String? = when (e) {
        is GetCredentialCancellationException -> null // el usuario cerró el selector de Google
        is FirebaseAuthWeakPasswordException -> "La contraseña es muy débil (mínimo 6 caracteres)."
        is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese correo."
        is FirebaseAuthInvalidUserException -> "No existe una cuenta con ese correo."
        is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos."
        is FirebaseNetworkException -> "Sin conexión a internet."
        is FirebaseFirestoreException -> when (e.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Firestore rechazó la operación (PERMISSION_DENIED). Publica las reglas de firestore.rules y revisa que la API de Cloud Firestore esté habilitada (README, paso 5)."
            FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.NOT_FOUND ->
                "No se pudo llegar a Firestore (${e.code}). Revisa que creaste la base de datos (paso 5) y que app/google-services.json sea de ESE mismo proyecto. Detalle: ${e.message}"
            else -> "Error de base de datos (${e.code}): ${e.message}"
        }
        is FirebaseTooManyRequestsException -> "Demasiados intentos. Espera un momento."
        else -> e.message ?: "Ocurrió un error. Inténtalo de nuevo."
    }
}
