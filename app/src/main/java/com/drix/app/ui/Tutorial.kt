package com.drix.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class TPage(
    val emoji: String, val title: String, val intro: String? = null,
    val items: List<Pair<String, String>> = emptyList(), val example: String? = null, val tip: String? = null
)

private fun tutorialPages(name: String) = listOf(
    TPage("👋", if (name.isBlank()) "¡Bienvenido a Drix!" else "¡Hola, $name!",
        "Drix acepta por ti los pedidos que valen la pena. Tú decides qué es rentable y Drix hace el resto. Te lo explicamos en un minuto."),
    TPage("🔓", "Así se activa", "En Inicio enciende el interruptor. Drix te llevará a dar dos permisos, solo la primera vez:",
        listOf(
            "Mostrar sobre otras apps" to "Para ver el aviso de estado encima de tu app de pedidos.",
            "Accesibilidad → Drix Automático" to "Para leer los pedidos y pulsar «Aceptar» por ti."
        ),
        tip = "Si Android bloquea la accesibilidad: Ajustes → Apps → Drix → ⋮ → «Permitir ajustes restringidos»."),
    TPage("💰", "Parámetros: tus reglas", "En la pestaña Parámetros defines cuándo te conviene un pedido. Drix solo acepta si cumple TODAS las reglas:",
        listOf(
            "Distancia máx. de recogida (km)" to "Qué tan lejos puede estar el cliente de ti. Ej. 2.5 km.",
            "Distancia máx. de destino (km)" to "El largo máximo del viaje. Ej. 10 km.",
            "Precio por kilómetro (S/)" to "Lo mínimo que quieres ganar por cada km del viaje (precio ÷ km del viaje). Ej. S/ 1.20.",
            "Precio mínimo (S/)" to "Ninguna oferta por debajo de este monto. Ej. S/ 5.00."
        ),
        example = "Oferta S/ 6.50, viaje de 4.8 km → S/ 1.35 por km ✅ cumple (mínimo 1.20).\nOferta S/ 5.00, viaje de 9 km → S/ 0.56 por km ❌ se rechaza.",
        tip = "Después de editar, pulsa «Guardar cambios»."),
    TPage("⏰", "Horario y ritmo", null,
        listOf(
            "Desde / Hasta (h)" to "Horas del día (0 a 24) en que Drix trabaja. Fuera de ese horario no acepta nada.",
            "Retraso mín / máx (ms)" to "Espera al azar antes de pulsar «Aceptar». 1000 ms = 1 segundo. Los pedidos caducan rápido: mantenlo bajo.",
            "Aceptar si no se identifica el distrito" to "Si la dirección no dice el distrito y usas zonas ✅ Preferir, decide si Drix acepta igual o lo rechaza."
        )),
    TPage("🗺️", "Zonas del mapa (1 de 2)", "En la pestaña Zonas marcas distritos completos tocándolos en el mapa:",
        listOf(
            "Navegar" to "Mueve el mapa y haz zoom sin marcar nada.",
            "Crear zona" to "Elige 🚫 Bloquear o ✅ Preferir y toca un distrito.",
            "Eliminar zona" to "Toca un distrito marcado para quitarlo.",
            "Buscar" to "Escribe el nombre de un distrito para ir directo."
        ),
        tip = "«Mi ubicación» y «Borrar todas las zonas» están en el botón de ajustes, arriba a la derecha."),
    TPage("🚦", "Zonas (2 de 2): cómo decide Drix", null,
        listOf(
            "🚫 Bloquear" to "Rechaza siempre los pedidos cuyo recojo esté en ese distrito.",
            "✅ Preferir" to "Acepta SOLO pedidos de los distritos que prefieras. Si marcas Lince y Miraflores, todo lo demás se rechaza.",
            "Sin zonas" to "Si no marcas nada, Drix acepta en cualquier distrito (siempre que cumpla tus parámetros)."
        ),
        example = "Drix reconoce el distrito por la dirección del pedido, por ejemplo «(Puente Piedra)». Si la dirección no lo trae, se usa el interruptor de Parámetros."),
    TPage("🚀", "¡Listo para empezar!", "Te recomendamos empezar con reglas suaves y ajustarlas con los días.",
        listOf(
            "Mira el aviso flotante" to "Te dice si un pedido fue aceptado o por qué se rechazó (recojo lejos, pocos S/ por km, zona…).",
            "¿La lista se quedó quieta?" to "En Inicio usa «Refrescar» para volver a cargar los pedidos.",
            "¿Dudas o cambiar tus datos?" to "Escríbenos por WhatsApp desde la pestaña Perfil."
        ))
)

