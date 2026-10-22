package com.nuvio.tv.lumio

// Lumio : décorations de saison par-dessus l'interface (jamais dans le lecteur), discrètes et sans gêner la navigation.
// Halloween (15 oct. → 2 nov.) : chauves-souris, citrouilles, toiles d'araignée et lueur orange.
// Noël (1er → 26 déc.) : neige qui tombe, guirlande lumineuse en haut, lueur rouge et verte.
// Nouvel An (27 déc. → 2 janv.) : confettis dorés. Saint-Valentin (10 → 15 févr.) : cœurs. Pâques (avril) : œufs.

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import java.time.LocalDate
import java.time.MonthDay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class LumioDecoSeason { HALLOWEEN, CHRISTMAS, NEW_YEAR, VALENTINE, EASTER, NONE }

fun currentDecoSeason(today: LocalDate = LocalDate.now()): LumioDecoSeason {
    val d = MonthDay.from(today)
    fun inRange(from: MonthDay, to: MonthDay) = !d.isBefore(from) && !d.isAfter(to)
    return when {
        inRange(MonthDay.of(10, 15), MonthDay.of(11, 2)) -> LumioDecoSeason.HALLOWEEN
        inRange(MonthDay.of(12, 1), MonthDay.of(12, 26)) -> LumioDecoSeason.CHRISTMAS
        inRange(MonthDay.of(12, 27), MonthDay.of(12, 31)) || inRange(MonthDay.of(1, 1), MonthDay.of(1, 2)) -> LumioDecoSeason.NEW_YEAR
        inRange(MonthDay.of(2, 10), MonthDay.of(2, 15)) -> LumioDecoSeason.VALENTINE
        today.monthValue == 4 && today.dayOfMonth <= 21 -> LumioDecoSeason.EASTER
        else -> LumioDecoSeason.NONE
    }
}


@Composable
fun LumioSeasonalOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    if (!LumioMode.ENABLED || !visible) return
    val season = remember { currentDecoSeason() }
    if (season == LumioDecoSeason.NONE) return

    // Décor FIXE (aucune animation) : dessiné une seule fois, aucun coût pendant la navigation
    val glyphs = when (season) {
        LumioDecoSeason.HALLOWEEN -> listOf("🦇", "🎃", "🦇", "👻", "🦇")
        LumioDecoSeason.CHRISTMAS -> listOf("❄️", "⭐", "❄️", "🎁", "❄️")
        LumioDecoSeason.NEW_YEAR -> listOf("✨", "🎉", "✨", "🥂")
        LumioDecoSeason.VALENTINE -> listOf("❤️", "💕", "💖")
        LumioDecoSeason.EASTER -> listOf("🥚", "🐣", "🌷")
        LumioDecoSeason.NONE -> emptyList()
    }
    val glow = when (season) {
        LumioDecoSeason.HALLOWEEN -> Color(0x33FF7A00)
        LumioDecoSeason.CHRISTMAS -> Color(0x22C8102E)
        LumioDecoSeason.NEW_YEAR -> Color(0x22FFD54F)
        LumioDecoSeason.VALENTINE -> Color(0x22FF4F81)
        LumioDecoSeason.EASTER -> Color(0x2266BB6A)
        LumioDecoSeason.NONE -> Color.Transparent
    }
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            drawRect(Brush.verticalGradient(listOf(glow, Color.Transparent), endY = size.height * 0.22f))
            when (season) {
                LumioDecoSeason.HALLOWEEN -> {
                    // Toiles d'araignée dans les coins du haut
                    val web = Color.White.copy(alpha = 0.16f)
                    for (corner in listOf(Offset(0f, 0f), Offset(w, 0f))) {
                        for (ring in 1..4) drawCircle(web, radius = ring * 24f, center = corner, style = Stroke(width = 1.2f))
                        for (k in 0..5) {
                            val a = (k / 5f) * (PI / 2).toFloat()
                            val dx = if (corner.x == 0f) kotlin.math.cos(a) else -kotlin.math.cos(a)
                            drawLine(web, corner, Offset(corner.x + dx * 100f, corner.y + sin(a) * 100f), strokeWidth = 1.2f)
                        }
                    }
                }
                LumioDecoSeason.CHRISTMAS -> {
                    // Guirlande lumineuse fixe en haut
                    val bulbs = 26
                    val colors = listOf(Color(0xFFFF3B30), Color(0xFF34C759), Color(0xFFFFCC00), Color(0xFF0A84FF))
                    for (i in 0 until bulbs) {
                        val x = w * (i + 0.5f) / bulbs
                        val y = 10f + 10f * sin((i / bulbs.toFloat()) * PI.toFloat() * 4)
                        drawCircle(colors[i % colors.size].copy(alpha = 0.8f), radius = 5f, center = Offset(x, y))
                    }
                }
                else -> Unit
            }
        }
        // Quelques petits motifs en haut de l'écran, à des places fixes
        val spots = listOf(0.18f to 10.dp, 0.36f to 22.dp, 0.58f to 6.dp, 0.74f to 26.dp, 0.88f to 12.dp)
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            glyphs.forEachIndexed { i, g ->
                val (fx, y) = spots[i % spots.size]
                Text(g, fontSize = 18.sp, modifier = Modifier.offset(x = maxWidth * fx, y = y).alpha(0.5f))
            }
        }
        val corner = when (season) {
            LumioDecoSeason.HALLOWEEN -> "🎃"
            LumioDecoSeason.CHRISTMAS -> "🎄"
            LumioDecoSeason.NEW_YEAR -> "🎆"
            LumioDecoSeason.VALENTINE -> "💝"
            LumioDecoSeason.EASTER -> "🐰"
            LumioDecoSeason.NONE -> ""
        }
        Text(corner, fontSize = 30.sp, modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-24).dp, y = (-18).dp).alpha(0.75f))
    }
}
