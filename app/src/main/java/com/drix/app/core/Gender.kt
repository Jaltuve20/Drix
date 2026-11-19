package com.drix.app.core

import com.drix.app.data.Districts

enum class Sex { MALE, FEMALE }

/**
 * Filtro por género. La app de pedidos NO muestra el género del pasajero, solo su nombre,
 * así que se deduce por el nombre (lista de nombres comunes + terminación -a / -o).
 * Si el nombre no permite saberlo con seguridad, devuelve null y el pedido NO se bloquea.
 */
object Gender {
    private fun set(s: String) = s.trim().split(Regex("\\s+")).toSet()

    // Nombres que la regla de la terminación (-a mujer, -o hombre) no cubre o cubriría mal.
    private val FEMALE = set("""
        isabel carmen pilar raquel ruth esther miriam mirian abigail beatriz gladys doris iris liz lizbeth lizeth lisbeth
        nicol nicole michelle jennifer jenny yenny yeny yeni mercedes soledad luz dolores ines nieves ivonne annie brigitte
        giselle gisselle noemi nohemi marleni marleny evelin evelyn shirley sherly kimberly nelly kelly wendy sandy heidy
        leidy leydi lady mary betty bessy betsy cindy lesly leslie ashley emily yuli yuly maribel maricarmen marisol rocio
        socorro consuelo amparo araceli aracely yazmin yasmin jazmin jasmin dulce elizabeth mabel yaneth janeth yanet
        jhoselyn joselyn jhosselyn madeleine maite magaly nathaly nataly maryori rosmery rosmary guadalupe lupe mari maru
        gaby gabi vicky vivi luly tere fabi caro carol cristal crystal sharon scarlet scarleth estefany stefany stephany
        yaritza yadira yeraldine heydi hayde dayanara sheyla sheila lizet lisset lizette
    """)
    private val MALE = set("""
        juan jose luis carlos jorge miguel manuel javier jesus david daniel gabriel hector ivan jhon john jhonatan jonathan
        jordan joel kevin marcos nelson nestor nicolas oscar omar rafael ramon raul ruben samuel victor walter wilmer
        william alex alexander andres angel anthony antony benjamin brayan bryan brian cesar christian cristian cristhian
        dennis denis edgar edwin elias elmer enrique eric erick ernesto esteban felix fabian frank freddy fredy german
        gerson gilbert gian harold henry herbert hernan ismael isaac jairo jean jefferson jeremy jhordan jimmy joaquin
        johan jhonny junior leonel lenin lionel marlon matias max michael moises neil noe noel percy piero raymundo
        reynaldo roy royer saul sebastian stiven steven thiago tomas ulises yair yerson yhon yonel yordy yeison abel adan
        adrian agustin alan aldo amilcar anderson aron axel bernardo boris carlo cristofer christopher dario derek dylan
        edson efrain erwin evert ezequiel fidel gael gregorio helmer hilario ignacio imanol iker jhan jonas kenny
        kenneth leandro liam lucas tobias isaias hans jhoel mathias santos jhoan jhosep josep jhonel
    """)
    // Terminan en -a pero son de hombre / terminan en -o pero son de mujer: ya están cubiertos arriba o aquí.
    private val MALE_A = set("luca nicola joshua jhoshua")

    fun of(name: String?): Sex? {
        val first = Districts.norm(name.orEmpty()).trim().split(" ").firstOrNull().orEmpty().replace(Regex("[^a-z]"), "")
        if (first.length < 2) return null
        return when {
            first in FEMALE -> Sex.FEMALE
            first in MALE || first in MALE_A -> Sex.MALE
            first.endsWith("a") -> Sex.FEMALE
            first.endsWith("o") -> Sex.MALE
            else -> null
        }
    }

    /** Motivo de rechazo si el pasajero está bloqueado por [mode] (0 = todos · 1 = sin hombres · 2 = sin mujeres). */
    fun blockedReason(name: String?, mode: Int): String? {
        if (mode == 0 || name.isNullOrBlank()) return null
        return when (of(name)) {
            Sex.MALE -> if (mode == 1) "Pasajero hombre ($name)" else null
            Sex.FEMALE -> if (mode == 2) "Pasajera mujer ($name)" else null
            null -> null
        }
    }

    private val RATING = Regex("""^[★⭐\s]*\d[.,]\d{1,2}(\s*\(\d+\))?$""")
    private val NAME = Regex("""^[\p{L}][\p{L}'’\-]{1,19}(\s[\p{L}][\p{L}'’\-]{1,19})?$""")
    private val STOP = set("plin yape efectivo entregas viaje aceptar ignorar oferta justo ahora nuevo nueva pen min seg km")

    /** El nombre del pasajero es el texto que va justo antes de su calificación («Fabiana» → «★4.26»). */
    fun passengerName(texts: List<String>): String? {
        val t = texts.map { it.replace('\u00A0', ' ').trim() }
        fun ok(s: String) = NAME.matches(s) && Districts.norm(s).trim() !in STOP
        for (i in 1 until t.size) if (RATING.matches(t[i]) && ok(t[i - 1])) return t[i - 1]
        for (i in 2 until t.size) if (RATING.matches(t[i]) && t[i - 1].length <= 2 && ok(t[i - 2])) return t[i - 2]
        return null
    }
}
