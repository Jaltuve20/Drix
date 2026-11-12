package com.drix.app.core

import android.view.accessibility.AccessibilityNodeInfo
import com.drix.app.data.*
import java.util.Calendar

data class Order(val fare: Float, val tripKm: Float, val pickupKm: Float, val district: District?) {
    val perKm get() = if (tripKm > 0) fare / tripKm else 0f
    val sig get() = "$fare|$tripKm|$pickupKm|${district?.name}"
}

/** Tarjeta de pedido en pantalla: datos leídos + nodo (para hacer clic). */
class Card(val order: Order, val node: AccessibilityNodeInfo)

sealed interface Decision {
    data object Accept : Decision
    data class Reject(val why: String) : Decision
}

object OrderParser {
    // Acepta "S/ 12.50", "PEN7", "PEN4.80"
    private val FARE = Regex("""(?:S/\.?|PEN)\s?(\d+(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE)
    // Acepta "~6,2 km", "3.2 km", "850 m"
    private val DIST = Regex("""(\d+(?:[.,]\d+)?)\s?(km|m)\b""", RegexOption.IGNORE_CASE)

    fun collectTexts(n: AccessibilityNodeInfo?, out: MutableList<String> = mutableListOf()): List<String> {
        if (n == null) return out
        (n.text ?: n.contentDescription)?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until n.childCount) collectTexts(n.getChild(i), out)
        return out
    }

    /** 1ª tarifa = precio; 1ª distancia = recojo (A); 2ª = viaje. Distrito = de la 1ª dirección. */
    fun parse(texts: List<String>): Order? {
        val joined = texts.joinToString(" | ")
        val fare = FARE.find(joined)?.groupValues?.get(1)?.replace(',', '.')?.toFloatOrNull() ?: return null
        val d = DIST.findAll(joined).map {
            val v = it.groupValues[1].replace(',', '.').toFloat()
            if (it.groupValues[2].equals("m", true)) v / 1000f else v
        }.toList()
        if (d.size < 2) return null
        return Order(fare, tripKm = d[1], pickupKm = d[0], district = Districts.findIn(texts))
    }

    /** La pantalla puede listar varios pedidos: devuelve el nodo más alto con exactamente 1 tarifa. */
    fun findCards(n: AccessibilityNodeInfo?, out: MutableList<Card> = mutableListOf()): List<Card> {
        if (n == null) return out
        val texts = collectTexts(n)
        val fares = FARE.findAll(texts.joinToString(" | ")).count()
        if (fares == 1) parse(texts)?.let { out.add(Card(it, n)) }
        else if (fares > 1) for (i in 0 until n.childCount) findCards(n.getChild(i), out)
        return out
    }

    private fun climb(s: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = s
        while (n != null && !n.isClickable) n = n.parent
        return n
    }

    /** Botón "Aceptar" dentro de la tarjeta; si no hay, la propia tarjeta (o su ancestro clicable). */
    fun clickTarget(card: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        card.findAccessibilityNodeInfosByText("Aceptar").firstOrNull()?.let(::climb) ?: climb(card)

    fun findAccept(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        root.findAccessibilityNodeInfosByText("Aceptar").firstOrNull()?.let(::climb)
}

object OrderEvaluator {
    fun evaluate(o: Order, c: Config, zones: List<Zone>): Decision {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val inShift = if (c.startHour <= c.endHour) h in c.startHour until c.endHour
                      else h >= c.startHour || h < c.endHour
        val greens = zones.filter { it.accept }.map { it.name }
        val reds = zones.filter { !it.accept }.map { it.name }
        return when {
            !inShift -> Decision.Reject("Fuera de horario")
            o.district?.name in reds -> Decision.Reject("Zona roja: ${o.district?.name}")
            greens.isNotEmpty() && o.district == null -> Decision.Reject("Distrito no identificado")
            greens.isNotEmpty() && o.district?.name !in greens -> Decision.Reject("Fuera de zona: ${o.district?.name}")
            o.fare < c.minFare -> Decision.Reject("Tarifa < S/ %.2f".format(c.minFare))
            o.pickupKm > c.maxPickupKm -> Decision.Reject("Recojo %.1f km > %.1f".format(o.pickupKm, c.maxPickupKm))
            o.perKm < c.minPerKm -> Decision.Reject("S/km %.2f < %.2f".format(o.perKm, c.minPerKm))
            else -> Decision.Accept
        }
    }
}
