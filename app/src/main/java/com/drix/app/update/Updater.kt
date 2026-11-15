package com.drix.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.drix.app.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Datos de la última versión publicada (archivo update.json). */
data class UpdateInfo(
    val versionCode: Long, val versionName: String, val apkUrl: String,
    val sha256: String, val notes: String, val mandatory: Boolean
)

/**
 * Actualización dentro de la app: lee update.json (BuildConfig.UPDATE_URL), descarga el APK,
 * verifica su huella SHA-256 y abre el instalador de Android. Android solo instala el APK
 * si está firmado con la MISMA llave que la app instalada.
 */
object Updater {
    sealed interface State {
        data object Idle : State
        data object UpToDate : State
        data class Available(val info: UpdateInfo) : State
        data class Downloading(val info: UpdateInfo, val progress: Float) : State
        data class NeedPermission(val file: File) : State
        data class Ready(val file: File) : State
        data class Error(val msg: String, val info: UpdateInfo?) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun installedCode(ctx: Context): Long =
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))

    fun installedName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""

    fun dismiss() { _state.value = State.Idle }

    /** Busca una versión nueva. [manual] = el usuario lo pidió (se le avisa también si no hay nada o si falla). */
    suspend fun check(ctx: Context, manual: Boolean) {
        val s = _state.value
        if (s is State.Available || s is State.Downloading || s is State.Ready || s is State.NeedPermission) return
        if (BuildConfig.UPDATE_URL.isBlank()) {
            if (manual) _state.value = State.Error("Las actualizaciones no están configuradas en esta versión.", null)
            return
        }
        val result = try {
            Result.success(withContext(Dispatchers.IO) { fetch() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        val info = result.getOrNull()
        _state.value = when {
            result.isFailure -> if (manual) State.Error("No se pudo buscar actualizaciones. Revisa tu internet.", null) else State.Idle
            info != null && info.versionCode > installedCode(ctx) -> State.Available(info)
            manual -> State.UpToDate
            else -> State.Idle
        }
    }

    private fun fetch(): UpdateInfo {
        val c = URL(BuildConfig.UPDATE_URL).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Drix-Updater")
        c.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            return UpdateInfo(
                versionCode = j.getLong("versionCode"), versionName = j.optString("versionName"),
                apkUrl = j.getString("apkUrl"), sha256 = j.optString("sha256"),
                notes = j.optString("notes"), mandatory = j.optBoolean("mandatory", false)
            )
        } finally {
            c.disconnect()
        }
    }

    /** Descarga el APK mostrando el progreso y, al terminar, abre el instalador. */
    suspend fun download(ctx: Context, info: UpdateInfo) {
        _state.value = State.Downloading(info, 0f)
        try {
            val file = withContext(Dispatchers.IO) {
                val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "Drix-update.apk")
                val c = URL(info.apkUrl).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 20000
                c.setRequestProperty("User-Agent", "Drix-Updater")
                try {
                    if (c.responseCode != 200) error("HTTP ${c.responseCode}")
                    val total = c.contentLengthLong
                    val md = MessageDigest.getInstance("SHA-256")
                    var done = 0L
                    var last = 0f
                    c.inputStream.use { input ->
                        out.outputStream().use { os ->
                            val buf = ByteArray(32 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                os.write(buf, 0, n)
                                md.update(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val p = done.toFloat() / total
                                    if (p - last >= 0.01f) { last = p; _state.value = State.Downloading(info, p) }
                                }
                            }
                        }
                    }
                    if (info.sha256.isNotBlank()) {
                        val got = md.digest().joinToString("") { "%02x".format(it) }
                        if (!got.equals(info.sha256.trim(), ignoreCase = true)) {
                            out.delete()
                            error("El archivo descargado está dañado. Intenta de nuevo.")
                        }
                    }
                } finally {
                    c.disconnect()
                }
                out
            }
            val pi = ctx.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            if (pi == null || pi.packageName != ctx.packageName) {
                file.delete()
                error("El archivo descargado no es una versión válida de Drix.")
            }
            install(ctx, file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = State.Error(e.message ?: "No se pudo descargar la actualización.", info)
        }
    }

    /** Abre el instalador de Android. Si falta el permiso «instalar apps desconocidas», lo pide primero. */
    fun install(ctx: Context, file: File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) { _state.value = State.NeedPermission(file); return }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        _state.value = State.Ready(file)
    }

    fun openPermissionSettings(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
