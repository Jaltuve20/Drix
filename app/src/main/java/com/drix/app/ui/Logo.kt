package com.drix.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** La «D» del logo: letra D con las líneas de velocidad a la izquierda. Se dibuja con vectores (sin imágenes). */
@Composable
fun DGlyph(modifier: Modifier = Modifier, color: Color = Ink) {
    Canvas(modifier.aspectRatio(0.98f)) {
        val u = size.height / 100f
        val body = Path().apply {
            fillType = PathFillType.EvenOdd
            // contorno exterior
            moveTo(30 * u, 6 * u); lineTo(54 * u, 6 * u)
            arcTo(Rect(10 * u, 6 * u, 98 * u, 94 * u), -90f, 180f, false)
            lineTo(30 * u, 94 * u); close()
            // hueco interior
            moveTo(46 * u, 28 * u); lineTo(54 * u, 28 * u)
            arcTo(Rect(32 * u, 28 * u, 76 * u, 72 * u), -90f, 180f, false)
            lineTo(46 * u, 72 * u); close()
        }
        drawPath(body, color)
        fun bar(x: Float, y: Float, w: Float) =
            drawRoundRect(color, Offset(x * u, y * u), Size(w * u, 10 * u), CornerRadius(5 * u))
        bar(6f, 24f, 22f); bar(0f, 45f, 28f); bar(6f, 66f, 22f)
    }
}

/** Cuadro amarillo con la D (icono de la app). */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier) = Box(
    modifier.size(size).background(Yellow, RoundedCornerShape(size * 0.24f)), Alignment.Center
) { DGlyph(Modifier.height(size * 0.56f)) }

/** Marca «Drix» donde la D es la D del logo: [D-logo]rix. */
@Composable
fun DrixWordmark(height: Dp, color: Color = Ink) = Row(verticalAlignment = Alignment.CenterVertically) {
    DGlyph(Modifier.height(height), color)
    Text(
        "rix", color = color, fontSize = (height.value * 1.35f).sp, fontWeight = FontWeight.Black,
        letterSpacing = (-0.5).sp, modifier = Modifier.padding(start = (height.value * 0.05f).dp)
    )
}

@Composable
fun Brand() = DrixWordmark(26.dp)
