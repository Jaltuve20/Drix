package com.drix.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/** Widget flotante (SYSTEM_ALERT_WINDOW) con el último resultado. Corre en el hilo principal. */
class OverlayWidget(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: TextView? = null

    fun show(msg: String, ok: Boolean? = null) {
        if (!Settings.canDrawOverlays(ctx)) return
        val v = view ?: TextView(ctx).apply {
            setPadding(32, 16, 32, 16); textSize = 13f; setTextColor(Color.BLACK)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // NOT_TOUCHABLE: el widget no intercepta toques sobre inDrive
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 120 }
            wm.addView(this, lp); view = this
        }
        v.text = "Drix · $msg"
        v.background = GradientDrawable().apply {
            cornerRadius = 48f
            setColor(when (ok) { true -> 0xFFBBF7D0.toInt(); false -> 0xFFFECACA.toInt(); else -> 0xFFFFC299.toInt() })
        }
    }

    fun hide() { view?.let { runCatching { wm.removeView(it) } }; view = null }
}
