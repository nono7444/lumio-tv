package com.nuvio.tv.ui.screens.detail

// Lumio : bouton « Demander » de la fiche, relié au serveur Lumio (demandes façon Seerr).

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import com.nuvio.tv.lumio.LumioRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.lumio.LumioClient
import com.nuvio.tv.lumio.LumioFolder
import com.nuvio.tv.lumio.LumioMe
import com.nuvio.tv.lumio.LumioMedia
import com.nuvio.tv.lumio.MediaStatus
import com.nuvio.tv.lumio.RequestStatus
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun statusLabel(status: Int): Pair<String, Color>? = when (status) {
    MediaStatus.AVAILABLE -> "Disponible" to Color(0xFF30D158)
    MediaStatus.PARTIALLY_AVAILABLE -> "Partiellement disponible" to Color(0xFF30D158)
    MediaStatus.PROCESSING -> "En cours" to Color(0xFF3D8BFF)
    MediaStatus.PENDING -> "En attente" to Color(0xFFFF9F0A)
    else -> null
}

private fun Meta.lumioType(): String =
    if (apiType.equals("series", true) || apiType.equals("tv", true)) "tv" else "movie"

private fun Meta.lumioImdb(): String? =
    imdbId?.takeIf { it.startsWith("tt") } ?: id.takeIf { it.startsWith("tt") }?.substringBefore(':')

private fun Meta.lumioTmdb(): Int? =
    id.removePrefix("tmdb:").takeIf { id.startsWith("tmdb:") }?.substringBefore(':')?.toIntOrNull()

private fun Meta.lumioSeasons(): List<Int> =
    videos.mapNotNull { it.season }.filter { it > 0 }.distinct().sorted()

/** État Lumio d'une fiche (statut sur le serveur, demandes, téléchargement), rafraîchi en direct. */
class LumioMediaState internal constructor(
    val meta: Meta,
    val client: LumioClient,
    val media: LumioMedia?,
    val reload: () -> Unit
) {
    val type: String get() = meta.lumioType()

    /** Lisible depuis Jellyfin (film disponible, ou au moins une saison). */
    val playable: Boolean
        get() = media?.let { it.status == MediaStatus.AVAILABLE || it.status == MediaStatus.PARTIALLY_AVAILABLE } == true

    /** Demande validée en cours (recherche, téléchargement, import…). */
    val active: LumioRequest? get() = media?.requests?.firstOrNull { it.status == RequestStatus.APPROVED }
    val pending: LumioRequest? get() = media?.requests?.firstOrNull { it.status == RequestStatus.PENDING }

    val canRequest: Boolean
        get() = media?.let { m ->
            if (type == "tv") m.seasons.any { it.status == MediaStatus.UNKNOWN } || (m.seasons.isEmpty() && m.status == MediaStatus.UNKNOWN)
            else m.status == MediaStatus.UNKNOWN
        } == true
}

@Composable
fun rememberLumioMedia(meta: Meta): LumioMediaState {
    val context = LocalContext.current
    val client = remember { LumioClient.get(context) }
    var media by remember(meta.id) { mutableStateOf<LumioMedia?>(null) }
    var reload by remember(meta.id) { mutableStateOf(0) }
    val type = meta.lumioType()

    LaunchedEffect(meta.id, reload) {
        runCatching {
            client.media(type, meta.lumioTmdb(), meta.lumioImdb(), if (type == "tv") meta.lumioSeasons() else emptyList())
        }.getOrNull()?.let { media = it }
    }
    // Suivi en direct pendant le téléchargement (toutes les 5 s), jusqu'à l'arrivée dans Jellyfin
    val activeId = media?.requests?.firstOrNull { it.status == RequestStatus.APPROVED }?.id
    LaunchedEffect(meta.id, activeId) {
        if (activeId == null) return@LaunchedEffect
        while (true) {
            delay(5_000)
            reload++
        }
    }
    return LumioMediaState(meta, client, media) { reload++ }
}

private val Blue = Color(0xFF3D8BFF)
private val Green = Color(0xFF30D158)
private val Orange = Color(0xFFFF9F0A)

