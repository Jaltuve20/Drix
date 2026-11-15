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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/* ═════════════ Colores extra (derivados de la paleta Drix) ═════════════ */

val Hairline = Color(0xFFE7E9ED)      // borde sutil de tarjetas
val Tint = Color(0xFFFFF7DB)          // amarillo muy suave para fondos de iconos/notas
val Danger = Color(0xFFDC2626)

/* ═════════════ Entradas ═════════════ */

/** Aparece deslizando hacia arriba con fundido. [index] escalona la entrada de varias tarjetas. */
@Composable
fun Modifier.enter(index: Int = 0, dy: Dp = 22.dp): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(8) * 70L)
        a.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
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
        a.animateTo(1f, tween(260))
    }
    LaunchedEffect(Unit) {
        delay(delayMs)
        s.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }
    return this.graphicsLayer {
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

/** Cuenta desde 0 hasta [target] la primera vez, y luego sigue los cambios con suavidad. */
@Composable
fun rememberCountUp(target: Float, durationMs: Int = 900): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { a.animateTo(target, tween(durationMs, easing = FastOutSlowInEasing)) }
    return a.value
}

/* ═════════════ Fondo en movimiento ═════════════ */

/**
 * Fondo vivo para cabeceras amarillas: burbujas que flotan y «líneas de velocidad» (como las de la D del logo)
 * que cruzan despacio. Se dibuja sobre el fondo ya pintado.
 */
@Composable
fun Modifier.heroMotion(): Modifier {
    val t = rememberInfiniteTransition(label = "hero")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart), label = "p")
    return this.drawBehind {
        val w = size.width; val h = size.height
        val a = p * 2f * Math.PI.toFloat()
        drawCircle(Color.White.copy(alpha = .17f), h * .58f, Offset(w * .94f + cos(a) * 18f, h * .10f + sin(a) * 14f))
        drawCircle(Ink.copy(alpha = .05f), h * .42f, Offset(w * .02f + cos(a + 2f) * 16f, h * .98f + sin(a + 1f) * 10f))
        drawCircle(Color.White.copy(alpha = .11f), h * .20f, Offset(w * .60f + cos(a + 4f) * 22f, h * .88f + sin(a + 3f) * 8f))
        // líneas de velocidad
        val barH = 7.dp.toPx()
        for (i in 0..2) {
            val len = (110 + i * 46).dp.toPx()
            val x = (((p + i * 0.33f) % 1f) * (w + len * 2f)) - len
            val y = h * (0.22f + i * 0.20f)
            drawRoundRect(Color.White.copy(alpha = .20f), Offset(x, y), Size(len, barH), CornerRadius(barH))
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
