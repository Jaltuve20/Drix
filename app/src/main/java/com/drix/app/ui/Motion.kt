package com.drix.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ═════════════ Colores extra (derivados de la paleta Drix) ═════════════ */

val Hairline = Color(0xFFE7E9ED)      // borde sutil de tarjetas
val Tint = Color(0xFFFFF7DB)          // amarillo muy suave para fondos de iconos/notas
val Danger = Color(0xFFDC2626)

/* ═════════════ Entradas ═════════════ */

/**
 * Aparece deslizando hacia arriba con fundido. [index] escalona la entrada de varias tarjetas.
 * Optimizado: ModulateAlpha evita crear un buffer offscreen por tarjeta (muy caro con sombras),
 * la animación es corta y el escalón máximo es pequeño para que nunca haya decenas de capas animadas a la vez.
 */
@Composable
fun Modifier.enter(index: Int = 0, dy: Dp = 10.dp): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
    return this.graphicsLayer {
        compositingStrategy = CompositingStrategy.ModulateAlpha
        alpha = a.value
        translationY = (1f - a.value) * dy.toPx()
    }
}

/** Aparece con un "pop" elástico (escala + fundido). */
@Composable
fun Modifier.popIn(delayMs: Long = 0L): Modifier {
    val s = remember { Animatable(0.6f) }
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        launch { a.animateTo(1f, tween(220)) }
        s.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }
    return this.graphicsLayer {
        compositingStrategy = CompositingStrategy.ModulateAlpha
        alpha = a.value
        scaleX = s.value; scaleY = s.value
    }
}

/* ═════════════ Pulsación ═════════════ */

/** Se encoge un poco al pulsar y rebota al soltar. Usa el mismo [src] que el clickable/botón. */
@Composable
fun Modifier.pressScale(src: MutableInteractionSource, to: Float = 0.95f): Modifier {
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) to else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press"
    )
    return this.graphicsLayer { scaleX = s; scaleY = s }
}

/* ═════════════ Números ═════════════ */

/**
 * Cuenta desde 0 hasta [target] la primera vez, y luego sigue los cambios con suavidad.
 * Devuelve un State: quien lo lea dentro de un lambda (o en un composable pequeño) recompone SOLO ese trozo,
 * no la pantalla entera 60 veces por segundo.
 */
@Composable
fun rememberCountUp(target: Float, durationMs: Int = 700): State<Float> {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { a.animateTo(target, tween(durationMs, easing = FastOutSlowInEasing)) }
    return a.asState()
}

/* ═════════════ Fondo en movimiento ═════════════ */

/**
 * Fondo vivo para cabeceras amarillas: burbujas que flotan y «líneas de velocidad» (como las de la D del logo)
 * que cruzan despacio. Se dibuja sobre el fondo ya pintado.
 */
@Composable
fun Modifier.heroMotion(): Modifier = this.drawWithCache {
    // ESTÁTICO a propósito: antes una animación infinita redibujaba el encabezado entero 60 veces/seg en cada pantalla,
    // lo que en teléfonos modestos dejaba sin tiempo al cambio de pestaña. Se dibuja una sola vez y queda en caché.
    val w = size.width; val h = size.height
    val white17 = Color.White.copy(alpha = .17f); val ink05 = Ink.copy(alpha = .05f)
    val white11 = Color.White.copy(alpha = .11f); val white20 = Color.White.copy(alpha = .20f)
    val barH = 7.dp.toPx(); val corner = CornerRadius(barH)
    onDrawBehind {
        drawCircle(white17, h * .58f, Offset(w * .94f, h * .10f))
        drawCircle(ink05, h * .42f, Offset(w * .02f, h * .98f))
        drawCircle(white11, h * .20f, Offset(w * .60f, h * .88f))
        for (i in 0..2) {
            val len = (110 + i * 46).dp.toPx()
            drawRoundRect(white20, Offset(w * (.50f + i * .12f), h * (0.22f + i * 0.20f)), Size(len, barH), corner)
        }
    }
}

/* ═════════════ Indicadores ═════════════ */

/** Punto de estado con ondas tipo radar cuando está [active]. */
@Composable
fun StatusPulse(color: Color, active: Boolean, dot: Dp = 14.dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val r by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "r")
    Box(Modifier.size(dot * 2.8f), Alignment.Center) {
        if (active) Canvas(Modifier.fillMaxSize()) {
            drawCircle(color.copy(alpha = (1f - r) * .5f), (dot.toPx() / 2f) * (1f + r * 2f))
        }
        Box(Modifier.size(dot).background(color, CircleShape))
    }
}

/** Tres puntos que saltan (cargando). */
@Composable
fun LoadingDots(color: Color = Ink) {
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..2) {
            val v by t.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse, initialStartOffset = StartOffset(i * 160)),
                label = "d$i"
            )
            Box(Modifier.size(9.dp).graphicsLayer { translationY = -8.dp.toPx() * v; alpha = .45f + .55f * v }.background(color, CircleShape))
        }
    }
}

/** Anillos que se expanden desde el centro (detrás del logo del splash). */
@Composable
fun RadarRings(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "radar")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "rp")
    Canvas(modifier) {
        val maxR = size.minDimension / 2f
        for (i in 0..2) {
            val f = (p + i / 3f) % 1f
            drawCircle(color.copy(alpha = (1f - f) * .28f), maxR * (0.35f + f * 0.65f), center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        }
    }
}

/** Flota suavemente arriba y abajo (iconos protagonistas). */
@Composable
fun Modifier.bob(amplitude: Dp = 6.dp, durationMs: Int = 1800): Modifier {
    val t = rememberInfiniteTransition(label = "bob")
    val v by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(durationMs, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bv")
    return this.graphicsLayer { translationY = v * amplitude.toPx() }
}
