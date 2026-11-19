package com.drix.app.auth

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Una fila del panel de administrador (cliente registrado y/o con documento en allowed). */
data class Client(
    val email: String, val name: String, val phone: String, val plan: String,
    val untilMs: Long?, val trial: Boolean, val hasDoc: Boolean, val paidTotal: Double
) {
    val isAdminRow get() = hasDoc && plan == "admin"
}

/** Operaciones del administrador. Las reglas de Firestore solo las permiten si tu correo está en admins/{correo}. */
object AdminRepo {
    private suspend fun <T> Task<T>.wait(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
    }

    /** base + n meses (calendario: 12/11 → 12/12), en la zona horaria del celular. */
    fun addMonths(baseMs: Long, months: Int): Long =
        Instant.ofEpochMilli(baseMs).atZone(ZoneId.systemDefault()).plusMonths(months.toLong()).toInstant().toEpochMilli()

    suspend fun load(): Result<List<Client>> = runCatching {
        val db = FirebaseFirestore.getInstance()
        val allowed = db.collection("allowed").get(Source.SERVER).wait()
        val users = db.collection("users").get(Source.SERVER).wait()
        val byEmail = HashMap<String, Client>()
        users.documents.forEach { u ->
            val e = u.getString("email")?.trim()?.lowercase().orEmpty()
            if (e.isNotBlank()) byEmail[e] = Client(e, u.getString("name").orEmpty(), u.getString("phone").orEmpty(), "", null, false, false, 0.0)
        }
        allowed.documents.forEach { a ->
            val e = a.id.lowercase()
            val base = byEmail[e] ?: Client(e, "", a.getString("phone").orEmpty(), "", null, false, false, 0.0)
            byEmail[e] = base.copy(
                plan = a.getString("plan").orEmpty(), untilMs = AuthRepo.untilOf(a),
                trial = a.getBoolean("trial") == true && a.getTimestamp("until") == null,
                hasDoc = true, paidTotal = a.getDouble("paidTotal") ?: 0.0
            )
        }
        val me = AuthRepo.user?.email?.trim()?.lowercase()
        byEmail.values.filter { it.email != me }.toList() // tú no apareces en tu propia lista
    }

    /**
     * Activa o renueva: suma [months] meses. Cuenta desde HOY si la cuenta es nueva o ya venció;
     * si todavía le quedan días, los suma al vencimiento actual (no pierde lo que ya pagó).
     * [paid] (opcional) queda registrado: último pago y total acumulado.
     */
    suspend fun grant(email: String, months: Int, paid: Double?): Result<Long> = runCatching {
        val ref = FirebaseFirestore.getInstance().collection("allowed").document(email.trim().lowercase())
        val snap = ref.get(Source.SERVER).wait()
        val cur = if (snap.exists()) AuthRepo.untilOf(snap) else null
        val until = addMonths(maxOf(System.currentTimeMillis(), cur ?: 0L), months)
        val data = mutableMapOf<String, Any>(
            "plan" to (if (months == 1) "1 mes" else "$months meses"),
            "until" to Timestamp(Date(until)), "trial" to false, "updatedAt" to FieldValue.serverTimestamp()
        )
        if (paid != null && paid > 0) {
            data["lastPaid"] = paid; data["lastPaidAt"] = FieldValue.serverTimestamp(); data["paidTotal"] = FieldValue.increment(paid)
        }
        ref.set(data, SetOptions.merge()).wait()
        until
    }

    suspend fun revoke(email: String): Result<Unit> = runCatching {
        FirebaseFirestore.getInstance().collection("allowed").document(email.trim().lowercase()).delete().wait(); Unit
    }

    /** Envía un aviso a todos los clientes. Las reglas de Firestore solo lo permiten al administrador. */
    suspend fun sendAnnouncement(title: String, message: String): Result<Unit> = runCatching {
        FirebaseFirestore.getInstance().collection("announcements").add(
            mapOf(
                "title" to title.trim(), "message" to message.trim(),
                "createdAt" to FieldValue.serverTimestamp(), "by" to AuthRepo.user?.email.orEmpty()
            )
        ).wait()
        Unit
    }
}
