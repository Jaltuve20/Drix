package com.drix.app.data

import java.text.Normalizer
import kotlin.math.*

/** Distrito con centro aproximado y radio (m) para dibujarlo y localizarlo en el mapa. */
data class District(val name: String, val lat: Double, val lon: Double, val r: Float, val aliases: List<String>)

object Districts {
    // nombre,lat,lon,radio_m,alias1|alias2  (valores aproximados)
    private const val RAW = """
Ancón,-11.7750,-77.1750,4000
Ate,-12.0258,-76.9178,5000
Barranco,-12.1400,-77.0210,1200
Bellavista,-12.0620,-77.1100,1200
Breña,-12.0580,-77.0520,1000
Callao,-12.0560,-77.1180,4000
Carabayllo,-11.8650,-77.0330,6000
Carmen de la Legua,-12.0450,-77.0950,1000,Carmen de la Legua Reynoso
Cercado de Lima,-12.0464,-77.0428,2500,Lima Cercado
Chaclacayo,-11.9750,-76.7660,4000
Chorrillos,-12.1700,-77.0170,3000
Cieneguilla,-12.0950,-76.8070,6000
Comas,-11.9400,-77.0600,3500
El Agustino,-12.0450,-77.0100,1500
Independencia,-11.9900,-77.0540,2000
Jesús María,-12.0700,-77.0520,1000
La Molina,-12.0830,-76.9400,4500
La Perla,-12.0710,-77.1170,1200
La Punta,-12.0720,-77.1650,800
La Victoria,-12.0670,-77.0150,1500
Lince,-12.0840,-77.0360,900
Los Olivos,-11.9700,-77.0720,3000
Lurigancho,-11.9360,-76.7020,7000,Chosica|Lurigancho Chosica
Lurín,-12.2760,-76.8700,6000
Magdalena del Mar,-12.0950,-77.0710,1100,Magdalena
Mi Perú,-11.8570,-77.1180,1200
Miraflores,-12.1190,-77.0290,2000
Pachacámac,-12.2300,-76.8670,7000
Pucusana,-12.4800,-76.7980,4000
Pueblo Libre,-12.0720,-77.0640,1200
Puente Piedra,-11.8670,-77.0750,5000
Punta Hermosa,-12.3340,-76.8230,3000
Punta Negra,-12.3640,-76.7930,3000
Rímac,-12.0290,-77.0430,2000
San Bartolo,-12.3880,-76.7800,2500
San Borja,-12.1000,-77.0000,1800
San Isidro,-12.0970,-77.0360,1500
San Juan de Lurigancho,-11.9770,-77.0000,5000,SJL
San Juan de Miraflores,-12.1550,-76.9700,3000,SJM
San Luis,-12.0770,-76.9970,1000
San Martín de Porres,-12.0000,-77.0900,4000,SMP
San Miguel,-12.0770,-77.0870,1800
Santa Anita,-12.0450,-76.9720,1800
Santa María del Mar,-12.3990,-76.7740,1500
Santa Rosa,-11.8000,-77.1650,3000
Santiago de Surco,-12.1450,-76.9930,4000,Surco
Surquillo,-12.1110,-77.0120,1200
Ventanilla,-11.8750,-77.1250,6000
Villa El Salvador,-12.2130,-76.9300,4500,VES
Villa María del Triunfo,-12.1670,-76.9300,4000,VMT
"""
    val all: List<District> = RAW.trim().lines().map { l ->
        val p = l.split(",")
        District(p[0].trim(), p[1].toDouble(), p[2].toDouble(), p[3].toFloat(), p.getOrNull(4)?.split("|") ?: emptyList())
    }

    fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    // (regex del nombre o alias, distrito)
    private val keys: List<Pair<Regex, District>> = all.flatMap { d ->
        (listOf(d.name) + d.aliases).map { Regex("(?<![a-z])" + Regex.escape(norm(it)) + "(?![a-z])") to d }
    }

    private fun dist(a: Double, b: Double, c: Double, d: Double): Double {
        val p1 = Math.toRadians(a); val p2 = Math.toRadians(c); val dl = Math.toRadians(d - b)
        val h = sin((p2 - p1) / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * 6371000.0 * asin(sqrt(h))
    }

    /** Distrito bajo un punto del mapa (el más cercano a su centro dentro de su radio). */
    fun at(lat: Double, lon: Double): District? =
        all.filter { dist(it.lat, it.lon, lat, lon) <= it.r }.minByOrNull { dist(it.lat, it.lon, lat, lon) / it.r }

    private fun scan(s: String): District? {
        val t = norm(s)
        return keys.mapNotNull { (rx, d) -> rx.find(t)?.let { it.range.first to d } }.minByOrNull { it.first }?.second
    }

    /** Busca el distrito: primero dentro de paréntesis "(… Puente Piedra)", luego en todo el texto. */
    fun findIn(texts: List<String>): District? {
        val paren = Regex("\\(([^)]*)\\)")
        for (t in texts) for (m in paren.findAll(t)) scan(m.groupValues[1])?.let { return it }
        for (t in texts) scan(t)?.let { return it }
        return null
    }
}
