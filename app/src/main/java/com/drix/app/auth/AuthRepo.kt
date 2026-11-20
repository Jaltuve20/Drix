package com.drix.app.auth

import android.content.Context
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
    /** true si el correo está en admins/{correo}: panel de administrador. */
    val isAdmin: Boolean = false
) {
    val complete get() = !firstName.isNullOrBlank() && !lastName.isNullOrBlank() && !phone.isNullOrBlank()
    val fullName get() = listOfNotNull(firstName, lastName).joinToString(" ").trim()
}

/** El correo no está en la lista de acceso (allowed/{correo}) o la sesión se abrió en otro teléfono. */
class AccessDenied(val expired: Boolean) : Exception(if (expired) "Acceso vencido" else "Sin acceso")

object AuthRepo {
    private val auth get() = FirebaseAuth.getInstance()
    private lateinit var app: Context
    fun init(ctx: Context) { app = ctx.applicationContext }

    val user: FirebaseUser? get() = auth.currentUser

    /** true mientras se crea una cuenta (evita mostrar el formulario de perfil por un instante). */
    val busy = MutableStateFlow(false)
    /** Súbelo para volver a cargar el perfil desde Firestore. */
    val version = MutableStateFlow(0)
    /** Correo de la cuenta cuya sesión se cerró porque se abrió en otro teléfono (la UI muestra el aviso). */
    val kicked = MutableStateFlow<String?>(null)

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

    /** Resultado de comprobar el acceso del correo actual. */
    data class AccessInfo(val admin: Boolean)

    /**
     * Comprueba en el SERVIDOR (Source.SERVER: un acceso revocado no sigue funcionando desde la caché) si el correo actual
     * tiene acceso y actualiza DrixPrefs (el servicio de accesibilidad solo trabaja con acceso vigente).
     *  - admins/{correo}  → administrador: panel de administrador.
     *  - allowed/{correo} → cliente verificado por ti: acceso DE POR VIDA (sin vencimiento).
     * Además exige que esta sea la ÚNICA sesión abierta de la cuenta (ver ensureSession).
     * Lanza AccessDenied si no hay acceso.
     */
    private suspend fun access(): AccessInfo {
        val db = FirebaseFirestore.getInstance()
        val email = user?.email?.trim()?.lowercase().orEmpty()
        fun deny(expired: Boolean): Nothing { DrixPrefs.revokeAccess(); throw AccessDenied(expired) }
        if (email.isBlank()) deny(false)
        val admin = runCatching { db.collection("admins").document(email).get(Source.SERVER).wait().exists() }.getOrDefault(false)
        if (admin) { DrixPrefs.grantAccess(); return AccessInfo(true) }
        val a = db.collection("allowed").document(email).get(Source.SERVER).wait()
        // Los documentos de la antigua prueba gratis (trial = true) ya no dan acceso: hay que activarlos desde el panel.
        if (!a.exists() || a.getBoolean("trial") == true) deny(false)
        ensureSession()
        DrixPrefs.grantAccess()
        return AccessInfo(false)
    }

    /* ── Sesión única: una cuenta solo puede estar abierta en UN teléfono ── */

    private fun sessionPrefs() = app.getSharedPreferences("drix_auth", Context.MODE_PRIVATE)
    private fun sidKey(uid: String) = "sid_$uid"

    /** Este teléfono pasa a ser la sesión activa de la cuenta: escribe un identificador nuevo en sessions/{uid}. */
    private suspend fun claimSession(uid: String) {
        val sid = java.util.UUID.randomUUID().toString()
        FirebaseFirestore.getInstance().collection("sessions").document(uid)
            .set(mapOf("sid" to sid, "device" to android.os.Build.MODEL.orEmpty().take(60), "updatedAt" to FieldValue.serverTimestamp())).wait()
        sessionPrefs().edit().putString(sidKey(uid), sid).apply()
    }

