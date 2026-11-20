package com.drix.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.drix.app.R
import com.drix.app.data.DrixPrefs
import com.drix.app.ui.MainActivity
import com.google.firebase.auth.FirebaseAuth
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Recordatorios temporales cada hora («no dejes que otros repartidores agarren los mejores pedidos…»).
 * Solo se envían si: hay sesión con acceso vigente, el cliente no los desactivó en Perfil, Drix está PAUSADO
 * y la hora actual cae dentro de su horario de trabajo. Cada aviso desaparece solo a los 20 minutos y trae un
 * botón «Activar ahora». Android decide la hora exacta (puede variar unos minutos con el ahorro de batería).
 */
object Nudges {
    private const val CHANNEL = "drix_recordatorios"
    private const val NOTIF_ID = 7101
    const val ACTION_ACTIVATE = "com.drix.app.NUDGE_ACTIVATE"

    private val messages = listOf(
        "No dejes que otros repartidores agarren los mejores pedidos" to "Conéctate ahora y deja que Drix acepte por ti.",
        "Los mejores pedidos duran segundos" to "Activa Drix y no te quedes mirando el celular.",
        "¿Ya estás conectado?" to "Enciende Drix y acepta solo los pedidos que te convienen.",
        "Es hora de trabajar" to "Otros conductores ya están conectados. Activa Drix y no te quedes atrás.",
        "Tu próximo viaje te está esperando" to "Toca el botón flotante de Drix para activarlo.",
        "No pierdas más pedidos" to "Drix reacciona más rápido que tu dedo. Actívalo ahora.",
        "Ajusta tus filtros y sal a trabajar" to "Revisa tus zonas y precios, y activa Drix.",
        "Los pedidos buenos no esperan" to "Conéctate ahora: Drix acepta por ti lo que cumple tus reglas."
    )

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<NudgeWorker>(1, TimeUnit.HOURS).setInitialDelay(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(ctx.applicationContext).enqueueUniquePeriodicWork("drix_nudges", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun maybeNotify(ctx: Context) {
        val app = ctx.applicationContext
        DrixPrefs.init(app)
        if (FirebaseAuth.getInstance().currentUser == null || !DrixPrefs.accessValid() || !DrixPrefs.nudgesOn.value) return
        val cfg = DrixPrefs.cfg.value
        if (cfg.enabled) return // ya está trabajando: no molestar
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (h < cfg.startHour || h >= cfg.endHour) return // fuera de su horario de trabajo
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val sp = app.getSharedPreferences("drix_nudge", Context.MODE_PRIVATE)
        val i = sp.getInt("i", 0) % messages.size
        sp.edit().putInt("i", i + 1).apply()
        val (title, text) = messages[i]

        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Recordatorios de Drix", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            app, 2, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val activate = PendingIntent.getBroadcast(
            app, 3, Intent(app, NudgeReceiver::class.java).setAction(ACTION_ACTIVATE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true).setTimeoutAfter(20 * 60 * 1000L) // temporal: se quita sola a los 20 min
            .setContentIntent(open)
            .addAction(0, "Activar ahora", activate)
            .build()
        nm.notify(NOTIF_ID, n)
    }
}

class NudgeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result { runCatching { Nudges.maybeNotify(applicationContext) }; return Result.success() }
}

/** Botón «Activar ahora» del recordatorio. */
class NudgeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action != Nudges.ACTION_ACTIVATE) return
        DrixPrefs.init(ctx)
        ctx.getSystemService(NotificationManager::class.java).cancel(7101)
        if (!DrixPrefs.accessValid()) { Toast.makeText(ctx, "Abre Drix para iniciar sesión", Toast.LENGTH_LONG).show(); return }
        DrixPrefs.update { it.copy(enabled = true) }
        Toast.makeText(ctx, "Drix activado", Toast.LENGTH_SHORT).show()
    }
}
