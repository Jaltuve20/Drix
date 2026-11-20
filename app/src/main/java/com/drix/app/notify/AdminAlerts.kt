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
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.TimeUnit

/**
 * Avisos PARA EL ADMINISTRADOR: cuando alguien se registra, llega «Tienes un correo pendiente por activar».
 * La notificación trae los botones «Activar» y «Rechazar»: abren el panel de administrador directamente en esa solicitud.
 *  - EN VIVO con la app abierta o el servicio de accesibilidad activo ([listen]).
 *  - EN SEGUNDO PLANO con la app cerrada: [BackgroundWorker] llama a [checkNow] cada ~15 min.
 * Cada solicitud se avisa UNA sola vez; si ya fue activada o rechazada, su notificación desaparece sola.
 */
object AdminAlerts {
    private const val CHANNEL = "drix_solicitudes"
    const val EXTRA_ACTION = "drix_admin_action" // "open" | "activate" | "reject"
    const val EXTRA_EMAIL = "drix_admin_email"

    /** Acción pedida desde una notificación; el panel de administrador la consume y la deja en null. */
    data class Action(val kind: String, val email: String)
    val action = MutableStateFlow<Action?>(null)

    fun handleIntent(i: Intent?) {
        if (i == null) return
        val kind = i.getStringExtra(EXTRA_ACTION) ?: return
        action.value = Action(kind, i.getStringExtra(EXTRA_EMAIL).orEmpty())
        i.removeExtra(EXTRA_ACTION)
    }

    /** Escucha en tiempo real las solicitudes pendientes. Solo para administradores. */
    fun listen(ctx: Context): () -> Unit {
        val app = ctx.applicationContext
        DrixPrefs.init(app)
        if (!DrixPrefs.isAdmin()) return {}
        val reg = FirebaseFirestore.getInstance().collection("requests").whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                if (snap == null || !DrixPrefs.isAdmin()) return@addSnapshotListener
                process(app, snap.documents)
            }
        return { reg.remove() }
    }

    /** Revisión única y BLOQUEANTE (hilo de fondo). */
    fun checkNow(ctx: Context) {
        val app = ctx.applicationContext
        DrixPrefs.init(app)
        if (FirebaseAuth.getInstance().currentUser == null || !DrixPrefs.isAdmin()) return
        val snap = Tasks.await(
            FirebaseFirestore.getInstance().collection("requests").whereEqualTo("status", "pending").get(),
            20, TimeUnit.SECONDS
        )
        process(app, snap.documents)
    }

    private fun process(app: Context, docs: List<DocumentSnapshot>) {
        synchronized(this) {
            val sp = app.getSharedPreferences("drix_admin_alerts", Context.MODE_PRIVATE)
            val notified = sp.getStringSet("n", emptySet()).orEmpty().toSet()
            val pending = docs.map { it.id }.toSet()
            val nm = app.getSystemService(NotificationManager::class.java)
            notified.filter { it !in pending }.forEach { nm.cancel(idFor(it)) } // ya activada / rechazada
            var shown = notified.intersect(pending)
            for (d in docs) if (d.id !in notified && show(app, d.id, d.getString("name").orEmpty())) shown = shown + d.id
            sp.edit().putStringSet("n", shown).apply()
        }
    }

    private fun idFor(email: String) = 8000 + (email.hashCode() and 0xFFFF)

    private fun show(ctx: Context, email: String, name: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Solicitudes de acceso", NotificationManager.IMPORTANCE_HIGH))
        val id = idFor(email)
        fun pi(kind: String, k: Int) = PendingIntent.getActivity(
            ctx, id * 3 + k,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ACTION, kind).putExtra(EXTRA_EMAIL, email),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val who = if (name.isBlank()) email else "$name · $email"
        nm.notify(id, NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Tienes un correo pendiente por activar")
            .setContentText(who).setStyle(NotificationCompat.BigTextStyle().bigText("$who\nToca Activar o Rechazar."))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
            .setContentIntent(pi("open", 0))
            .addAction(0, "Activar", pi("activate", 1))
            .addAction(0, "Rechazar", pi("reject", 2))
            .build())
        return true
    }
}
