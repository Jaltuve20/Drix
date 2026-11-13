package com.drix.app.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Resumen de un día: dinero (S/), kilómetros y cantidad de pedidos que Drix aceptó. */
data class DayStats(val fare: Float = 0f, val km: Float = 0f, val orders: Int = 0)

/**
 * Estadísticas "Extras". Se guardan por día (se conservan los últimos 14).
 * Son ESTIMADAS: cuentan cada pedido que Drix acepta (tarifa del pedido; km = recojo + viaje).
 */
object DrixStats {
    private lateinit var sp: SharedPreferences
    private val _days = MutableStateFlow<Map<String, DayStats>>(emptyMap())
    val days: StateFlow<Map<String, DayStats>> = _days.asStateFlow()

    private var lastSig = ""
    private var lastAt = 0L

    fun dayKey(ms: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("drix_stats", Context.MODE_PRIVATE)
        _days.value = sp.all.mapNotNull { (k, v) ->
            if (!k.startsWith("d_") || v !is String) return@mapNotNull null
            val p = v.split("|")
            val f = p.getOrNull(0)?.toFloatOrNull(); val km = p.getOrNull(1)?.toFloatOrNull(); val n = p.getOrNull(2)?.toIntOrNull()
            if (f == null || km == null || n == null) null else k.removePrefix("d_") to DayStats(f, km, n)
        }.toMap()
    }

    /** Suma un pedido aceptado. Ignora el mismo pedido si se repite en menos de 60 s. */
    fun record(fare: Float, km: Float, sig: String) {
        if (!::sp.isInitialized) return
        val now = System.currentTimeMillis()
        if (sig == lastSig && now - lastAt < 60_000) return
        lastSig = sig; lastAt = now
        val key = dayKey(now)
        val cur = _days.value[key] ?: DayStats()
        val next = cur.copy(fare = cur.fare + fare, km = cur.km + km, orders = cur.orders + 1)
        val all = (_days.value + (key to next)).toSortedMap().entries.toList().takeLast(14).associate { it.key to it.value }
        _days.value = all
        val e = sp.edit().clear()
        all.forEach { (k, v) -> e.putString("d_$k", "${v.fare}|${v.km}|${v.orders}") }
        e.apply()
    }

    /** Pone en cero el día indicado (hoy por defecto). */
    fun resetDay(key: String = dayKey()) {
        if (!::sp.isInitialized) return
        _days.value = _days.value - key
        sp.edit().remove("d_$key").apply()
    }
}
