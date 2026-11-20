package com.drix.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.drix.app.BuildConfig
import com.drix.app.R
import com.drix.app.ui.MainActivity
import com.drix.app.update.UpdateInfo
import com.drix.app.update.Updater
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Notificación «Una nueva actualización disponible» con las novedades (el texto de RELEASE_NOTES.txt que va en update.json).
 * Se avisa UNA sola vez por versión. Al tocarla se abre Drix y allí aparece el diálogo para descargar e instalar.
 */
object UpdateNotifier {
    private const val CHANNEL = "drix_updates"
    private const val NOTIF_ID = 7001

    /** Revisión única y BLOQUEANTE (hilo de fondo). */
    fun checkNow(ctx: Context) {
        val app = ctx.applicationContext
        if (BuildConfig.UPDATE_URL.isBlank()) return
        val info = Updater.fetch()
        val sp = app.getSharedPreferences("drix_upd", Context.MODE_PRIVATE)
        val nm = app.getSystemService(NotificationManager::class.java)
        if (info.versionCode <= Updater.installedCode(app)) { nm.cancel(NOTIF_ID); return } // ya está al día
        if (sp.getLong("notified", 0L) >= info.versionCode) return                             // ya se avisó de esta versión
        if (show(app, info)) sp.edit().putLong("notified", info.versionCode).apply()
    }

    private fun show(ctx: Context, info: UpdateInfo): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Actualizaciones de Drix", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notes = info.notes.trim().ifBlank { "Mejoras y correcciones." }.take(900)
        val firstLine = notes.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        val ver = info.versionName.ifBlank { "nueva" }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Una nueva actualización disponible")
            .setContentText("Versión $ver · $firstLine")
            .setStyle(NotificationCompat.BigTextStyle().setSummaryText("Drix $ver").bigText("Qué hay de nuevo:\n$notes"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.notify(NOTIF_ID, n)
        return true
    }
}

/** Trabajo periódico: con la app cerrada revisa avisos del administrador y versiones nuevas. */
class BackgroundWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching { Announcements.checkNow(applicationContext) }
        runCatching { UpdateNotifier.checkNow(applicationContext) }
        Result.success()
    }
}

object BackgroundChecks {
    /** Programa la revisión cada 15 min (el mínimo de Android) cuando hay internet. Si ya existe, no la duplica. */
    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<BackgroundWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx.applicationContext)
            .enqueueUniquePeriodicWork("drix_background", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
