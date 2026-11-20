package com.drix.app.auth

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Una fila del panel de administrador. hasDoc = ya verificado/activado (documento en allowed, de por vida). */
data class Client(
    val email: String, val name: String, val phone: String, val plan: String,
    val hasDoc: Boolean, val paidTotal: Double, val rejected: Boolean = false
) {
    val isAdminRow get() = hasDoc && plan == "admin"
}

/** Solicitud de acceso pendiente (requests/{correo}): alguien se registró y espera que la actives o la rechaces. */
data class PendingRequest(val email: String, val name: String, val phone: String, val at: Long)

/** Una fila del ranking mensual: pedidos que Drix aceptó a ese cliente en el mes. */
data class RankRow(val email: String, val name: String, val orders: Int)

/** Un voto de una encuesta: quién votó, Sí (true) o No (false) y cuándo. */
data class Voter(val email: String, val name: String, val yes: Boolean, val at: Long)

/** Una encuesta del historial del administrador, con sus votos. */
data class PollRow(val id: String, val title: String, val description: String, val createdAt: Long, val closed: Boolean, val voters: List<Voter>) {
    val yes get() = voters.count { it.yes }
    val no get() = voters.count { !it.yes }
}

/** Operaciones del administrador. Las reglas de Firestore solo las permiten si tu correo está en admins/{correo}. */
object AdminRepo {
    private suspend fun <T> Task<T>.wait(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
    }

    suspend fun load(): Result<List<Client>> = runCatching {
        val db = FirebaseFirestore.getInstance()
        // Las tres consultas salen a la vez (antes eran una tras otra): el panel carga en un tercio del tiempo.
        val (allowed, users, reqs) = coroutineScope {
            val a = async { db.collection("allowed").get(Source.SERVER).wait() }
            val u = async { db.collection("users").get(Source.SERVER).wait() }
            val r = async { runCatching { db.collection("requests").get(Source.SERVER).wait() }.getOrNull() }
            Triple(a.await(), u.await(), r.await())
        }
        val byEmail = HashMap<String, Client>()
        users.documents.forEach { u ->
            val e = u.getString("email")?.trim()?.lowercase().orEmpty()
            if (e.isNotBlank()) byEmail[e] = Client(e, u.getString("name").orEmpty(), u.getString("phone").orEmpty(), "", false, 0.0)
        }
        val rejectedSet = HashSet<String>()
        reqs?.documents?.forEach { r ->
            val e = r.id.lowercase()
            if (r.getString("status") == "rejected") rejectedSet += e
            if (e !in byEmail) byEmail[e] = Client(e, r.getString("name").orEmpty(), r.getString("phone").orEmpty(), "", false, 0.0)
        }
        allowed.documents.forEach { a ->
            val e = a.id.lowercase()
            val base = byEmail[e] ?: Client(e, "", a.getString("phone").orEmpty(), "", false, 0.0)
            // Los documentos de la antigua prueba gratis (trial = true) cuentan como NO activados.
            val active = a.getBoolean("trial") != true
            byEmail[e] = base.copy(plan = a.getString("plan").orEmpty(), hasDoc = active, paidTotal = a.getDouble("paidTotal") ?: 0.0)
        }
        val me = AuthRepo.user?.email?.trim()?.lowercase()
        byEmail.values.filter { it.email != me }.map { it.copy(rejected = !it.hasDoc && it.email in rejectedSet) }
    }

    /** TIEMPO REAL: solicitudes pendientes (más antiguas primero). Devuelve la función para dejar de escuchar. */
    fun watchPending(onChange: (List<PendingRequest>) -> Unit): () -> Unit {
        val reg = FirebaseFirestore.getInstance().collection("requests").whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                onChange(snap.documents.map { d ->
                    PendingRequest(d.id, d.getString("name").orEmpty(), d.getString("phone").orEmpty(), d.getTimestamp("createdAt")?.toDate()?.time ?: 0L)
                }.sortedBy { it.at })
            }
        return { reg.remove() }
    }

    /** Rechaza una solicitud: el cliente verá que fue rechazada. Se puede activar más tarde desde la lista de clientes. */
    suspend fun reject(email: String): Result<Unit> = runCatching {
        val e = email.trim().lowercase()
        FirebaseFirestore.getInstance().collection("requests").document(e)
            .set(mapOf("email" to e, "status" to "rejected", "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).wait()
        Unit
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
        val e = email.trim().lowercase()
        runCatching { // la solicitud deja de estar pendiente (y desaparece la notificación del administrador)
            FirebaseFirestore.getInstance().collection("requests").document(e)
                .set(mapOf("email" to e, "status" to "approved", "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).wait()
        }
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

    /** Crea una encuesta Sí/No para todos los clientes (título + descripción). */
    suspend fun createPoll(title: String, description: String): Result<Unit> = runCatching {
        FirebaseFirestore.getInstance().collection("polls").add(
            mapOf(
                "title" to title.trim(), "description" to description.trim(), "closed" to false,
                "createdAt" to FieldValue.serverTimestamp(), "by" to AuthRepo.user?.email.orEmpty()
            )
        ).wait()
        Unit
    }

    /** Historial: las últimas encuestas con todos sus votos (de la más nueva a la más vieja). */
    suspend fun loadPolls(): Result<List<PollRow>> = runCatching {
        val docs = FirebaseFirestore.getInstance().collection("polls").orderBy("createdAt", Query.Direction.DESCENDING).limit(15)
            .get(Source.SERVER).wait().documents
        coroutineScope { // los votos de todas las encuestas se piden a la vez
            docs.map { d ->
                async {
                    val votes = d.reference.collection("votes").get(Source.SERVER).wait().documents.map { v ->
                        Voter(v.getString("email").orEmpty(), v.getString("name").orEmpty(), v.getString("vote") == "yes", v.getTimestamp("votedAt")?.toDate()?.time ?: 0L)
                    }.sortedBy { it.at }
                    PollRow(d.id, d.getString("title").orEmpty(), d.getString("description").orEmpty(), d.getTimestamp("createdAt")?.toDate()?.time ?: 0L, d.getBoolean("closed") == true, votes)
                }
            }.map { it.await() }
        }
    }

    /** Cierra una encuesta (ya nadie puede votar) o la vuelve a abrir. */
    suspend fun setPollClosed(id: String, closed: Boolean): Result<Unit> = runCatching {
        FirebaseFirestore.getInstance().collection("polls").document(id).update("closed", closed).wait(); Unit
    }

    /** Borra la encuesta y todos sus votos. */
    suspend fun deletePoll(id: String): Result<Unit> = runCatching {
        val db = FirebaseFirestore.getInstance()
        val ref = db.collection("polls").document(id)
        val votes = ref.collection("votes").get(Source.SERVER).wait().documents
        votes.chunked(400).forEach { chunk -> db.batch().also { b -> chunk.forEach { b.delete(it.reference) } }.commit().wait() }
        ref.delete().wait(); Unit
    }
}
