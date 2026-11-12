package com.drix.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.drix.app.core.*
import com.drix.app.data.Config
import com.drix.app.data.DrixPrefs
import kotlin.random.Random

class DrixAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance: DrixAccessibilityService? = null }

    private lateinit var overlay: OverlayWidget
    private val handler = Handler(Looper.getMainLooper())
    private var lastRun = 0L
    private var pendingSig = ""
    private var doneSig = ""
    private var doneAt = 0L
    private var lastBannerClick = 0L

    override fun onServiceConnected() {
        instance = this
        DrixPrefs.init(this)
        overlay = OverlayWidget(this)
        overlay.show("Drix listo")
    }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }

    private fun handled(sig: String) = sig == pendingSig || (sig == doneSig && System.currentTimeMillis() - doneAt < 15000)
    private fun markDone(sig: String) { doneSig = sig; doneAt = System.currentTimeMillis() }
    private fun click(n: AccessibilityNodeInfo?) = n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true

    /** Clic tras un retraso aleatorio (configurable). Devuelve el retraso usado. */
    private fun clickLater(cfg: Config, find: () -> AccessibilityNodeInfo?, done: (Boolean) -> Unit = {}): Long {
        val wait = Random.nextLong(cfg.minDelayMs.toLong(), cfg.maxDelayMs.toLong() + 1)
        handler.postDelayed({ done(click(find())) }, wait)
        return wait
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        val cfg = DrixPrefs.cfg.value
        if (e == null || !cfg.enabled || e.packageName?.toString() != cfg.targetPkg) return
        val now = System.currentTimeMillis()
        if (now - lastRun < 500) return // antirrebote: no saturar la app objetivo
        lastRun = now
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return
        val tree = OrderParser.build(root)          // un solo recorrido del árbol
        val texts = tree.texts
        DrixPrefs.debug.value = texts.take(60).joinToString("\n")
        val zones = DrixPrefs.zones.value

        // A) Pantalla de DETALLE ("Aceptar por PEN…")
        if (OrderParser.isDetail(texts)) {
            val o = OrderParser.parseDetail(texts) ?: run { overlay.show("👀 Detalle ilegible"); return }
            when (val d = OrderEvaluator.evaluate(o, cfg, zones)) {
                is Decision.Accept -> {
                    if (handled(o.sig)) return
                    pendingSig = o.sig
                    val w = clickLater(cfg, { rootInActiveWindow?.let { OrderParser.findAccept(it) } }) { ok -> pendingSig = ""; if (ok) markDone(o.sig) }
                    overlay.show("✅ S/ %.1f · %.2f/km · %s · %d ms".format(o.fare, o.perKm, o.district?.name ?: "?", w), true)
                }
                is Decision.Reject -> {
                    overlay.show("❌ ${d.why}", false)
                    if (!handled(o.sig)) { // sale del detalle para que la lista siga cargando
                        pendingSig = o.sig
                        clickLater(cfg, { rootInActiveWindow?.let { OrderParser.findText(it, "Ignorar oferta") } }) { ok ->
                            pendingSig = ""; markDone(o.sig)
                            if (!ok) performGlobalAction(GLOBAL_ACTION_BACK)
                        }
                    }
                }
            }
            return
        }

        // B) LISTA: pulsa "Mostrar solicitudes nuevas" para que siga cargando
        if (now - lastBannerClick > 2500) OrderParser.findText(root, "solicitudes nuevas")?.let { lastBannerClick = now; click(it) }

        val cards = OrderParser.findCards(tree)
        if (cards.isEmpty()) { overlay.show("👀 Sin pedidos legibles · ${texts.size} textos"); return }
        var why = ""
        for (card in cards) {
            val o = card.order
            when (val d = OrderEvaluator.evaluate(o, cfg, zones)) {
                is Decision.Accept -> {
                    if (handled(o.sig)) return
                    pendingSig = o.sig
                    val target = OrderParser.clickTarget(card.node)
                    val w = clickLater(cfg, {
                        // Verifica que la tarjeta siga mostrando el mismo pedido antes de pulsar
                        val same = card.node.refresh() && OrderParser.parse(OrderParser.build(card.node).texts)?.sig == o.sig
                        if (same) target?.takeIf { it.refresh() } else null
                    }) { ok -> pendingSig = ""; if (ok) markDone(o.sig) }
                    overlay.show("✅ S/ %.1f · %.2f/km · %s · %d ms".format(o.fare, o.perKm, o.district?.name ?: "?", w), true)
                    return
                }
                is Decision.Reject -> if (why.isEmpty()) why = d.why
            }
        }
        overlay.show("❌ ${cards.size} pedidos · $why", false)
    }

    /** Botón "Refrescar" de Drix: reinicia el estado y recarga la lista de la app objetivo. */
    fun refresh(delayMs: Long = 0) {
        pendingSig = ""; doneSig = ""; doneAt = 0
        handler.postDelayed({
            val root = rootInActiveWindow
            if (root?.packageName?.toString() != DrixPrefs.cfg.value.targetPkg) { overlay.show("Abre la app objetivo y pulsa Refrescar"); return@postDelayed }
            if (OrderParser.isDetail(OrderParser.build(root).texts)) {
                if (!click(OrderParser.findText(root, "Ignorar oferta"))) performGlobalAction(GLOBAL_ACTION_BACK)
                handler.postDelayed({ swipeDown() }, 900)
            } else if (!click(OrderParser.findText(root, "solicitudes nuevas"))) swipeDown()
            overlay.show("🔄 Refrescando lista…")
        }, delayMs)
    }

    /** Gesto de "tirar hacia abajo" (pull-to-refresh). */
    private fun swipeDown() {
        val dm = resources.displayMetrics
        val path = Path().apply { moveTo(dm.widthPixels / 2f, dm.heightPixels * 0.35f); lineTo(dm.widthPixels / 2f, dm.heightPixels * 0.75f) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 350)).build(), null, null)
    }

    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; handler.removeCallbacksAndMessages(null); if (::overlay.isInitialized) overlay.hide(); super.onDestroy() }
}
