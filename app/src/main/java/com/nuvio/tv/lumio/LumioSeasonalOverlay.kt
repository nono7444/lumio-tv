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

private data class Particle(val x: Float, val speed: Float, val size: Float, val phase: Float, val drift: Float, val glyph: String)

@Composable
fun LumioSeasonalOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    if (!LumioMode.ENABLED || !visible) return
    val season = remember { currentDecoSeason() }
    if (season == LumioDecoSeason.NONE) return

    val glyphs = when (season) {
        LumioDecoSeason.HALLOWEEN -> listOf("🦇", "🦇", "🎃", "👻", "🦇", "🍂")
        LumioDecoSeason.CHRISTMAS -> emptyList() // neige dessinée
        LumioDecoSeason.NEW_YEAR -> listOf("✨", "🎉", "✨", "🥂")
        LumioDecoSeason.VALENTINE -> listOf("❤️", "💕", "💖")
        LumioDecoSeason.EASTER -> listOf("🥚", "🐣", "🌷")
        LumioDecoSeason.NONE -> emptyList()
    }
    val count = if (season == LumioDecoSeason.CHRISTMAS) 70 else 14
    val particles = remember(season) {
        val r = Random(season.ordinal * 97 + 7)
        List(count) {
            Particle(
                x = r.nextFloat(),
                speed = 0.35f + r.nextFloat() * 0.65f,
                size = if (season == LumioDecoSeason.CHRISTMAS) 1.5f + r.nextFloat() * 3.5f else 16f + r.nextFloat() * 14f,
                phase = r.nextFloat(),
                drift = 0.01f + r.nextFloat() * 0.03f,
                glyph = if (glyphs.isEmpty()) "" else glyphs[r.nextInt(glyphs.size)]
            )
        }
    }
    // Animations lues seulement au dessin (pas de recomposition à chaque image : fluide sur les petites box)
    val tState = rememberInfiniteTransition(label = "lumioSeason").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (season == LumioDecoSeason.CHRISTMAS) 14_000 else 22_000, easing = LinearEasing), RepeatMode.Restart),
        label = "lumioSeasonT"
    )
    val twinkleState = rememberInfiniteTransition(label = "lumioTwinkle").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "lumioTwinkleA"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        // Lueur de saison sur les bords
        val glow = when (season) {
            LumioDecoSeason.HALLOWEEN -> Color(0x33FF7A00)
            LumioDecoSeason.CHRISTMAS -> Color(0x22C8102E)
            LumioDecoSeason.NEW_YEAR -> Color(0x22FFD54F)
            LumioDecoSeason.VALENTINE -> Color(0x22FF4F81)
            LumioDecoSeason.EASTER -> Color(0x2266BB6A)
            LumioDecoSeason.NONE -> Color.Transparent
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = tState.value
            val twinkle = twinkleState.value
            drawRect(Brush.verticalGradient(listOf(glow, Color.Transparent), endY = size.height * 0.28f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, glow), startY = size.height * 0.8f))

            when (season) {
                LumioDecoSeason.CHRISTMAS -> {
                    // Neige
                    particles.forEach { p ->
                        val y = ((p.phase + t * p.speed) % 1f) * (h + 20f) - 10f
                        val x = (p.x + sin((t * 2 * PI * 2 + p.phase * 6).toFloat()) * p.drift) * w
                        drawCircle(Color.White.copy(alpha = 0.55f), radius = p.size, center = Offset(x, y))
                    }
                    // Guirlande lumineuse en haut
                    val bulbs = 26
                    val colors = listOf(Color(0xFFFF3B30), Color(0xFF34C759), Color(0xFFFFCC00), Color(0xFF0A84FF))
                    for (i in 0 until bulbs) {
                        val x = w * (i + 0.5f) / bulbs
                        val y = 10f + 10f * sin((i / bulbs.toFloat()) * PI.toFloat() * 4)
                        val on = (i + (t * 12).toInt()) % 2 == 0
                        drawCircle(colors[i % colors.size].copy(alpha = if (on) 0.9f * twinkle else 0.35f), radius = 5f, center = Offset(x, y))
                    }
                }
                LumioDecoSeason.HALLOWEEN -> {
                    // Toiles d'araignée dans les coins du haut
                    val web = Color.White.copy(alpha = 0.18f)
                    for (corner in listOf(Offset(0f, 0f), Offset(w, 0f))) {
                        for (ring in 1..4) {
                            drawCircle(web, radius = ring * 26f, center = corner, style = Stroke(width = 1.2f))
                        }
                        for (k in 0..5) {
                            val a = (k / 5f) * (PI / 2).toFloat()
                            val dx = if (corner.x == 0f) kotlin.math.cos(a) else -kotlin.math.cos(a)
                            drawLine(web, corner, Offset(corner.x + dx * 110f, corner.y + sin(a) * 110f), strokeWidth = 1.2f)
                        }
                    }
                }
                else -> Unit
            }
        }

        // Petits personnages (chauves-souris, citrouilles, cœurs…) qui traversent lentement le haut de l'écran
        if (season != LumioDecoSeason.CHRISTMAS) {
            particles.forEach { p ->
                Box(
                    modifier = Modifier
                        .offset {
                            val progress = (p.phase + tState.value * p.speed) % 1f
                            val x = progress * (w + 120f) - 60f
                            val y = h * (0.04f + p.x * 0.22f) + sin((progress * 2 * PI * 3).toFloat()) * 18f
                            androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt())
                        }
                        .alpha(0.55f)
                ) {
                    Text(p.glyph, fontSize = p.size.sp)
                }
            }
        }

        // Clin d'œil dans le coin
        val corner = when (season) {
            LumioDecoSeason.HALLOWEEN -> "🎃"
            LumioDecoSeason.CHRISTMAS -> "🎄"
            LumioDecoSeason.NEW_YEAR -> "🎆"
            LumioDecoSeason.VALENTINE -> "💝"
            LumioDecoSeason.EASTER -> "🐰"
            LumioDecoSeason.NONE -> ""
        }
        Text(corner, fontSize = 34.sp, modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-24).dp, y = (-18).dp).alpha(0.8f))
    }
}