/** Tutorial de bienvenida: solo se muestra una vez, a las cuentas nuevas. */
@Composable
fun TutorialScreen(name: String, onFinish: () -> Unit) {
    val pages = remember(name) { tutorialPages(name) }
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    val progress by animateFloatAsState((pager.currentPage + 1f) / pages.size, tween(450), label = "prog")

    Column(Modifier.fillMaxSize().background(Color.White).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${pager.currentPage + 1} de ${pages.size}", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.weight(1f))
            if (!last) TextButton(onFinish) { Text("Omitir", color = Muted) }
        }
        // barra de progreso que se llena al avanzar
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(5.dp).clip(RoundedCornerShape(50)).background(Fog)) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Brush.horizontalGradient(listOf(YellowLight, YellowDeep)), RoundedCornerShape(50)))
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            Box(Modifier.graphicsLayer {
                val off = ((pager.currentPage - i) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                alpha = 1f - off * .65f
                val sc = 1f - off * .08f
                scaleX = sc; scaleY = sc
            }) { TutorialPage(pages[i]) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(pages.size) { i -> Dot(i == pager.currentPage) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (pager.currentPage > 0) OutlinedButton(
                { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                Modifier.height(54.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Line)
            ) { Text("Atrás", color = Ink, fontWeight = FontWeight.SemiBold) }
            PillButton(if (last) "¡Empezar!" else "Siguiente", Modifier.weight(1f)) {
                if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }
        }
    }
}

@Composable
private fun Dot(on: Boolean) {
    val w by animateDpAsState(if (on) 24.dp else 8.dp, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "dw")
    val c by animateColorAsState(if (on) Yellow else Line, tween(250), label = "dc")
    Box(Modifier.padding(3.dp).size(w, 8.dp).background(c, RoundedCornerShape(50)))
}

@Composable
private fun TutorialPage(p: TPage) = Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 14.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier.popIn().bob(7.dp).size(92.dp)
            .shadow(14.dp, shape, ambientColor = Yellow.copy(alpha = .5f), spotColor = YellowDeep.copy(alpha = .7f))
            .clip(shape).background(Brush.linearGradient(listOf(YellowLight, YellowDeep))),
        Alignment.Center
    ) { Text(p.emoji, fontSize = 44.sp) }
    Spacer(Modifier.height(20.dp))
    Text(p.title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink, textAlign = TextAlign.Center, letterSpacing = (-0.4).sp, modifier = Modifier.enter(1))
    p.intro?.let { Text(it, fontSize = 15.sp, color = Muted, lineHeight = 21.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp).enter(2)) }
    if (p.items.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            p.items.forEachIndexed { idx, (t, d) ->
                Row(
                    Modifier.fillMaxWidth().enter(idx + 3).background(Fog, RoundedCornerShape(18.dp)).padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(Modifier.padding(top = 5.dp).size(8.dp).background(Yellow, RoundedCornerShape(50)))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(t, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                        Text(d, fontSize = 13.sp, color = Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
    p.example?.let {
        Spacer(Modifier.height(14.dp))
        Text(it, fontSize = 13.sp, color = Ink, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth().enter(p.items.size + 3).background(Tint, RoundedCornerShape(18.dp)).padding(14.dp))
    }
    p.tip?.let { Text("💡 $it", fontSize = 12.sp, color = Muted, lineHeight = 17.sp, modifier = Modifier.padding(top = 14.dp).enter(p.items.size + 4)) }
    Spacer(Modifier.height(8.dp))
}
