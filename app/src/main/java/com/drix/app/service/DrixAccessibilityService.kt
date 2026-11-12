package com.drix.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.drix.app.core.*
import com.drix.app.data.Config
import com.drix.app.data.DrixPrefs
import kotlin.random.Random

class DrixAccessibilityService : AccessibilityService() {
    private lateinit var overlay: OverlayWidget
    private val handler = Handler(Looper.getMainLooper())
    private var lastRun = 0L
    private var lastClickSig = ""
    private var pendingSig = ""
    private var awaitConfirmUntil = 0L

    override fun onServiceConnected() {
        DrixPrefs.init(this)
        overlay = OverlayWidget(this)
        overlay.show("Drix listo")
    }

    /** Hace clic tras un retraso aleatorio (configurable). Devuelve el retraso usado. */
    private fun clickLater(cfg: Config, find: () -> AccessibilityNodeInfo?, done: (Boolean) -> Unit = {}): Long {
        val wait = Random.nextLong(cfg.minDelayMs.toLong(), cfg.maxDelayMs.toLong() + 1)
        handler.postDelayed({ done(find()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) }, wait)
        return wait
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        val cfg = DrixPrefs.cfg.value
        if (e == null || !cfg.enabled || e.packageName?.toString() != cfg.targetPkg) return
        val now = System.currentTimeMillis()
        if (now - lastRun < 300) return // antirrebote
        lastRun = now
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return

        // Paso 2: tras tocar una tarjeta, algunas apps abren un detalle con botón "Aceptar"
        if (now < awaitConfirmUntil && OrderParser.findAccept(root) != null) {
            awaitConfirmUntil = 0
            val w = clickLater(cfg, { rootInActiveWindow?.let { OrderParser.findAccept(it) } })
            overlay.show("✅ Confirmando en $w ms", true); return
        }

        val cards = OrderParser.findCards(root)
        if (cards.isEmpty()) {
            // Diagnóstico: 0 textos = la app no expone su contenido a accesibilidad
            overlay.show("👀 Sin pedidos legibles · ${OrderParser.collectTexts(root).size} textos")
            return
        }
        val zones = DrixPrefs.zones.value
        var why = ""
        for (card in cards) {
            val o = card.order
            when (val d = OrderEvaluator.evaluate(o, cfg, zones)) {
                is Decision.Accept -> {
                    if (o.sig == lastClickSig || o.sig == pendingSig) return
                    pendingSig = o.sig
                    val target = OrderParser.clickTarget(card.node)
                    val w = clickLater(cfg, {
                        // Re-verifica que la tarjeta siga mostrando el mismo pedido antes de pulsar
                        val same = card.node.refresh() && OrderParser.parse(OrderParser.collectTexts(card.node))?.sig == o.sig
                        if (same) target?.takeIf { it.refresh() } else null
                    }) { ok ->
                        pendingSig = ""
                        if (ok) { lastClickSig = o.sig; awaitConfirmUntil = System.currentTimeMillis() + 4000 }
                    }
                    overlay.show("✅ S/ %.1f · %.2f/km · %s · %d ms".format(o.fare, o.perKm, o.district?.name ?: "?", w), true)
                    return
                }
                is Decision.Reject -> if (why.isEmpty()) why = d.why
            }
        }
        overlay.show("❌ ${cards.size} pedidos · $why", false)
    }

    override fun onInterrupt() {}
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); if (::overlay.isInitialized) overlay.hide(); super.onDestroy() }
}
