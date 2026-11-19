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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/**
 * Avisos del administrador. El admin escribe un aviso (colección «announcements» de Firestore) y cada celular
 * con la app lo recibe como notificación. Escucha en tiempo real mientras la app está abierta o el servicio de
 * accesibilidad está activo; si el celular estaba apagado o sin internet, se entrega al abrir la app.
 */
object Announcements {
    private const val CHANNEL = "drix_avisos"

    /** Empieza a escuchar. Devuelve la función para detenerse. */
    fun listen(ctx: Context): () -> Unit {
        val app = ctx.applicationContext
        DrixPrefs.init(app)
        val sp = app.getSharedPreferences("drix_ann", Context.MODE_PRIVATE)
        val reg = FirebaseFirestore.getInstance().collection("announcements")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(1)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !DrixPrefs.accessValid()) return@addSnapshotListener
                val d = snap.documents.firstOrNull()
                synchronized(this) {
                    val seen = sp.getLong("seen", -1L)
                    if (d == null) { // todavía no hay avisos: lo que se publique desde ahora sí se entrega
                        if (seen == -1L) sp.edit().putLong("seen", System.currentTimeMillis()).apply()
                        return@addSnapshotListener
                    }
                    val ts = d.getTimestamp("createdAt")?.toDate()?.time ?: return@addSnapshotListener
                    if (seen == -1L) { sp.edit().putLong("seen", ts).apply(); return@addSnapshotListener } // instalación nueva: no se muestran avisos viejos
                    if (ts <= seen) return@addSnapshotListener
                    sp.edit().putLong("seen", ts).apply()
                    show(app, (ts % Int.MAX_VALUE).toInt(), d.getString("title").orEmpty(), d.getString("message").orEmpty())
                }
            }
        return { reg.remove() }
    }

    private fun show(ctx: Context, id: Int, title: String, message: String) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Avisos de Drix", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title.ifBlank { "Drix" })
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.notify(id, n)
    }
}
