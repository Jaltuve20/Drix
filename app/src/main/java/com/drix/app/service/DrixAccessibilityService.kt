package com.drix.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
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
    private var trailingScheduled = false
    private var lastBannerClick = 0L

    // LISTA: evita volver a tocar la misma tarjeta una y otra vez
    private var listPending = ""
    private var listSig = ""
    private var listAt = 0L

    // DETALLE: estado propio del botón verde «Aceptar por …» (independiente de la lista)
    private var accepting = false
    private var detailSig = ""
    private var detailAt = 0L
    private var ignoring = false

    // Pedido aceptado en la lista: cuando se abra el detalle se pulsa «Aceptar» sin volver a evaluar
    private var armedSig = ""
    private var armedAt = 0L

    override fun onServiceConnected() {
        instance = this
        DrixPrefs.init(this)
        overlay = OverlayWidget(this)
        overlay.show("Drix listo")
    }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }

    private fun listHandled(sig: String) = sig == listPending || (sig == listSig && System.currentTimeMillis() - listAt < 6000)
    private fun isArmed() = armedSig.isNotEmpty() && System.currentTimeMillis() - armedAt < 25000

    /* ───────────── Pulsaciones ───────────── */

    /** ACTION_CLICK sobre el nodo o su primer ancestro clicable. */
    private fun pressNode(raw: AccessibilityNodeInfo?): Boolean {
        var n: AccessibilityNodeInfo? = raw
        var hops = 0
        while (n != null && hops < 8) {
            if (n.isClickable && n.isEnabled && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent; hops++
        }
        return false
    }

    /** Área del botón: el ancestro clicable más cercano (si tiene tamaño de botón) o el propio nodo. */
    private fun buttonBounds(raw: AccessibilityNodeInfo): Rect {
        val dm = resources.displayMetrics
        var n: AccessibilityNodeInfo? = raw
        var hops = 0
        while (n != null && hops < 6) {
            val r = Rect(); n.getBoundsInScreen(r)
            if (n.isClickable && !r.isEmpty && r.width() <= dm.widthPixels && r.height() <= dm.heightPixels * 0.22f) return r
            n = n.parent; hops++
        }
        return Rect().also { raw.getBoundsInScreen(it) }
    }

    /** Toque físico (gesto) en el centro del botón. Es lo que funciona cuando ACTION_CLICK “dice” que sí pero la app lo ignora. */
    private fun tapNode(raw: AccessibilityNodeInfo?): Boolean {
        if (raw == null) return false
        val r = buttonBounds(raw)
        if (r.isEmpty) return false
        val tap = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(tap, 0, 90)).build(), null, null)
    }

    /** Pulsa por ACTION_CLICK; si no hay ancestro clicable, hace el toque con gesto. */
    private fun click(raw: AccessibilityNodeInfo?): Boolean = raw != null && (pressNode(raw) || tapNode(raw))

    private fun randomDelay(cfg: Config) = Random.nextLong(cfg.minDelayMs.toLong(), cfg.maxDelayMs.toLong() + 1)

    /* ───────────── Evento → procesamiento ───────────── */

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        val cfg = DrixPrefs.cfg.value
        if (e == null || !cfg.enabled || e.packageName?.toString() != cfg.targetPkg) return
        val now = System.currentTimeMillis()
        if (now - lastRun < 400) {
            // Antes se DESCARTABA este evento: si era el que abría el detalle, el servicio ya no volvía a mirar y la pantalla se quedaba así.
            // Ahora se programa una lectura final (trailing) para no perder nunca el último estado.
            if (!trailingScheduled) {
                trailingScheduled = true
                handler.postDelayed({ trailingScheduled = false; process() }, 450)
            }
            return
        }
        process()
    }

    private fun process() {
        val cfg = DrixPrefs.cfg.value
        if (!cfg.enabled) return
        lastRun = System.currentTimeMillis()
        val now = lastRun
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return
        if (root.packageName?.toString() != cfg.targetPkg) return
        val tree = OrderParser.build(root)
        val texts = tree.texts
        val btn = OrderParser.findAccept(root)
        val box = Rect().also { btn?.getBoundsInScreen(it) }
        DrixPrefs.debug.value = (btn?.let { "[BOTÓN] ${it.className} clic=${it.isClickable} activo=${it.isEnabled} área=$box\n" } ?: "[BOTÓN] no encontrado\n") +
            texts.take(60).joinToString("\n")
        val zones = DrixPrefs.zones.value

        // A) Pantalla de DETALLE ("Aceptar por PEN…")
        if (OrderParser.isDetail(texts)) {
            if (accepting || ignoring) return
            val o = OrderParser.parseDetail(texts)
            val armed = isArmed()
            if (o == null && !armed) { overlay.show("👀 Detalle ilegible"); return }
            val sig = o?.sig ?: armedSig
            val decision: Decision = if (armed) Decision.Accept else OrderEvaluator.evaluate(o!!, cfg, zones)
            when (decision) {
                is Decision.Accept -> {
                    if (sig == detailSig && now - detailAt < 12000) return // ya aceptado hace poco
                    startAccept(cfg, sig, o?.fare)
                }
                is Decision.Reject -> {
                    overlay.show("❌ ${decision.why}", false)
                    if (sig == detailSig && now - detailAt < 12000) return
                    ignoring = true
                    handler.postDelayed({
                        val ok = click(rootInActiveWindow?.let { OrderParser.findText(it, "Ignorar oferta") })
                        if (!ok) performGlobalAction(GLOBAL_ACTION_BACK)
                        detailSig = sig; detailAt = System.currentTimeMillis(); ignoring = false
                    }, randomDelay(cfg))
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
                    if (listHandled(o.sig)) return
                    listPending = o.sig
                    armedSig = o.sig; armedAt = now // el detalle que se abra a continuación se aceptará directamente
                    val target = OrderParser.clickTarget(card.node)
                    val w = randomDelay(cfg)
                    handler.postDelayed({
                        // Verifica que la tarjeta siga mostrando el mismo pedido antes de pulsar
                        val same = card.node.refresh() && OrderParser.parse(OrderParser.build(card.node).texts)?.sig == o.sig
                        val ok = same && target.refresh() && click(target)
                        listPending = ""
                        if (ok) { listSig = o.sig; listAt = System.currentTimeMillis() }
                        else { armedSig = ""; overlay.show("⚠️ No pude tocar la tarjeta", false) }
                    }, w)
                    overlay.show("✅ S/ %.1f · %.2f/km · %s · %d ms".format(o.fare, o.perKm, o.district?.name ?: "?", w), true)
                    return
                }
                is Decision.Reject -> if (why.isEmpty()) why = d.why
            }
        }
        overlay.show("❌ ${cards.size} pedidos · $why", false)
    }

    /* ───────────── Botón verde «Aceptar por …» con reintentos ───────────── */

    private fun startAccept(cfg: Config, sig: String, fare: Float?) {
        accepting = true
        val w = randomDelay(cfg)
        overlay.show((if (fare != null) "✅ S/ %.1f · ".format(fare) else "✅ ") + "pulsando Aceptar en %d ms".format(w), true)
        handler.postDelayed({ tryAccept(sig, 0) }, w)
    }

    /**
     * Pulsa «Aceptar por …» y comprueba que la pantalla cambió. Si el botón sigue ahí, reintenta
     * alternando ACTION_CLICK y toque físico (gesto) hasta 6 veces.
     */
    private fun tryAccept(sig: String, attempt: Int) {
        val btn = rootInActiveWindow?.let { OrderParser.findAccept(it) }
        if (btn == null) {
            // Si ya habíamos pulsado y el botón desapareció, el pedido se aceptó
            finishAccept(sig, attempt > 0)
            return
        }
        if (attempt >= 6) { finishAccept(sig, false); return }
        // intentos pares: ACTION_CLICK; impares: toque físico. Si no hay ancestro clicable, cae al gesto.
        val fired = if (attempt % 2 == 0) (pressNode(btn) || tapNode(btn)) else tapNode(btn)
        overlay.show("🟢 Pulsando «Aceptar» · intento ${attempt + 1}${if (fired) "" else " (sin respuesta)"}", true)
        handler.postDelayed({ tryAccept(sig, attempt + 1) }, 750)
    }

    private fun finishAccept(sig: String, ok: Boolean) {
        accepting = false
        detailSig = sig; detailAt = System.currentTimeMillis()
        armedSig = ""
        if (ok) overlay.show("🟢 ¡Pedido aceptado!", true)
        else overlay.show("⚠️ No pude pulsar «Aceptar». Revisa Diagnóstico", false)
    }

    /** Botón "Refrescar" de Drix: reinicia el estado y recarga la lista de la app objetivo. */
    fun refresh(delayMs: Long = 0) {
        listPending = ""; listSig = ""; listAt = 0
        detailSig = ""; detailAt = 0; armedSig = ""; accepting = false; ignoring = false
        handler.removeCallbacksAndMessages(null); trailingScheduled = false
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