/**
 * Bouton principal quand le titre n'est pas (encore) sur le serveur : il remplace « Lire ».
 * Demander → barre de téléchargement en direct → (le bouton Lire revient tout seul quand c'est prêt).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LumioPrimaryButton(
    state: LumioMediaState,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {}
) {
    var dialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val active = state.active
    val pending = state.pending
    val d = active?.download

    // Texte, couleur et remplissage de la barre
    val (text, tint, fill) = when {
        active != null && d?.state == "downloading" ->
            Triple("Téléchargement ${d.progress?.let { "$it %" } ?: "…"}" + (if (d.count > 1) " · ${d.count} épisodes" else ""), Blue, (d.progress ?: 0) / 100f)
        active != null && d?.state == "importing" -> Triple("Ajout au serveur…", Green, 1f)
        active != null && d?.state == "warning" -> Triple("Bloqué : à vérifier", Orange, 1f)
        active != null -> Triple("Recherche d'une source…", Blue, 0.06f)
        pending != null -> Triple("En attente de validation", Orange, 0f)
        state.canRequest -> Triple("Demander", Color.White, 0f)
        else -> Triple("Pas encore disponible", Color.White, 0f)
    }
    val isRequest = active == null && pending == null && state.canRequest
    val shape = RoundedCornerShape(50)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = { if (isRequest) dialog = true else state.reload() },
            shape = ButtonDefaults.shape(shape),
            modifier = Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .widthIn(min = 260.dp)
                .clip(shape)
                .drawBehind {
                    if (!isRequest) {
                        drawRect(Color(0x33FFFFFF))
                        if (fill > 0f) drawRect(tint.copy(alpha = 0.85f), size = Size(size.width * fill.coerceIn(0f, 1f), size.height))
                    }
                },
            colors = if (isRequest) {
                ButtonDefaults.colors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black
                )
            } else {
                ButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    focusedContainerColor = Color(0x22FFFFFF),
                    focusedContentColor = Color.White
                )
            }
        ) {
            Icon(
                imageVector = when {
                    isRequest -> Icons.Default.Send
                    d?.state == "warning" -> Icons.Default.Warning
                    pending != null -> Icons.Default.HourglassTop
                    else -> Icons.Default.Download
                },
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(text, fontWeight = FontWeight.Bold)
        }
        when {
            d?.state == "warning" -> Text(
                "${if (state.type == "tv") "Sonarr" else "Radarr"} : ${d.message ?: "intervention manuelle nécessaire"}",
                color = Orange,
                style = MaterialTheme.typography.labelMedium
            )
            active != null && d == null || d?.state == "searching" -> Text(
                "${if (state.type == "tv") "Sonarr" else "Radarr"} cherche une version à télécharger",
                color = NuvioTheme.extendedColors.textSecondary,
                style = MaterialTheme.typography.labelMedium
            )
            pending != null -> Text(
                "Demandé par ${if (pending.mine) "toi" else pending.requestedBy} · l'admin doit valider",
                color = NuvioTheme.extendedColors.textSecondary,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }

    val m = state.media
    if (dialog && m != null) {
        LumioRequestDialog(
            meta = state.meta,
            media = m,
            client = state.client,
            onDismiss = { dialog = false },
            onDone = {
                dialog = false
                scope.launch {
                    delay(400)
                    state.reload()
                }
            }
        )
    }
}

/**
 * Bouton secondaire, à côté de « Lire » : pour une série déjà en partie là, demander les autres saisons
 * (ou suivre leur téléchargement).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LumioRequestButton(state: LumioMediaState, onFocused: () -> Unit = {}) {
    if (state.media == null || !state.playable) return
    if (state.type == "tv" && (state.canRequest || state.active != null || state.pending != null)) {
        LumioPrimaryButton(state = state, onFocused = onFocused)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LumioRequestDialog(
    meta: Meta,
    media: LumioMedia,
    client: LumioClient,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    val type = meta.lumioType()
    val scope = rememberCoroutineScope()
    val seasons = remember(media) {
        val fromServer = media.seasons.map { it.number to it.status }
        if (fromServer.isNotEmpty()) fromServer else meta.lumioSeasons().map { it to MediaStatus.UNKNOWN }
    }
    val requestable = seasons.filter { it.second == MediaStatus.UNKNOWN }.map { it.first }
    var selected by remember(media) { mutableStateOf(requestable.toSet()) }
    var me by remember { mutableStateOf<LumioMe?>(null) }
    var folders by remember { mutableStateOf<List<LumioFolder>>(emptyList()) }
    var folder by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        me = runCatching { client.me() }.getOrNull()
        runCatching { client.folders(type) }.getOrNull()?.let { (list, def) ->
            folders = list
            folder = suggestFolder(list, meta, def)
        }
        runCatching { firstFocus.requestFocus() }
    }

    NuvioDialog(
        onDismiss = onDismiss,
        title = if (type == "tv") "Demander la série" else "Demander le film",
        subtitle = meta.name,
        width = 560.dp
    ) {
        if (type == "tv" && seasons.isNotEmpty()) {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    val all = requestable.isNotEmpty() && requestable.all { it in selected }
                    ToggleRow(
                        text = "Toutes les saisons",
                        on = all,
                        enabled = requestable.isNotEmpty(),
                        modifier = Modifier.focusRequester(firstFocus)
                    ) { selected = if (all) emptySet() else requestable.toSet() }
                }
                items(seasons) { (n, st) ->
                    val can = st == MediaStatus.UNKNOWN
                    ToggleRow(
                        text = "Saison $n" + (statusLabel(st)?.let { " · ${it.first}" } ?: ""),
                        on = if (can) n in selected else true,
                        enabled = can
                    ) { selected = if (n in selected) selected - n else selected + n }
                }
            }
        }

        if (folders.size > 1) {
            Text("Dossier", style = MaterialTheme.typography.labelLarge, color = NuvioTheme.extendedColors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                folders.forEach { f ->
                    val on = f.path == folder
                    Button(
                        onClick = { folder = f.path },
                        colors = ButtonDefaults.colors(
                            containerColor = if (on) Color.White else Color(0x22FFFFFF),
                            contentColor = if (on) Color.Black else Color.White,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black
                        ),
                        modifier = if (type == "movie" && f == folders.first()) Modifier.focusRequester(firstFocus) else Modifier
                    ) { Text(f.path.trimEnd('/').substringAfterLast('/').replaceFirstChar { it.uppercase() }) }
                }
            }
        }

        me?.let {
            if (!it.autoApprove) Text("Ta demande devra être validée par l'admin.", color = Color(0xFFFF9F0A))
            if (type == "movie" && it.quotaLimit > 0 && it.quotaRemaining != null)
                Text("Il te reste ${it.quotaRemaining} film(s) sur ${it.quotaLimit} tous les ${it.quotaDays} jours.", color = NuvioTheme.extendedColors.textSecondary)
        }
        message?.let { Text(it, color = Color(0xFFFF453A)) }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    message = null
                    scope.launch {
                        try {
                            client.request(
                                type = type,
                                tmdbId = media.tmdbId ?: meta.lumioTmdb(),
                                imdbId = meta.lumioImdb(),
                                tvdbId = media.tvdbId,
                                title = meta.name,
                                year = meta.releaseInfo?.take(4),
                                poster = meta.poster,
                                backdrop = meta.background,
                                seasons = if (type == "tv") selected.sorted() else emptyList(),
                                folder = folder
                            )
                            onDone()
                        } catch (e: Exception) {
                            message = e.message ?: "Demande impossible"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && (type == "movie" || selected.isNotEmpty()),
                modifier = if (type == "movie" && folders.size <= 1) Modifier.focusRequester(firstFocus) else Modifier
            ) {
                Text(
                    if (busy) "Envoi…"
                    else if (type == "tv" && selected.isNotEmpty()) "Demander (${selected.size})"
                    else "Demander"
                )
            }
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.colors(containerColor = Color(0x22FFFFFF), contentColor = Color.White)
            ) { Text("Annuler") }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ToggleRow(text: String, on: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.colors(
            containerColor = Color(0x14FFFFFF),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            disabledContainerColor = Color(0x0AFFFFFF),
            disabledContentColor = Color(0x99FFFFFF)
        )
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = if (on) Color(0xFF3D8BFF) else Color.Transparent,
            modifier = Modifier.padding(end = 10.dp)
        )
        Text(text)
        Spacer(Modifier.width(4.dp))
    }
}

/** Dossier suggéré : Anime pour l'animation japonaise, Kids pour l'animation / famille. */
private fun suggestFolder(folders: List<LumioFolder>, meta: Meta, default: String?): String? {
    val last = { p: String -> p.trimEnd('/').substringAfterLast('/') }
    val genres = meta.genres.joinToString(" ").lowercase()
    val animation = "anim" in genres
    val japanese = meta.language?.lowercase()?.let { "ja" in it || "japan" in it } == true || meta.country?.lowercase()?.contains("japan") == true
    if (animation && japanese) folders.firstOrNull { Regex("anim[eé]", RegexOption.IGNORE_CASE).containsMatchIn(last(it.path)) }?.let { return it.path }
    if (animation || "famil" in genres || "enfant" in genres || "kids" in genres)
        folders.firstOrNull { Regex("kid|enfant|jeunesse|famil", RegexOption.IGNORE_CASE).containsMatchIn(last(it.path)) }?.let { return it.path }
    return default ?: folders.firstOrNull()?.path
}
