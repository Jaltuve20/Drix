package com.drix.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.drix.app.core.*
import com.drix.app.data.DrixPrefs

class DrixAccessibilityService : AccessibilityService() {
    private lateinit var overlay: OverlayWidget
    private var lastRun = 0L
    private var lastClickSig = ""

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
                if (sig != lastClickSig) { // evita doble clic sobre el mismo pedido
                    val btn = OrderParser.findAccept(root)
                    if (btn?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) lastClickSig = sig
                }
                overlay.show("✅ S/ %.1f · %.2f/km".format(order.fare, order.perKm), true)
            }
            is Decision.Reject -> overlay.show("❌ ${d.why}", false)
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() { if (::overlay.isInitialized) overlay.hide(); super.onDestroy() }
}
