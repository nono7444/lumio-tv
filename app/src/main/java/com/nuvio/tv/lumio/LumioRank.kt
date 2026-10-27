package com.nuvio.tv.lumio

// Lumio : rang de la famille (récompenses du serveur Lumio), avec son écusson — le même que sur le téléphone.
// 10 rangs : bois, bronze, cuivre, argent, acier, puis écussons à cadre doré (fond bleu, or, violet, pourpre, orange).
// Pastille en haut à droite de l'accueil (rien à sélectionner), et écran « Mon rang » (menu) avec le classement.

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class RankTier(
    val light: Color,
    val base: Color,
    val dark: Color,
    val inner: Pair<Color, Color>?,
    val star: Pair<Color, Color>,
    val starEdge: Color
)

private val GOLD_LIGHT = Color(0xFFFFE58A)
private val GOLD_BASE = Color(0xFFF4B52C)
private val GOLD_DARK = Color(0xFFA8700F)
private val GOLD_STAR = Color(0xFFFFE27A) to Color(0xFFE89B12)

private fun gold(inner: Pair<Color, Color>, star: Pair<Color, Color> = GOLD_STAR, edge: Color = Color(0xFF8A5A06)) =
    RankTier(GOLD_LIGHT, GOLD_BASE, GOLD_DARK, inner, star, edge)

private val TIERS = listOf(
    RankTier(Color(0xFF8E5A34), Color(0xFF6E4224), Color(0xFF3E2412), null, Color(0xFF7A4A28) to Color(0xFF4E2C16), Color(0xFF2E190B)), // bois
    RankTier(Color(0xFFC77A43), Color(0xFFA65A26), Color(0xFF5E2F12), null, Color(0xFFB4642E) to Color(0xFF7A3C17), Color(0xFF4A230B)), // bronze
    RankTier(Color(0xFFE3A79B), Color(0xFFC27F73), Color(0xFF7E4A43), null, Color(0xFFD08F84) to Color(0xFF9A5F57), Color(0xFF6A3C36)), // cuivre
    RankTier(Color(0xFFC2CAD6), Color(0xFF8E98A8), Color(0xFF4D5563), null, Color(0xFFA3ADBC) to Color(0xFF6B7484), Color(0xFF3E4552)), // argent
    RankTier(Color(0xFFE6EBF2), Color(0xFFB3BBC8), Color(0xFF646C7A), null, Color(0xFFD2D8E1) to Color(0xFF8D96A5), Color(0xFF555D6A)), // acier
    gold(Color(0xFF5BA8FF) to Color(0xFF1F5FC4)), // bleu
    gold(Color(0xFFF5A623) to Color(0xFFC26E08), Color(0xFFFFF3BF) to Color(0xFFF7C23A), Color(0xFF7A4E05)), // or
    gold(Color(0xFF8C63FF) to Color(0xFF4B25B8)), // violet
    gold(Color(0xFFA24DE0) to Color(0xFF5A1E99)), // pourpre
    gold(Color(0xFFFFA43A) to Color(0xFFE0610F)) // orange
)

/** Écusson (repère 100 × 110) : haut plat aux coins coupés, pointe en bas. */
private fun outerPath(k: Float) = Path().apply {
    moveTo(18 * k, 6 * k); lineTo(82 * k, 6 * k); lineTo(94 * k, 18 * k); lineTo(94 * k, 60 * k)
    cubicTo(94 * k, 78 * k, 74 * k, 94 * k, 50 * k, 106 * k)
    cubicTo(26 * k, 94 * k, 6 * k, 78 * k, 6 * k, 60 * k)
    lineTo(6 * k, 18 * k); close()
}

private fun innerPath(k: Float) = Path().apply {
    moveTo(23 * k, 15 * k); lineTo(77 * k, 15 * k); lineTo(85 * k, 23 * k); lineTo(85 * k, 59 * k)
    cubicTo(85 * k, 73 * k, 69 * k, 86 * k, 50 * k, 96 * k)
    cubicTo(31 * k, 86 * k, 15 * k, 73 * k, 15 * k, 59 * k)
    lineTo(15 * k, 23 * k); close()
}

