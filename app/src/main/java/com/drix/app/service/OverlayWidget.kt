package com.drix.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.drix.app.R
import com.drix.app.data.DrixPrefs

/**
 * Aviso flotante de estado (SYSTEM_ALERT_WINDOW): tarjeta oscura con logo, estado y mensaje.
 * Solo se muestra con la automatización ACTIVA; al pausarla desaparece (ver el servicio).
 */
class OverlayWidget(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var card: GradientDrawable? = null
    private var dot: GradientDrawable? = null
    private var title: TextView? = null
    private var msg: TextView? = null

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun build(): LinearLayout {
        val bg = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(0xF2191D21.toInt()) }
        val dd = GradientDrawable().apply { setShape(GradientDrawable.OVAL); setColor(0xFFFFC800.toInt()) }
        val logo = ImageView(ctx).apply {
            setImageResource(R.drawable.drix_logo)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(11) }
        }
        val dotView = View(ctx).apply {
            background = dd
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(6) }
        }
        val t = TextView(ctx).apply {
            textSize = 10f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.14f; setTextColor(0xFFFFC800.toInt())
        }
        val m = TextView(ctx).apply {
            textSize = 13.5f; setTextColor(Color.WHITE); maxLines = 2
            maxWidth = ctx.resources.displayMetrics.widthPixels - dp(120)
        }
        val head = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL; addView(dotView); addView(t) }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; addView(head); addView(m) }
        card = bg; dot = dd; title = t; msg = m
        return LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL; background = bg
            setPadding(dp(12), dp(9), dp(18), dp(10))
            addView(logo); addView(col)
        }
    }

    fun show(raw: String, ok: Boolean? = null) {
        // Pausado o sin permiso → nada en pantalla
        if (!DrixPrefs.cfg.value.enabled || !Settings.canDrawOverlays(ctx)) { hide(); return }
        val r = root ?: build().also { v ->
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // NOT_TOUCHABLE: el aviso no intercepta toques sobre la app de pedidos
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(46) }
            if (runCatching { wm.addView(v, lp) }.isFailure) return
            v.alpha = 0f; v.translationY = -dp(10).toFloat()
            v.animate().alpha(1f).translationY(0f).setDuration(200).start()
            root = v
        }
        val warn = raw.startsWith("⚠")
        val accent = when { warn -> 0xFFF59E0B; ok == true -> 0xFF22C55E; ok == false -> 0xFFEF4444; else -> 0xFFFFC800 }.toInt()
        title?.text = when {
            warn -> "ATENCIÓN"
            ok == true && raw.contains("aceptado", true) -> "ACEPTADO"
            ok == true -> "ACEPTANDO"
            ok == false -> "RECHAZADO"
            else -> "DRIX · ACTIVO"
        }
        title?.setTextColor(accent); dot?.setColor(accent)
        card?.setStroke(dp(2), (accent and 0x00FFFFFF) or 0xAA000000.toInt())
        msg?.text = raw.replace(Regex("^[^\\p{L}\\p{N}«¡¿]+"), "")   // quita el emoji inicial: el color ya indica el estado
        r.requestLayout()
    }

    fun hide() { root?.let { runCatching { wm.removeView(it) } }; root = null }
}
