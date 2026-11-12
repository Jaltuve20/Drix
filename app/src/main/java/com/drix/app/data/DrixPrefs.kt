package com.drix.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.*

/** Filtros de rentabilidad + estado de la automatización. */
data class Config(
    val minPerKm: Float = 1.2f,      // S/ por km mínimo
    val maxPickupKm: Float = 2.5f,   // distancia máx. de recojo
    val minFare: Float = 5f,         // tarifa mínima por viaje (S/)
    val startHour: Int = 6,          // horario de trabajo
    val endHour: Int = 23,
    val enabled: Boolean = false,    // automatización ON/OFF
    val targetPkg: String = "sinet.startup.inDriver", // app a automatizar (elegible)
    val minDelayMs: Int = 400,   // retraso mínimo antes de pulsar Aceptar
    val maxDelayMs: Int = 1200   // retraso máximo (se elige al azar entre ambos)
)

/** Distrito marcado: aceptar (verde) o rechazar (rojo). */
data class Zone(val name: String, val accept: Boolean)

/** Almacén único (SharedPreferences + StateFlow) compartido por UI y servicio. */
object DrixPrefs {
    private lateinit var sp: SharedPreferences
    private val _cfg = MutableStateFlow(Config())
    private val _zones = MutableStateFlow<List<Zone>>(emptyList())
    val cfg = _cfg.asStateFlow()
    val zones = _zones.asStateFlow()

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("drix", Context.MODE_PRIVATE)
        val d = Config()
        _cfg.value = Config(
            sp.getFloat("km", d.minPerKm), sp.getFloat("pick", d.maxPickupKm),
            sp.getFloat("fare", d.minFare), sp.getInt("h1", d.startHour),
            sp.getInt("h2", d.endHour), sp.getBoolean("on", false), sp.getString("pkg", d.targetPkg)!!,
            sp.getInt("dmin", d.minDelayMs), sp.getInt("dmax", d.maxDelayMs)
        )
        _zones.value = sp.getString("zones3", "")!!.split(";").mapNotNull {
            val p = it.split(":")
            if (p.size != 2) null else Zone(p[0], p[1] == "1")
        }
    }

    fun update(t: (Config) -> Config) {
        val c = t(_cfg.value); _cfg.value = c
        sp.edit().putFloat("km", c.minPerKm).putFloat("pick", c.maxPickupKm)
            .putFloat("fare", c.minFare).putInt("h1", c.startHour)
            .putInt("h2", c.endHour).putBoolean("on", c.enabled).putString("pkg", c.targetPkg)
            .putInt("dmin", c.minDelayMs).putInt("dmax", c.maxDelayMs).apply()
    }

    fun setZones(z: List<Zone>) {
        _zones.value = z
        sp.edit().putString("zones3", z.joinToString(";") { "${it.name}:${if (it.accept) 1 else 0}" }).apply()
    }
}