private fun starPath(k: Float, cx: Float, cy: Float, r: Float, inner: Float) = Path().apply {
    for (i in 0 until 10) {
        val a = -PI / 2 + i * PI / 5
        val rr = if (i % 2 == 0) r else inner
        val x = (cx + rr * cos(a)).toFloat() * k
        val y = (cy + rr * sin(a)).toFloat() * k
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.drawShield(tier: Int) {
    val t = TIERS[tier.coerceIn(0, TIERS.lastIndex)]
    val k = size.width / 100f
    val outer = outerPath(k)
    translate(top = 3 * k) { drawPath(outer, t.dark) }
    drawPath(outer, Brush.verticalGradient(listOf(t.light, t.base)))
    drawPath(outer, t.dark, style = Stroke(width = 2 * k))
    val inner = innerPath(k)
    drawPath(inner, Brush.verticalGradient(listOf(t.inner?.first ?: t.base, t.inner?.second ?: t.dark)))
    drawPath(inner, (if (t.inner != null) GOLD_DARK else t.dark).copy(alpha = 0.6f), style = Stroke(width = 1.5f * k))
    val shine = Path().apply {
        moveTo(24 * k, 17 * k); lineTo(76 * k, 17 * k); lineTo(83 * k, 24 * k); lineTo(83 * k, 34 * k)
        cubicTo(64 * k, 30 * k, 36 * k, 30 * k, 17 * k, 34 * k); lineTo(17 * k, 24 * k); close()
    }
    drawPath(shine, Color.White.copy(alpha = if (t.inner != null) 0.18f else 0.12f))
    val star = starPath(k, 50f, 57f, 25f, 10.5f)
    translate(top = 2.5f * k) { drawPath(star, t.starEdge) }
    drawPath(star, Brush.verticalGradient(listOf(t.star.first, t.star.second), startY = 32 * k, endY = 82 * k))
    drawPath(star, t.starEdge, style = Stroke(width = 1.5f * k))
    drawPath(starPath(k, 50f, 57f, 13f, 5.5f), Color.White.copy(alpha = 0.18f))
}

@Composable
fun LumioRankShield(tier: Int, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = size, height = size * 1.1f)) { drawShield(tier) }
}

/** Membre de la famille dans le classement. */
data class LumioRankMember(
    val id: String,
    val name: String,
    val points: Int,
    val rank: Int,
    val hours: Int,
    val gradeName: String,
    val tier: Int,
    val gradeMin: Int,
    val nextName: String?,
    val nextMin: Int?,
    val nextTier: Int?
)

private fun parseBoard(json: org.json.JSONObject): Pair<List<LumioRankMember>, String> {
    val arr = json.optJSONArray("users")
    val list = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
        val u = arr!!.optJSONObject(i) ?: return@mapNotNull null
        val g = u.optJSONObject("grade")
        val n = u.optJSONObject("next")
        LumioRankMember(
            id = u.optString("id"),
            name = u.optString("name"),
            points = u.optInt("points"),
            rank = u.optInt("rank"),
            hours = u.optInt("hours"),
            gradeName = g?.optString("name").orEmpty(),
            tier = g?.optInt("tier", 0) ?: 0,
            gradeMin = g?.optInt("min", 0) ?: 0,
            nextName = n?.optString("name"),
            nextMin = n?.optInt("min"),
            nextTier = n?.optInt("tier")
        )
    }
    return list to json.optString("me")
}

/** Classement (mis en cache 10 minutes, le serveur calcule déjà les points). */
object LumioRankStore {
    private var cached: Pair<List<LumioRankMember>, String>? = null
    private var at = 0L
    suspend fun load(client: LumioClient, force: Boolean = false): Pair<List<LumioRankMember>, String>? {
        if (!force && cached != null && System.currentTimeMillis() - at < 10 * 60_000) return cached
        return runCatching { parseBoard(client.rewards()) }.getOrNull()?.also {
            cached = it
            at = System.currentTimeMillis()
        } ?: cached
    }
}

