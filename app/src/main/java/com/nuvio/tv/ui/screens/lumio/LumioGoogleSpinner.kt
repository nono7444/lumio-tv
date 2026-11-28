package com.nuvio.tv.ui.screens.lumio

// Lumio : spinner façon Google (Material) — un arc qui tourne, s'allonge puis se raccourcit
// en passant bleu → rouge → jaune → vert. Utilisé pendant la mise en réserve d'une chaîne Live TV.

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LumioGoogleColors = listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853))

@Composable
fun LumioGoogleSpinner(size: Dp = 72.dp, stroke: Dp = 6.dp) {
    val t = rememberInfiniteTransition(label = "spinner")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1568, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    val arc by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1333, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)), RepeatMode.Restart), label = "arc")
    val hue by t.animateFloat(0f, 4f, infiniteRepeatable(tween(1333 * 4, easing = LinearEasing), RepeatMode.Restart), label = "hue")
    Canvas(modifier = Modifier.size(size)) {
        val w = stroke.toPx()
        // Première moitié : la tête avance (l'arc s'allonge) ; deuxième : la queue rattrape (l'arc raccourcit)
        val sweep = if (arc < 0.5f) 10f + 260f * (arc / 0.5f) else 270f - 260f * ((arc - 0.5f) / 0.5f)
        val tail = if (arc < 0.5f) 0f else 260f * ((arc - 0.5f) / 0.5f)
        val i = hue.toInt() % 4
        val frac = (hue - hue.toInt()).let { if (it < 0.9f) 0f else (it - 0.9f) / 0.1f }
        val color = lerp(LumioGoogleColors[i], LumioGoogleColors[(i + 1) % 4], frac)
        drawArc(
            color = color,
            startAngle = spin + tail + hue * 90f - 90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(w / 2, w / 2),
            size = Size(this.size.width - w, this.size.height - w),
            style = Stroke(width = w, cap = StrokeCap.Round)
        )
    }
}
