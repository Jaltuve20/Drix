package com.drix.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Perfil guardado en Firestore (users/{uid}). */
data class Profile(val name: String? = null, val phone: String? = null)

/** Todo el acceso a Firebase (Auth + Firestore) en un solo lugar. */
object AuthRepo {
    private val auth get() = FirebaseAuth.getInstance()
    val user: FirebaseUser? get() = auth.currentUser

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

    suspend fun signUp(name: String, email: String, phone: String, pass: String): Result<Unit> = runCatching {
        val res = auth.createUserWithEmailAndPassword(email.trim(), pass).wait()
        val u = res.user ?: return@runCatching
        u.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).wait()
        // El teléfono y el nombre también van a Firestore; si Firestore no está activado, la cuenta se crea igual.
        runCatching {
            FirebaseFirestore.getInstance().collection("users").document(u.uid)
                .set(mapOf("name" to name.trim(), "phone" to phone.trim(), "email" to email.trim())).wait()
        }
        u.reload().wait()
        Unit
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

    suspend fun profile(uid: String): Profile = runCatching {
        val d = FirebaseFirestore.getInstance().collection("users").document(uid).get().wait()
        Profile(d.getString("name"), d.getString("phone"))
    }.getOrDefault(Profile())

    /** Crea el perfil en Firestore si no existe (p. ej. cuentas nuevas de Google). */
    suspend fun ensureProfile() {
        runCatching {
            val u = user ?: return
            val ref = FirebaseFirestore.getInstance().collection("users").document(u.uid)
            if (!ref.get().wait().exists()) {
                ref.set(mapOf("name" to u.displayName.orEmpty(), "email" to u.email.orEmpty(), "phone" to ""), SetOptions.merge()).wait()
            }
        }
    }

    /** Guarda nombre y teléfono (Auth + Firestore). */
    suspend fun saveProfile(name: String, phone: String): Result<Unit> = runCatching {
        val u = user ?: error("No hay sesión iniciada.")
        u.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).wait()
        FirebaseFirestore.getInstance().collection("users").document(u.uid)
            .set(mapOf("name" to name.trim(), "phone" to phone.trim(), "email" to u.email.orEmpty()), SetOptions.merge()).wait()
        Unit
    }

    /** Envía el enlace para cambiar la contraseña al correo de la cuenta actual. */
    suspend fun resetForCurrentUser(): Result<Unit> {
        val mail = user?.email ?: return Result.failure(IllegalStateException("Tu cuenta no tiene correo."))
        return reset(mail)
    }

    fun signOut() = auth.signOut()

    /** Traduce los errores de Firebase a mensajes claros en español. */
    fun message(e: Throwable): String? = when (e) {
        is GetCredentialCancellationException -> null // el usuario cerró el selector de Google
        is FirebaseAuthWeakPasswordException -> "La contraseña es muy débil (mínimo 6 caracteres)."
        is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese correo."
        is FirebaseAuthInvalidUserException -> "No existe una cuenta con ese correo."
        is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos."
        is FirebaseNetworkException -> "Sin conexión a internet."
        is FirebaseTooManyRequestsException -> "Demasiados intentos. Espera un momento."
        else -> e.message ?: "Ocurrió un error. Inténtalo de nuevo."
    }
}