/** Pastille du rang en haut à droite de l'accueil (rien à sélectionner : le menu « Mon rang » ouvre les stats). */
@Composable
fun LumioRankBadge(visible: Boolean, modifier: Modifier = Modifier) {
    if (!LumioMode.ENABLED || !visible) return
    val context = LocalContext.current
    var me by remember { mutableStateOf<LumioRankMember?>(null) }
    LaunchedEffect(Unit) {
        val board = LumioRankStore.load(LumioClient.get(context)) ?: return@LaunchedEffect
        me = board.first.firstOrNull { it.id == board.second }
    }
    val m = me ?: return
    Box(modifier = modifier.focusProperties { canFocus = false }, contentAlignment = Alignment.TopEnd) {
        Row(
            modifier = Modifier
                .padding(top = 18.dp, end = 28.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x99000000))
                .padding(start = 8.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LumioRankShield(m.tier, 22.dp)
            Text(m.gradeName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Écran « Mon rang » : écusson, points, progression vers le rang suivant, classement de la famille. */
@Composable
fun LumioRewardsScreen() {
    val context = LocalContext.current
    var board by remember { mutableStateOf<Pair<List<LumioRankMember>, String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        board = LumioRankStore.load(LumioClient.get(context), force = true)
        if (board == null) error = "Classement indisponible (serveur Lumio injoignable ?)"
    }
    val (users, meId) = board ?: (emptyList<LumioRankMember>() to "")
    val me = users.firstOrNull { it.id == meId }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("Mon rang", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black) }
        error?.let { e -> item { Text(e, color = Color(0xFFFF453A), fontSize = 16.sp) } }
        if (me != null) item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                LumioRankShield(me.tier, 96.dp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(me.gradeName, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text(
                        "${me.points} points · ${if (me.rank == 1) "1er" else "${me.rank}e"} de la famille · ${me.hours} h regardées",
                        color = Color(0xFFB0B0B5), fontSize = 16.sp
                    )
                    if (me.nextMin != null && me.nextName != null) {
                        val span = (me.nextMin - me.gradeMin).coerceAtLeast(1)
                        val pct = ((me.points - me.gradeMin).toFloat() / span).coerceIn(0.03f, 1f)
                        Box(Modifier.width(420.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF))) {
                            Box(Modifier.fillMaxWidth(pct).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFFC93C)))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Encore ${me.nextMin - me.points} points pour devenir ${me.nextName}", color = Color(0xFFB0B0B5), fontSize = 14.sp)
                            me.nextTier?.let { LumioRankShield(it, 18.dp) }
                        }
                    } else {
                        Text("Rang maximum atteint 🎉", color = Color(0xFFB0B0B5), fontSize = 14.sp)
                    }
                }
            }
        }
        if (users.isNotEmpty()) item {
            Spacer(Modifier.height(10.dp))
            Text("Classement de la famille", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        items(users, key = { it.id }) { u ->
            // Lignes sélectionnables pour pouvoir faire défiler la liste à la télécommande
            androidx.tv.material3.Surface(
                onClick = {},
                shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                    containerColor = if (u.id == meId) Color(0x22FFC93C) else Color(0x14FFFFFF),
                    focusedContainerColor = Color(0x33FFFFFF)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(if (u.rank <= 3) listOf("🥇", "🥈", "🥉")[u.rank - 1] else "${u.rank}.", color = Color.White, fontSize = 18.sp, modifier = Modifier.width(34.dp))
                    LumioRankShield(u.tier, 30.dp)
                    Column(Modifier.weight(1f)) {
                        Text(u.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("${u.gradeName} · ${u.hours} h", color = Color(0xFFB0B0B5), fontSize = 13.sp)
                    }
                    Text("${u.points}", color = Color(0xFFFFC93C), fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
