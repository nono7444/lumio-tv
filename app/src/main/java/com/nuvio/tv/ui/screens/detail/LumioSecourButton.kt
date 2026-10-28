package com.nuvio.tv.ui.screens.detail

// Lumio : bouton rond « DL » de la fiche (admin) — téléchargement direct par Lumio Secour, comme sur le téléphone.
// Lumio Secour (serveur Lumio) trouve le meilleur fichier, le convertit avec AllDebrid et le range sur le disque ;
// la progression apparaît dans « Demandes ». Série : une saison ou toutes.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nuvio.tv.lumio.LumioMode
import com.nuvio.tv.ui.components.NuvioDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SecourGreen = Color(0xFF30D158)

/** Admin seulement (vérifié auprès du serveur Lumio, une fois par fiche). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LumioSecourButton(state: LumioMediaState, onFocused: () -> Unit = {}) {
    if (!LumioMode.ENABLED) return
    val media = state.media ?: return
    // « DL » seulement si le titre n'est pas (entièrement) disponible sur le serveur
    if (media.status == com.nuvio.tv.lumio.MediaStatus.AVAILABLE) return
    val tmdbId = media.tmdbId ?: return
    var admin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { admin = runCatching { state.client.me().admin }.getOrDefault(false) }
    if (!admin) return
    var dialog by remember { mutableStateOf(false) }

    Button(
        onClick = { dialog = true },
        shape = ButtonDefaults.shape(CircleShape),
        colors = ButtonDefaults.colors(
            containerColor = SecourGreen,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = SecourGreen
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = Modifier
            .size(48.dp)
            .onFocusChanged { if (it.isFocused) onFocused() }
    ) {
        Text("DL", fontSize = 16.sp, fontWeight = FontWeight.Black)
    }

    if (dialog) {
        LumioSecourDialog(state = state, tmdbId = tmdbId, onDismiss = { dialog = false })
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LumioSecourDialog(state: LumioMediaState, tmdbId: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val meta = state.meta
    val seasons = remember(meta.id) { meta.videos.mapNotNull { it.season }.filter { it > 0 }.distinct().sorted() }
    var busy by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    fun grab(chosen: List<Int>?) {
        if (busy) return
        busy = true
        scope.launch {
            val msg = runCatching {
                val already = state.client.secourGrab(
                    type = state.type,
                    tmdbId = tmdbId,
                    title = meta.name,
                    year = meta.releaseInfo?.take(4),
                    poster = meta.poster,
                    seasons = chosen
                )
                if (already) "« ${meta.name} » est déjà en cours de téléchargement."
                else "« ${meta.name} » part en téléchargement avec Lumio Secour. Suivi dans Demandes."
            }.getOrElse { it.message ?: "Lumio Secour : téléchargement impossible" }
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            busy = false
            onDismiss()
            delay(800)
            state.reload()
        }
    }

    // Choix proposés : le film entier, ou pour une série chaque saison et « Toutes »
    val options: List<Pair<String, List<Int>?>> =
        if (state.type == "tv" && seasons.isNotEmpty()) {
            listOf("Toutes les saisons (${seasons.size})" to seasons) + seasons.map { "Saison $it" to listOf(it) }
        } else {
            listOf("Télécharger le meilleur fichier" to null)
        }

    NuvioDialog(
        onDismiss = onDismiss,
        title = "Lumio Secour",
        subtitle = "${meta.name} · meilleur fichier trouvé (Movix), téléchargé sur le disque",
        width = 560.dp
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options.withIndex().toList()) { (i, opt) ->
                Button(
                    onClick = { grab(opt.second) },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                ) {
                    Text(if (busy && i == 0) "Envoi…" else opt.first)
                }
            }
        }
    }
}
