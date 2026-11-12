package com.drix.app.core

import android.view.accessibility.AccessibilityNodeInfo
import com.drix.app.data.Config
import java.util.Calendar

data class Order(val fare: Float, val tripKm: Float, val pickupKm: Float) {
    val perKm get() = if (tripKm > 0) fare / tripKm else 0f
}

sealed interface Decision {
    data object Accept : Decision
    data class Reject(val why: String) : Decision
}

object OrderParser {
    private val FARE = Regex("""S/\.?\s?(\d+(?:[.,]\d{1,2})?)""")
    // Captura "3.2 km" o "850 m" (los metros se convierten a km)
    private val DIST = Regex("""(\d+(?:[.,]\d+)?)\s?(km|m)\b""", RegexOption.IGNORE_CASE)

    /** Recorre el árbol de nodos y devuelve todos los textos visibles. */
    fun collectTexts(n: AccessibilityNodeInfo?, out: MutableList<String> = mutableListOf()): List<String> {
        if (n == null) return out
        n.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until n.childCount) collectTexts(n.getChild(i), out)
        return out
    }

    /**
     * Heurística: 1ª cifra con S/ = tarifa; 1ª distancia = recojo; 2ª = viaje.
     * Ajusta el orden con Layout Inspector según la versión de inDrive.
     */
    fun parse(texts: List<String>): Order? {
        val joined = texts.joinToString(" | ")
        val fare = FARE.find(joined)?.groupValues?.get(1)?.replace(',', '.')?.toFloatOrNull() ?: return null
        val d = DIST.findAll(joined).map {
            val v = it.groupValues[1].replace(',', '.').toFloat()
            if (it.groupValues[2].equals("m", true)) v / 1000f else v
        }.toList()
        if (d.size < 2) return null
        return Order(fare, tripKm = d[1], pickupKm = d[0])
    }

    /** Busca el botón "Aceptar" y sube hasta el primer ancestro clicable. */
    fun findAccept(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n = root.findAccessibilityNodeInfosByText("Aceptar").firstOrNull() ?: return null
        while (!n.isClickable) n = n.parent ?: return null
        return n
    }
}

object OrderEvaluator {
    fun evaluate(o: Order, c: Config): Decision {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val inShift = if (c.startHour <= c.endHour) h in c.startHour until c.endHour
                      else h >= c.startHour || h < c.endHour
        return when {
            !inShift -> Decision.Reject("Fuera de horario")
            o.fare < c.minFare -> Decision.Reject("Tarifa < S/ %.2f".format(c.minFare))
            o.pickupKm > c.maxPickupKm -> Decision.Reject("Recojo > %.1f km".format(c.maxPickupKm))
            o.perKm < c.minPerKm -> Decision.Reject("S/km %.2f < %.2f".format(o.perKm, c.minPerKm))
            else -> Decision.Accept
        }
    }
}
