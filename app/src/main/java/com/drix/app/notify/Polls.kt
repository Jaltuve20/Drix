package com.drix.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.drix.app.R
import com.drix.app.data.DrixPrefs
import com.drix.app.ui.MainActivity
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Una encuesta del administrador (polls/{id}): título + descripción; el cliente vota Sí o No. */
data class Poll(val id: String, val title: String, val description: String)

/**
 * Encuestas Sí/No. El administrador crea la encuesta en el panel y cada cliente:
 *  - la ve como ventana al abrir Drix (mientras no haya votado) y recibe una notificación si la app está cerrada;
 *  - vota UNA sola vez (polls/{id}/votes/{uid}); el voto no se puede cambiar.
 * Solo se muestra la encuesta más reciente y mientras esté abierta.
 */
object Polls {
    private const val CHANNEL = "drix_encuestas"

    /** Encuesta pendiente de responder (la ventana de Drix la muestra mientras no sea null). */
    val pending = MutableStateFlow<Poll?>(null)
    private var skipped: String? = null // «Ahora no»: se vuelve a preguntar la próxima vez que abra la app

    fun skipForNow() { skipped = pending.value?.id; pending.value = null }

    private suspend fun <T> Task<T>.wait(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
    }

    /** Escucha en tiempo real. [notify] = true también muestra notificación (para el servicio, con la app cerrada). */
    fun listen(ctx: Context, notify: Boolean): () -> Unit {
        val app = ctx.applicationContext
        DrixPrefs.init(app)
        val reg = FirebaseFirestore.getInstance().collection("polls")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(1)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !DrixPrefs.accessValid()) return@addSnapshotListener
                val d = snap.documents.firstOrNull()
                if (d == null || d.getBoolean("closed") == true) { pending.value = null; return@addSnapshotListener }
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@addSnapshotListener
                d.reference.collection("votes").document(uid).get().addOnSuccessListener { v ->
                    if (v.exists()) { if (pending.value?.id == d.id) pending.value = null; return@addOnSuccessListener }
                    val poll = Poll(d.id, d.getString("title").orEmpty(), d.getString("description").orEmpty())
                    if (poll.id != skipped) pending.value = poll
                    if (notify) notifyOnce(app, poll)
                }
            }
        return { reg.remove() }
    }

    /** Revisión única y BLOQUEANTE (hilo de fondo): avisa de una encuesta nueva sin responder. */
    fun checkNow(ctx: Context) {
        val app = ctx.applicationContext
        DrixPrefs.init(app)
        val u = FirebaseAuth.getInstance().currentUser ?: return
        if (!DrixPrefs.accessValid()) return
        val d = Tasks.await(
            FirebaseFirestore.getInstance().collection("polls").orderBy("createdAt", Query.Direction.DESCENDING).limit(1).get(),
            20, TimeUnit.SECONDS
        ).documents.firstOrNull() ?: return
        if (d.getBoolean("closed") == true) return
        val voted = Tasks.await(d.reference.collection("votes").document(u.uid).get(), 20, TimeUnit.SECONDS).exists()
        if (!voted) notifyOnce(app, Poll(d.id, d.getString("title").orEmpty(), d.getString("description").orEmpty()))
    }

    /** Registra el voto del cliente actual. Las reglas de Firestore solo permiten UN voto por cuenta y con la encuesta abierta. */
    suspend fun vote(poll: Poll, yes: Boolean): Result<Unit> = runCatching {
        val u = FirebaseAuth.getInstance().currentUser ?: error("Inicia sesión para votar")
        FirebaseFirestore.getInstance().collection("polls").document(poll.id).collection("votes").document(u.uid).set(
            mapOf(
                "vote" to if (yes) "yes" else "no", "email" to u.email.orEmpty().trim().lowercase(),
                "name" to DrixPrefs.name().take(80), "votedAt" to FieldValue.serverTimestamp()
            )
        ).wait()
        pending.value = null
        Unit
    }

    private fun notifyOnce(app: Context, poll: Poll) {
        synchronized(this) {
            val sp = app.getSharedPreferences("drix_poll", Context.MODE_PRIVATE)
            if (sp.getString("notified", null) == poll.id) return
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            sp.edit().putString("notified", poll.id).apply()
            val nm = app.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Encuestas de Drix", NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(
                app, 4, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val text = poll.title.ifBlank { "Tu opinión cuenta" } + " · Toca para votar Sí o No"
            nm.notify(7102, NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Nueva encuesta de Drix")
                .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(open).build())
        }
    }
}