    /**
     * Sin identificador local (inicio de sesión nuevo) → este teléfono reclama la sesión y el otro se cierra solo.
     * Con identificador local → se compara con el del servidor: si es otro, alguien entró desde otro teléfono → se cierra esta sesión.
     */
    private suspend fun ensureSession() {
        val u = user ?: return
        val local = sessionPrefs().getString(sidKey(u.uid), null)
        if (local == null) { claimSession(u.uid); return }
        val server = FirebaseFirestore.getInstance().collection("sessions").document(u.uid).get(Source.SERVER).wait().getString("sid")
        if (server == null) { claimSession(u.uid); return }
        if (server != local) { kick(); throw AccessDenied(false) }
    }

    /** Cierra esta sesión porque la cuenta se abrió en otro teléfono, y deja el aviso para la pantalla. */
    fun kick() {
        val u = user ?: return
        kicked.value = u.email.orEmpty()
        signOut()
    }

    /**
     * TIEMPO REAL: si la cuenta se abre en otro teléfono, esta sesión se cierra en segundos (app abierta o servicio activo).
     * Los administradores no reclaman sesión, por lo que nunca se les cierra.
     */
    fun watchSession(): () -> Unit {
        val u = user ?: return {}
        val reg = FirebaseFirestore.getInstance().collection("sessions").document(u.uid).addSnapshotListener { snap, err ->
            if (err != null || snap == null || snap.metadata.isFromCache) return@addSnapshotListener
            val local = sessionPrefs().getString(sidKey(u.uid), null) ?: return@addSnapshotListener
            val server = snap.getString("sid") ?: return@addSnapshotListener
            if (server != local) kick()
        }
        return { reg.remove() }
    }

    /** Vuelve a comprobar el acceso (lo usa el servicio cuando el administrador cambia algo en tiempo real). */
    suspend fun checkAccess(): Result<AccessInfo> = runCatching { access() }

    /**
     * TIEMPO REAL: avisa cuando el administrador cambia el acceso de este correo (quitar, activar, renovar).
     * Escucha allowed/{correo} y admins/{correo}. Devuelve una función para dejar de escuchar.
     */
    fun watchAccess(onChange: () -> Unit): () -> Unit {
        val email = user?.email?.trim()?.lowercase().orEmpty()
        if (email.isBlank()) return {}
        val db = FirebaseFirestore.getInstance()
        val regs = listOf("allowed", "admins").map { col ->
            var first = true
            db.collection(col).document(email).addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener                 // sin red / sin permiso: se ignora
                if (first) { first = false; return@addSnapshotListener }    // la 1.ª lectura es el estado actual, no un cambio
                // "No existe" leído solo de la caché local no cuenta (evita expulsar por datos viejos sin conexión)
                if (snap != null && snap.metadata.isFromCache && !snap.exists()) return@addSnapshotListener
                onChange()
            }
        }
        return { regs.forEach { it.remove() } }
    }

    /** Carga el perfil. Éxito con null = la cuenta aún no tiene perfil (hay que pedir los datos). */
    suspend fun loadProfile(ctx: Context, uid: String): Result<Profile?> = runCatching {
        val acc = access()
        val d = FirebaseFirestore.getInstance().collection("users").document(uid).get().wait()
        if (!d.exists()) null else {
            val local = ctx.getSharedPreferences("drix_auth", Context.MODE_PRIVATE).getBoolean("tut_$uid", false)
            // Cuentas antiguas (sin el campo tutorialDone) cuentan como "tutorial ya visto": solo lo ven los nuevos.
            Profile(d.getString("firstName"), d.getString("lastName"), d.getString("phone"),
                (d.getBoolean("tutorialDone") ?: true) || local, acc.admin)
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
        runCatching { u.updateProfile(UserProfileChangeRequest.Builder().setDisplayName("$first $last").build()).wait() }
        Unit
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
    fun signOut() {
        user?.uid?.let { if (::app.isInitialized) sessionPrefs().edit().remove(sidKey(it)).apply() }
        DrixPrefs.revokeAccess(); auth.signOut()
    }

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
