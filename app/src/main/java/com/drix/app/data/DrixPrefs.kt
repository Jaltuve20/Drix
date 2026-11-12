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
    val targetPkg: String = "sinet.startup.inDriver" // app a automatizar (elegible)
)

/** Zona circular en coordenadas normalizadas 0..1 del mapa. */
data class Zone(val x: Float, val y: Float, val r: Float, val accept: Boolean)

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
            sp.getInt("h2", d.endHour), sp.getBoolean("on", false), sp.getString("pkg", d.targetPkg)!!
        )
        _zones.value = sp.getString("zones", "")!!.split(";").mapNotNull {
            val p = it.split(",")
            if (p.size != 4) null else Zone(p[0].toFloat(), p[1].toFloat(), p[2].toFloat(), p[3] == "1")
        }
    }

    fun update(t: (Config) -> Config) {
        val c = t(_cfg.value); _cfg.value = c
        sp.edit().putFloat("km", c.minPerKm).putFloat("pick", c.maxPickupKm)
            .putFloat("fare", c.minFare).putInt("h1", c.startHour)
            .putInt("h2", c.endHour).putBoolean("on", c.enabled).putString("pkg", c.targetPkg).apply()
    }

    fun setZones(z: List<Zone>) {
        _zones.value = z
        sp.edit().putString("zones", z.joinToString(";") { "${it.x},${it.y},${it.r},${if (it.accept) 1 else 0}" }).apply()
    }
}
