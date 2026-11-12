package com.drix.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.drix.app.core.*
import android.os.Handler
import android.os.Looper
import kotlin.random.Random
import com.drix.app.data.DrixPrefs

class DrixAccessibilityService : AccessibilityService() {
    private lateinit var overlay: OverlayWidget
    private var lastRun = 0L
    private var lastClickSig = ""
    private var pendingSig = ""
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        DrixPrefs.init(this)
        overlay = OverlayWidget(this)
        overlay.show("Drix listo")
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        val cfg = DrixPrefs.cfg.value
        if (e == null || !cfg.enabled || e.packageName?.toString() != cfg.targetPkg) return
        // Antirrebote: máx. 1 lectura cada 300 ms
        val now = System.currentTimeMillis()
        if (now - lastRun < 300) return
        lastRun = now

        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return
        val order = OrderParser.parse(OrderParser.collectTexts(root)) ?: return
        val sig = "${order.fare}|${order.tripKm}|${order.pickupKm}"

        when (val d = OrderEvaluator.evaluate(order, cfg)) {
            is Decision.Accept -> {
                if (sig != lastClickSig && sig != pendingSig) { // evita doble clic sobre el mismo pedido
                    pendingSig = sig
                    // Retraso aleatorio entre min y max (configurable en Configuración)
                    val wait = Random.nextLong(cfg.minDelayMs.toLong(), cfg.maxDelayMs.toLong() + 1)
                    handler.postDelayed({
                        // Re-lee la pantalla: si el pedido ya desapareció, no hace clic
                        val btn = rootInActiveWindow?.let { OrderParser.findAccept(it) }
                        if (btn?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) lastClickSig = sig
                        pendingSig = ""
                    }, wait)
                    overlay.show("✅ S/ %.1f · %.2f/km · acepta en %d ms".format(order.fare, order.perKm, wait), true)
                }
            }
            is Decision.Reject -> overlay.show("❌ ${d.why}", false)
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); if (::overlay.isInitialized) overlay.hide(); super.onDestroy() }
}
