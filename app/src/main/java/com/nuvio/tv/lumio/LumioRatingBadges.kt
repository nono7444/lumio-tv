package com.nuvio.tv.lumio

// Lumio : pastille « ★ 8,5 · Top » en haut à gauche des affiches, avec la moyenne des notes de la famille
// (même système que l'appli du téléphone). Toutes les moyennes sont chargées d'un coup et partagées.

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

data class LumioRatingSummary(val avg: Double, val count: Int)

object LumioRatingBadges {
    private val _summary = MutableStateFlow<Map<String, LumioRatingSummary>>(emptyMap())
    val summary: StateFlow<Map<String, LumioRatingSummary>> = _summary

    @Volatile
    private var loadedAt = 0L

    @Volatile
    private var loading = false

    /** Recharge les moyennes (au plus toutes les 5 minutes, ou tout de suite avec [force]). */
    suspend fun refresh(context: Context, force: Boolean = false) {
        if (loading || (!force && System.currentTimeMillis() - loadedAt < 5 * 60_000)) return
        loading = true
        try {
            val json = LumioClient.get(context).ratingSummary()
            val items = json.optJSONObject("items") ?: JSONObject()
            val out = HashMap<String, LumioRatingSummary>()
            items.keys().forEach { k ->
                items.optJSONObject(k)?.let { out[k] = LumioRatingSummary(it.optDouble("avg"), it.optInt("count")) }
            }
            _summary.value = out
            loadedAt = System.currentTimeMillis()
        } catch (_: Exception) {
        } finally {
            loading = false
        }
    }

    /** Au démarrage de l'appli : chargement puis rafraîchissement toutes les 5 minutes. */
    fun start(context: Context) {
        val app = context.applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            while (true) {
                refresh(app, force = true)
                kotlinx.coroutines.delay(if (_summary.value.isEmpty()) 30_000 else 5 * 60_000L)
            }
        }
    }

    fun forImdb(imdb: String?): LumioRatingSummary? = imdb?.takeIf { it.startsWith("tt") }?.let { _summary.value["imdb:$it"] }
}

private fun verdict(n: Double): Pair<String, Color> = when {
    n >= 7.5 -> "Top" to Color(0xFF34C759)
    n >= 5.0 -> "Moyen" to Color(0xFFFF9F0A)
    else -> "Bouse" to Color(0xFFB5651D)
}

/** Pastille de la note de la famille pour l'affiche (rien si personne n'a noté ce titre). */
@Composable
fun LumioRatingBadge(imdbId: String?, modifier: Modifier = Modifier) {
    if (!LumioMode.ENABLED || imdbId.isNullOrBlank()) return
    val map by LumioRatingBadges.summary.collectAsState()
    val r = map["imdb:$imdbId"] ?: return
    val (label, color) = verdict(r.avg)
    val avg = if (r.avg % 1.0 == 0.0) r.avg.toInt().toString() else String.format(java.util.Locale.FRANCE, "%.1f", r.avg)
    Row(
        modifier = modifier
            .background(Color(0xCC000000), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("★", color = Color(0xFFFFC93C), fontSize = 10.sp)
        Text(avg, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("· $label", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
