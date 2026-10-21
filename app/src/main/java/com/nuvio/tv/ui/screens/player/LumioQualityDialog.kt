package com.nuvio.tv.ui.screens.player

// Lumio : choix de la qualité dans le lecteur (à la place de « Sources ») — Originale, 1080p, 720p.
// 1080p / 720p : le serveur Jellyfin convertit la vidéo à ce débit (utile hors de la maison ou si ça saccade).

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nuvio.tv.ui.components.NuvioDialog

/** Qualité choisie pendant la session (gardée d'une vidéo à l'autre). */
object LumioQuality {
    @Volatile
    var current: Long? = null
}

private val OPTIONS = listOf<Pair<String, Long?>>(
    "Originale (meilleure qualité)" to null,
    "1080p · 10 Mb/s" to 10_000_000L,
    "720p · 4 Mb/s" to 4_000_000L,
    "480p · 1,5 Mb/s" to 1_500_000L
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LumioQualityDialog(onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    NuvioDialog(onDismiss = onDismiss, title = "Qualité", subtitle = "La vidéo reprend au même endroit", width = 460.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OPTIONS.forEachIndexed { i, (label, bitrate) ->
                val on = LumioQuality.current == bitrate
                Button(
                    onClick = {
                        LumioQuality.current = bitrate
                        onPick(bitrate)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (on || (i == 0 && OPTIONS.none { it.second == LumioQuality.current })) Modifier.focusRequester(first) else Modifier),
                    colors = ButtonDefaults.colors(
                        containerColor = if (on) Color(0x33FFFFFF) else Color(0x14FFFFFF),
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    )
                ) { Text((if (on) "✓  " else "") + label) }
            }
        }
    }
}
