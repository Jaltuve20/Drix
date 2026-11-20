package com.drix.app.auth

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Una fila del panel de administrador. hasDoc = ya verificado/activado (documento en allowed, de por vida). */
data class Client(
    val email: String, val name: String, val phone: String, val plan: String,
    val hasDoc: Boolean, val paidTotal: Double
) {
    val isAdminRow get() = hasDoc && plan == "admin"
}

/** Una fila del ranking mensual: pedidos que Drix aceptó a ese cliente en el mes. */
data class RankRow(val email: String, val name: String, val orders: Int)

/** Operaciones del administrador. Las reglas de Firestore solo las permiten si tu correo está en admins/{correo}. */
object AdminRepo {
    private suspend fun <T> Task<T>.wait(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
    }

    suspend fun load(): Result<List<Client>> = runCatching {
        val db = FirebaseFirestore.getInstance()
        val allowed = db.collection("allowed").get(Source.SERVER).wait()
        val users = db.collection("users").get(Source.SERVER).wait()
        val byEmail = HashMap<String, Client>()
        users.documents.forEach { u ->
            val e = u.getString("email")?.trim()?.lowercase().orEmpty()
            if (e.isNotBlank()) byEmail[e] = Client(e, u.getString("name").orEmpty(), u.getString("phone").orEmpty(), "", false, 0.0)
        }
        allowed.documents.forEach { a ->
            val e = a.id.lowercase()
            val base = byEmail[e] ?: Client(e, "", a.getString("phone").orEmpty(), "", false, 0.0)
            // Los documentos de la antigua prueba gratis (trial = true) cuentan como NO activados.
            val active = a.getBoolean("trial") != true
            byEmail[e] = base.copy(plan = a.getString("plan").orEmpty(), hasDoc = active, paidTotal = a.getDouble("paidTotal") ?: 0.0)
        }
        val me = AuthRepo.user?.email?.trim()?.lowercase()
        byEmail.values.filter { it.email != me }.toList() // tú no apareces en tu propia lista
    }

    /**
     * Verifica y activa un correo DE POR VIDA (pago único). Sin vencimiento: si el documento traía un "until" o "trial"
     * de la época de las suscripciones, se borra. [paid] (opcional) queda registrado: último pago y total acumulado.
     */
    suspend fun grant(email: String, paid: Double?): Result<Unit> = runCatching {
        val ref = FirebaseFirestore.getInstance().collection("allowed").document(email.trim().lowercase())
        val data = mutableMapOf<String, Any>(
            "plan" to "de por vida", "trial" to false, "until" to FieldValue.delete(), "updatedAt" to FieldValue.serverTimestamp()
        )
        if (paid != null && paid > 0) {
            data["lastPaid"] = paid; data["lastPaidAt"] = FieldValue.serverTimestamp(); data["paidTotal"] = FieldValue.increment(paid)
        }
        ref.set(data, SetOptions.merge()).wait()
        Unit
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

    /** Pedidos aceptados por cliente en el mes [month] («yyyy-MM»), de mayor a menor. */
    suspend fun loadRanking(month: String): Result<List<RankRow>> = runCatching {
        FirebaseFirestore.getInstance().collection("monthly").whereEqualTo("month", month).get(Source.SERVER).wait()
            .documents.mapNotNull { d ->
                val n = (d.getLong("orders") ?: 0L).toInt()
                if (n <= 0) null else RankRow(d.getString("email").orEmpty(), d.getString("name").orEmpty(), n)
            }.sortedByDescending { it.orders }
    }
}
