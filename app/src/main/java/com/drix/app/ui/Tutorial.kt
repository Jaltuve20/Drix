package com.drix.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    TPage("🫧", "El botón flotante", "El logo de Drix flota sobre tus apps para controlarlo sin abrir Drix:",
        listOf(
            "Un toque" to "Activa o pausa Drix al instante. Anillo verde = activo, rojo = pausado.",
            "Mantenlo pulsado" to "Salen «Abrir Drix» y ✕ para ocultar el botón.",
            "Arrástralo" to "Muévelo donde no estorbe: se pega al borde y recuerda su lugar."
        ),
        tip = "¿Lo ocultaste? Vuelve a mostrarlo en Perfil → «Mostrar botón flotante»."),
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
            "Palabras bloqueadas" to "Escribe frases como «casco doble» y Drix rechaza todo pedido que las mencione (en la tarjeta o en el comentario).",
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
            "🚫 Bloquear" to "Rechaza siempre los pedidos cuyo recojo O destino esté en ese distrito (también por zonas como Vitarte o Huaycán en Ate).",
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
            }) { if (i == 0) TutorialIntro(name) else TutorialPage(pages[i]) }
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


/* ═════════════ Intro animada (primera pantalla del tutorial) ═════════════ */

private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun backOut(x: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1f; val y = x - 1f; return 1f + c3 * y * y * y + c1 * y * y }
/** Visibilidad de una escena entre [a] y [b] segundos, con fundidos de .35 s. */
private fun win(t: Float, a: Float, b: Float) = seg(t, a, a + .35f) * (1f - seg(t, b - .35f, b))

/** Pequeño «video» en bucle: el pedido bueno dura segundos → logo de Drix → filtros/zonas/palabras → Drix se activa. */
@Composable
private fun TutorialIntro(name: String) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { while (true) { t.snapTo(0f); t.animateTo(9.6f, tween(9600, easing = LinearEasing)) } }
    val inf = rememberInfiniteTransition(label = "intro")
    val streak by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "streak")
    val on by remember { derivedStateOf { t.value > 8.5f } }
    val dot by animateColorAsState(if (on) Go else Color(0xFFEF4444), tween(250), label = "dot")

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(32.dp))
                .background(Brush.verticalGradient(listOf(Charcoal, Ink)))
                .drawBehind {
                    val w = size.width; val h = size.height
                    drawCircle(Yellow.copy(alpha = .10f), h * .42f, Offset(w * .98f, h * .02f))
                    for (i in 0..6) { // líneas de velocidad que cruzan de derecha a izquierda
                        val len = w * (.18f + (i % 3) * .07f)
                        val ph = (streak + i * .19f) % 1f
                        drawRoundRect(Yellow.copy(alpha = .30f), Offset(w - ph * (w + len), h * (.07f + i * .135f)), Size(len, 3.dp.toPx()), CornerRadius(3.dp.toPx()))
                    }
                },
            Alignment.Center
        ) {
            // ESCENA 1 · el pedido bueno dura segundos
            Column(Modifier.fillMaxWidth().padding(24.dp).graphicsLayer { alpha = win(t.value, 0f, 3.0f) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("El pedido bueno", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { val p = backOut(seg(t.value, .1f, .7f)); scaleX = p; scaleY = p })
                Text("dura segundos.", color = Yellow, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { val p = backOut(seg(t.value, .6f, 1.2f)); scaleX = p; scaleY = p })
                Box(
                    Modifier.padding(top = 22.dp).fillMaxWidth(.7f).height(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .12f))
                        .drawBehind { drawRoundRect(Yellow, size = Size(size.width * (1f - seg(t.value, 1.2f, 2.8f)), size.height), cornerRadius = CornerRadius(size.height)) }
                )
                Text("Mientras manejas, otro conductor ya lo tomó.", color = Color.White.copy(alpha = .7f), fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp).graphicsLayer { alpha = seg(t.value, 1.5f, 2.1f) })
            }
            // ESCENA 2 · llega Drix
            Column(Modifier.fillMaxWidth().padding(24.dp).graphicsLayer { alpha = win(t.value, 3.0f, 6.2f) }, horizontalAlignment = Alignment.CenterHorizontally) {
                if (name.isNotBlank()) Text("¡Hola, $name!", color = Color.White.copy(alpha = .75f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 14.dp).graphicsLayer { alpha = seg(t.value, 3.4f, 3.9f) })
                Box(Modifier.graphicsLayer { val p = backOut(seg(t.value, 3.1f, 3.9f)); scaleX = p; scaleY = p }) { DrixWordmark(58.dp, Yellow) }
                Text("Acepta por ti los pedidos", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 22.dp).graphicsLayer { alpha = seg(t.value, 4.0f, 4.6f); translationY = (1f - seg(t.value, 4.0f, 4.6f)) * 24f })
                Text("que te convienen.", color = Yellow, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = seg(t.value, 4.4f, 5.0f); translationY = (1f - seg(t.value, 4.4f, 5.0f)) * 24f })
            }
            // ESCENA 3 · tus reglas y Drix se activa
            Column(Modifier.fillMaxWidth().padding(22.dp).graphicsLayer { alpha = win(t.value, 6.2f, 9.6f) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tú pones las reglas", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 4.dp).graphicsLayer { alpha = seg(t.value, 6.4f, 6.9f) })
                listOf("💰  Precio y distancia" to 6.7f, "🗺️  Zonas que sí y que no" to 7.1f, "🚫  Palabras bloqueadas" to 7.5f).forEachIndexed { i, (txt, st) ->
                    val dir = if (i % 2 == 0) -1f else 1f
                    Text(txt, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(.88f)
                            .graphicsLayer { val p = backOut(seg(t.value, st, st + .55f)); alpha = seg(t.value, st, st + .25f); translationX = (1f - p) * dir * size.width * .7f }
                            .background(Color.White.copy(alpha = .09f), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 11.dp))
                }
                Row(
                    Modifier.padding(top = 8.dp)
                        .graphicsLayer { val p = backOut(seg(t.value, 8.0f, 8.6f)); scaleX = p; scaleY = p }
                        .background(Color(0xFF2B3036), RoundedCornerShape(50)).padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(12.dp).background(dot, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(if (on) "Activa" else "Pausada", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Desliza para ver cómo funciona  →", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
