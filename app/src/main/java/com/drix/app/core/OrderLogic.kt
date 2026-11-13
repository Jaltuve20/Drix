package com.drix.app.core

import android.view.accessibility.AccessibilityNodeInfo
import com.drix.app.data.*
import java.util.Calendar
import kotlin.math.abs

data class Order(val fare: Float, val tripKm: Float, val pickupKm: Float, val district: District?) {
    val perKm get() = if (tripKm > 0) fare / tripKm else Float.NaN   // NaN = desconocido (no se filtra)
    val sig get() = "$fare|$tripKm|$pickupKm|${district?.name}"
}

/** Nodo con sus textos precalculados: un solo recorrido del árbol (menos carga sobre la app objetivo). */
class TNode(val node: AccessibilityNodeInfo, own: String?, val kids: List<TNode>) {
    val texts: List<String> = listOfNotNull(own) + kids.flatMap { it.texts }
}

/** Tarjeta de pedido en la lista: datos leídos + nodo (para hacer clic). */
class Card(val order: Order, val node: AccessibilityNodeInfo)

sealed interface Decision {
    data object Accept : Decision
    data class Reject(val why: String) : Decision
}

object OrderParser {
    private val FARE = Regex("""(?:S/\.?|PEN)\s?(\d+(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE)       // PEN7, S/ 12.50
    private val DIST = Regex("""(\d+(?:[.,]\d+)?)\s?(km|m)\b""", RegexOption.IGNORE_CASE)                 // ~6,2 km, 850 m
    private val ACCEPT_FARE = Regex("""Aceptar por[\s|]*(?:PEN|S/)\s?(\d+(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val PERKM = Regex("""S/\s?\d+(?:[.,]\d+)?/km[\s|]*~?\s?(\d+(?:[.,]\d+)?)\s?km""", RegexOption.IGNORE_CASE)

    private fun num(s: String) = s.replace(',', '.').toFloatOrNull()
    private fun distances(joined: String) = DIST.findAll(joined).map {
        val v = it.groupValues[1].replace(',', '.').toFloat()
        if (it.groupValues[2].equals("m", true)) v / 1000f else v
    }.toList()

    fun build(n: AccessibilityNodeInfo, depth: Int = 0): TNode {
        val kids = if (depth > 25) emptyList() else (0 until n.childCount).mapNotNull { n.getChild(it) }.map { build(it, depth + 1) }
        return TNode(n, (n.text ?: n.contentDescription)?.toString()?.takeIf { it.isNotBlank() }, kids)
    }

    /** Tarjeta de la LISTA: 1ª tarifa = precio; 1ª distancia = recojo (A); 2ª = viaje. */
    fun parse(texts: List<String>): Order? {
        val joined = texts.joinToString(" | ").replace('\u00A0', ' ').replace('\u202F', ' ')
        val fare = FARE.find(joined)?.groupValues?.get(1)?.let(::num) ?: return null
        val d = distances(joined)
        if (d.size < 2) return null
        return Order(fare, tripKm = d[1], pickupKm = d[0], district = Districts.findIn(texts))
    }

    /** Pantalla de DETALLE ("Aceptar por PEN6.50"): precio del botón; recojo = "~9,5 km" junto a S/x/km. */
    fun isDetail(texts: List<String>) = texts.any { it.replace('\u00A0', ' ').contains("Aceptar por", true) }
    fun parseDetail(texts: List<String>): Order? {
        val joined = texts.joinToString(" | ").replace('\u00A0', ' ').replace('\u202F', ' ')
        val fare = ACCEPT_FARE.find(joined)?.groupValues?.get(1)?.let(::num) ?: return null
        val all = distances(joined)
        val pickup = PERKM.find(joined)?.groupValues?.get(1)?.let(::num) ?: all.getOrNull(0) ?: Float.NaN
        val trip = all.firstOrNull { abs(it - pickup) > 0.01f } ?: Float.NaN
        return Order(fare, trip, pickup, Districts.findIn(texts))
    }

    /** La lista muestra varios pedidos: devuelve el nodo más alto con exactamente 1 tarifa. */
    fun findCards(t: TNode, out: MutableList<Card> = mutableListOf()): List<Card> {
        val fares = FARE.findAll(t.texts.joinToString(" | ")).count()
        if (fares == 1) parse(t.texts)?.let { out.add(Card(it, t.node)) }
        else if (fares > 1) t.kids.forEach { findCards(it, out) }
        return out
    }

    private fun norm(s: CharSequence?): String =
        s?.toString()?.replace('\u00A0', ' ')?.replace('\u202F', ' ')?.trim().orEmpty()

    /** Búsqueda propia por el árbol (la del sistema falla con espacios especiales o textos en contentDescription). */
    fun findNode(n: AccessibilityNodeInfo, depth: Int = 0, pred: (String) -> Boolean): AccessibilityNodeInfo? {
        val t = norm(n.text).ifEmpty { norm(n.contentDescription) }
        if (t.isNotEmpty() && pred(t)) return n
        if (depth > 25) return null
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            findNode(c, depth + 1, pred)?.let { return it }
        }
        return null
    }

    fun findText(root: AccessibilityNodeInfo, s: String): AccessibilityNodeInfo? = findNode(root) { it.contains(s, true) }
    fun clickTarget(card: AccessibilityNodeInfo) = findText(card, "Aceptar") ?: card

    /** Botón verde del detalle: «Aceptar por …»; como alternativa, un texto corto que empiece por «Aceptar». */
    fun findAccept(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findNode(root) { it.startsWith("Aceptar por", true) }
            ?: findNode(root) { it.startsWith("Aceptar", true) && it.length <= 30 }
}

object OrderEvaluator {
    fun evaluate(o: Order, c: Config, zones: List<Zone>): Decision {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val inShift = if (c.startHour <= c.endHour) h in c.startHour until c.endHour
                      else h >= c.startHour || h < c.endHour
        val greens = zones.filter { it.accept }.map { it.name }
        val reds = zones.filter { !it.accept }.map { it.name }
        val dn = o.district?.name
        return when {
            !inShift -> Decision.Reject("Fuera de horario")
            dn != null && dn in reds -> Decision.Reject("Zona roja: $dn")
            greens.isNotEmpty() && dn == null && !c.unknownOk -> Decision.Reject("Distrito no identificado")
            greens.isNotEmpty() && dn != null && dn !in greens -> Decision.Reject("Fuera de zona: $dn")
            o.fare < c.minFare -> Decision.Reject("Tarifa < S/ %.2f".format(c.minFare))
            o.pickupKm > c.maxPickupKm -> Decision.Reject("Recojo %.1f km > %.1f".format(o.pickupKm, c.maxPickupKm))
            o.tripKm > c.maxTripKm -> Decision.Reject("Destino %.1f km > %.1f".format(o.tripKm, c.maxTripKm))
            o.perKm < c.minPerKm -> Decision.Reject("S/km %.2f < %.2f".format(o.perKm, c.minPerKm))
            else -> Decision.Accept
        }
    }
}
