package com.drix.app.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.drix.app.R
import com.drix.app.data.DrixPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Botón flotante con el logo de Drix para activar/pausar la automatización sin abrir la app.
 *  · Se toca el logo → salen ▶ (activar) y ✕ (detener). Se cierra solo a los pocos segundos.
 *  · Se arrastra para moverlo; al soltar se pega al borde más cercano y recuerda su posición.
 *  · El anillo del logo indica el estado: verde = activo, rojo = pausado.
 * Vive en el servicio de accesibilidad (ventana TYPE_ACCESSIBILITY_OVERLAY: no necesita el permiso de superposición).
 */
class FloatingButton(private val ctx: Context, scope: CoroutineScope) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = ctx.getSharedPreferences("drix_bubble", Context.MODE_PRIVATE)
    private val ui = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val green = 0xFF22C55E.toInt()
    private val red = 0xFFEF4444.toInt()

    private var root: LinearLayout? = null
    private var bubbleView: View? = null
    private var panel: LinearLayout? = null
    private var ring: GradientDrawable? = null
    private var play: Glyph? = null
    private var cancel: Glyph? = null
    private var right = prefs.getBoolean("right", true)
    private var yPos = prefs.getInt("y", -1)
    private var expanded = false

    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }
    private val collapse = Runnable { setExpanded(false) }

    private fun dm() = ctx.resources.displayMetrics
    private fun dp(v: Int) = (v * dm().density).toInt()

    init {
        // El anillo y los botones siguen el estado real (también si se cambia desde la app)
        scope.launch { DrixPrefs.cfg.map { it.enabled }.distinctUntilChanged().collect { refresh(it) } }
    }

    fun show() {
        if (root != null) return
        DrixPrefs.init(ctx)
        val r = build()
        root = r
        if (yPos < 0) yPos = dm().heightPixels / 3
        place()
        if (runCatching { wm.addView(r, lp) }.isFailure) { root = null; return }
        refresh(DrixPrefs.cfg.value.enabled)
    }

    fun hide() {
        ui.removeCallbacks(collapse)
        root?.let { runCatching { wm.removeView(it) } }
        root = null; expanded = false
    }

    private fun refresh(on: Boolean) {
        ring?.setColor(if (on) green else red)
        play?.current = on
        cancel?.current = !on
    }

    private fun act(on: Boolean) {
        if (on && !DrixPrefs.accessValid()) {
            Toast.makeText(ctx, "Tu acceso no está vigente. Renueva tu cuenta por WhatsApp.", Toast.LENGTH_LONG).show()
            return
        }
        DrixPrefs.update { it.copy(enabled = on) }
        setExpanded(false)
        Toast.makeText(ctx, if (on) "Drix activado" else "Drix pausado", Toast.LENGTH_SHORT).show()
    }

    private fun build(): LinearLayout {
        val ringBg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(red) }
        ring = ringBg
        val logo = ImageView(ctx).apply {
            setImageResource(R.drawable.drix_logo); scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) }
            }
            clipToOutline = true
        }
        val bubble = FrameLayout(ctx).apply {
            background = ringBg; alpha = 0.95f
            setPadding(dp(3), dp(3), dp(3), dp(3))
            contentDescription = "Drix: abrir controles"
            addView(logo, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
            setOnTouchListener(touch)
        }
        bubbleView = bubble

        val pl = Glyph(ctx, 0, green).apply { contentDescription = "Activar Drix"; setOnClickListener { act(true) } }
        val ca = Glyph(ctx, 1, red).apply { contentDescription = "Detener Drix"; setOnClickListener { act(false) } }
        play = pl; cancel = ca
        val pn = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE
            background = GradientDrawable().apply { cornerRadius = dp(30).toFloat(); setColor(0xEE191D21.toInt()) }
            setPadding(dp(6), dp(5), dp(6), dp(5))
            addView(pl, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(6) })
            addView(ca, LinearLayout.LayoutParams(dp(46), dp(46)))
        }
        panel = pn

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            if (right) { addView(pn); addView(bubble) } else { addView(bubble); addView(pn) }
        }
    }

    /** Toque = abrir/cerrar los botones · arrastre = mover. */
    private val touch = object : View.OnTouchListener {
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        var dragging = false; var wasExpanded = false
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    ui.removeCallbacks(collapse)
                    wasExpanded = expanded
                    if (expanded) setExpanded(false)   // vuelve al borde antes de arrastrar
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) { lp.x = (startX + dx).toInt(); lp.y = (startY + dy).toInt(); update() }
                }
                MotionEvent.ACTION_UP -> { if (dragging) snap() else if (!wasExpanded) setExpanded(true) }
                MotionEvent.ACTION_CANCEL -> if (dragging) snap()
            }
            return true
        }
    }

    private fun setExpanded(on: Boolean) {
        expanded = on
        panel?.visibility = if (on) View.VISIBLE else View.GONE
        place()
        ui.removeCallbacks(collapse)
        if (on) ui.postDelayed(collapse, 4500)
    }

    private fun measuredWidth(): Int {
        val r = root ?: return dp(54)
        r.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        return r.measuredWidth
    }

    /** Coloca la ventana pegada al borde (izquierdo o derecho) a la altura guardada. */
    private fun place() {
        val w = measuredWidth()
        lp.x = if (right) dm().widthPixels - w else 0
        lp.y = yPos
        update()
    }

    private fun update() { root?.let { runCatching { wm.updateViewLayout(it, lp) } } }

    /** Al soltar tras arrastrar: se pega al borde más cercano y guarda la posición. */
    private fun snap() {
        val w = measuredWidth()
        val newRight = lp.x + w / 2 > dm().widthPixels / 2
        yPos = lp.y.coerceIn(dp(30), dm().heightPixels - dp(110))
        if (newRight != right) {
            right = newRight
            val r = root; val b = bubbleView; val p = panel
            if (r != null && b != null && p != null) { r.removeAllViews(); if (right) { r.addView(p); r.addView(b) } else { r.addView(b); r.addView(p) } }
        }
        prefs.edit().putBoolean("right", right).putInt("y", yPos).apply()
        lp.y = yPos
        val target = if (right) dm().widthPixels - w else 0
        ValueAnimator.ofInt(lp.x, target).apply {
            duration = 160
            addUpdateListener { lp.x = it.animatedValue as Int; update() }
        }.start()
    }
}

/** Botón redondo del panel: ▶ (kind 0) o ✕ (kind 1). `current` = el estado en que está Drix ahora (aro blanco). */
private class Glyph(ctx: Context, private val kind: Int, private val fill: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var current = false
        set(v) { field = v; invalidate() }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val r = width / 2f
        p.style = Paint.Style.FILL; p.color = fill
        c.drawCircle(cx, cy, r - 2f, p)
        if (current) {
            p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.14f; p.color = Color.WHITE
            c.drawCircle(cx, cy, r - p.strokeWidth / 2f - 2f, p)
        }
        p.color = Color.WHITE
        if (kind == 0) {
            p.style = Paint.Style.FILL
            val tri = Path().apply {
                moveTo(cx - r * 0.18f, cy - r * 0.38f); lineTo(cx - r * 0.18f, cy + r * 0.38f); lineTo(cx + r * 0.40f, cy); close()
            }
            c.drawPath(tri, p)
        } else {
            p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.20f; p.strokeCap = Paint.Cap.ROUND
            c.drawLine(cx - r * 0.28f, cy - r * 0.28f, cx + r * 0.28f, cy + r * 0.28f, p)
            c.drawLine(cx + r * 0.28f, cy - r * 0.28f, cx - r * 0.28f, cy + r * 0.28f, p)
        }
    }
}
