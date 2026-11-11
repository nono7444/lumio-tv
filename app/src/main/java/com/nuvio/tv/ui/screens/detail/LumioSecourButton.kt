package com.nuvio.tv.ui.screens.detail

// Lumio : bouton « Movix » de la fiche (admin) — téléchargement direct par Lumio Secour, comme sur le téléphone.
// Film : choix du fichier parmi ceux trouvés (ou le meilleur, automatiquement). Série : une saison ou toutes.
// Le serveur Lumio ouvre le lien avec AllDebrid (ou TorBox) et range le fichier ; suivi dans « Demandes ».

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
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
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nuvio.tv.lumio.LumioLink
import com.nuvio.tv.lumio.LumioMode
import com.nuvio.tv.ui.components.NuvioDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Admin seulement (vérifié auprès du serveur Lumio, une fois par fiche) : bouton « Movix » sur un titre absent.
 * Il ouvre le choix des fichiers (film) ou des saisons (série) et télécharge sans passer par le tracker.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LumioSecourButton(state: LumioMediaState, onFocused: () -> Unit = {}) {
    if (!LumioMode.ENABLED) return
    val media = state.media ?: return
    // « Movix » seulement si le titre n'est pas (entièrement) disponible sur le serveur
    if (media.status == com.nuvio.tv.lumio.MediaStatus.AVAILABLE) return
    val tmdbId = media.tmdbId ?: return
    var admin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { admin = runCatching { state.client.me().admin }.getOrDefault(false) }
    if (!admin) return
    var dialog by remember { mutableStateOf(false) }

    Button(
        onClick = { dialog = true },
        shape = ButtonDefaults.shape(RoundedCornerShape(50)),
        colors = ButtonDefaults.colors(
            containerColor = Color(0x33FFFFFF),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        ),
        modifier = Modifier.onFocusChanged { if (it.isFocused) onFocused() }
    ) {
        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text("Movix", fontWeight = FontWeight.Bold)
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
    val isTv = state.type == "tv"
    val seasons = remember(meta.id) { meta.videos.mapNotNull { it.season }.filter { it > 0 }.distinct().sorted() }
    var busy by remember { mutableStateOf(false) }
    // Film : liste des fichiers trouvés (null = recherche en cours)
    var links by remember { mutableStateOf<List<LumioLink>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!isTv) {
            runCatching { state.client.secourLinks(tmdbId) }
                .onSuccess { links = it }
                .onFailure { error = it.message ?: "Recherche impossible"; links = emptyList() }
        }
        delay(150)
        runCatching { first.requestFocus() }
    }

    fun grab(chosen: List<Int>?, pick: LumioLink? = null) {
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
                    seasons = chosen,
                    pick = pick
                )
                if (already) "« ${meta.name} » est déjà en cours de téléchargement."
                else "« ${meta.name} » part en téléchargement direct. Suivi dans Demandes."
            }.getOrElse { it.message ?: "Téléchargement impossible" }
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            busy = false
            onDismiss()
            delay(800)
            state.reload()
        }
    }

    NuvioDialog(
        onDismiss = onDismiss,
        title = "Movix",
        subtitle = when {
            isTv -> "${meta.name} · choisis les saisons (meilleur fichier de chaque épisode)"
            links == null -> "${meta.name} · recherche des fichiers…"
            links!!.isEmpty() -> "${meta.name} · ${error ?: "aucun fichier trouvé"}"
            else -> "${meta.name} · ${links!!.size} fichier(s) : choisis celui à télécharger"
        },
        width = 720.dp
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isTv) {
                val options: List<Pair<String, List<Int>?>> =
                    if (seasons.isNotEmpty()) listOf("Toutes les saisons (${seasons.size})" to seasons) + seasons.map { "Saison $it" to listOf(it) }
                    else listOf("Télécharger la série" to null)
                items(options.withIndex().toList()) { (i, opt) ->
                    Button(
                        onClick = { grab(opt.second) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                    ) { Text(if (busy && i == 0) "Envoi…" else opt.first) }
                }
            } else {
                item {
                    Button(
                        onClick = { grab(null) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().focusRequester(first)
                    ) { Text(if (busy) "Envoi…" else "Automatique : le meilleur fichier") }
                }
                items(links ?: emptyList()) { l ->
                    Button(onClick = { grab(null, l) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            listOf(l.quality, l.language, l.size, l.hoster, l.source, if (l.cached) "prêt" else "").filter { it.isNotBlank() }.joinToString(" · "),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
