@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.lumio

// Lumio : onglet Live TV, comme sur l'iPhone. Les sources (listes IPTV M3U / Xtream) sont celles ajoutées
// dans Lumio sur le téléphone : il les envoie au serveur Lumio (sur ton compte), la TV les lit de là.
// Chaînes groupées (France d'abord), favoris, lecture dans le lecteur de Lumio TV.

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.lumio.LumioClient
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class LiveChannel(val key: String, val name: String, val logo: String?, val group: String, val url: String, val headers: Map<String, String>)
private data class LiveSourceInfo(val id: String, val name: String, val kind: String, val url: String)

private const val FAVORITES = "★ Favoris"
private const val ALL = "Toutes"

private fun attr(line: String, name: String): String? =
    Regex("""$name="([^"]*)"""", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

/** Liste M3U étendue → chaînes en direct (les films / séries à la demande des listes Xtream sont ignorés). */
private fun parseM3u(text: String, sourceId: String, max: Int = 30_000): List<LiveChannel> {
    val out = ArrayList<LiveChannel>()
    var name: String? = null
    var logo: String? = null
    var group: String? = null
    var headers = HashMap<String, String>()
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        when {
            line.startsWith("#EXTINF", ignoreCase = true) -> {
                val comma = line.lastIndexOf(',')
                name = (if (comma >= 0) line.substring(comma + 1) else "").trim().ifBlank { attr(line, "tvg-name") ?: "Chaîne" }
                logo = attr(line, "tvg-logo")
                group = attr(line, "group-title")
                headers = HashMap()
            }
            line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                val kv = line.substring(11)
                val k = kv.substringBefore('=')
                val v = kv.substringAfter('=', "")
                if (k.contains("user-agent", true)) headers["User-Agent"] = v
                if (k.contains("referrer", true) || k.contains("referer", true)) headers["Referer"] = v
            }
            line.startsWith("#EXTGRP:", ignoreCase = true) -> if (group == null) group = line.substring(8).trim()
            !line.startsWith("#") -> {
                val n = name
                if (n != null && !Regex("""/(movie|series)/""", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                    out += LiveChannel("$sourceId:${out.size}:$n", n, logo, group ?: "Autres", line, headers)
                    if (out.size >= max) return out
                }
                name = null
            }
        }
    }
    return out
}

private fun isFrench(group: String) = Regex("""\b(fr|france|français|french)\b""", RegexOption.IGNORE_CASE).containsMatchIn(group)

/** Liste téléchargée gardée 6 h sur la TV (les grosses listes IPTV sont lentes à récupérer). */
private suspend fun loadChannels(context: Context, src: LiveSourceInfo, force: Boolean): List<LiveChannel> = withContext(Dispatchers.IO) {
    val cache = File(context.cacheDir, "live-${src.id.ifBlank { src.url.hashCode().toString() }}.m3u")
    val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < 6 * 3600_000
    val text = if (fresh && !force) cache.readText() else {
        val conn = URL(src.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "Lumio TV")
        val body = try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
        runCatching { cache.writeText(body) }
        body
    }
    parseM3u(text, src.id)
}

@Composable
fun LumioLiveScreen(onPlay: (LiveChannel) -> Unit) {
    val context = LocalContext.current
    val client = remember { LumioClient.get(context) }
    val prefs = remember { context.getSharedPreferences("lumio_live", Context.MODE_PRIVATE) }
    var sources by remember { mutableStateOf<List<LiveSourceInfo>?>(null) }
    var source by remember { mutableStateOf<LiveSourceInfo?>(null) }
    var channels by remember { mutableStateOf<List<LiveChannel>?>(null) }
    var group by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())!!.toSet()) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        runCatching { client.liveSources() }
            .onSuccess { json ->
                val arr = json.optJSONArray("sources")
                val list = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                    arr!!.optJSONObject(i)?.let { LiveSourceInfo(it.optString("id"), it.optString("name"), it.optString("kind"), it.optString("url")) }
                }.filter { it.kind == "m3u" }
                sources = list
                source = list.firstOrNull { it.id == prefs.getString("source", null) } ?: list.firstOrNull()
            }
            .onFailure { error = it.message ?: "Serveur Lumio injoignable"; sources = emptyList() }
    }
    LaunchedEffect(source?.id, reload) {
        val src = source ?: return@LaunchedEffect
        channels = null
        error = null
        runCatching { loadChannels(context, src, force = reload > 0) }
            .onSuccess { list ->
                channels = list
                val groups = list.map { it.group }.distinct()
                group = when {
                    favorites.any { f -> list.any { it.key == f } } -> FAVORITES
                    else -> groups.firstOrNull(::isFrench) ?: groups.firstOrNull() ?: ALL
                }
            }
            .onFailure { error = "Liste impossible à lire : ${it.message}" }
    }

    fun toggleFavorite(ch: LiveChannel) {
        favorites = if (ch.key in favorites) favorites - ch.key else favorites + ch.key
        prefs.edit().putStringSet("favorites", favorites).apply()
    }

    Column(modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Live TV", style = MaterialTheme.typography.headlineLarge, color = NuvioTheme.colors.TextPrimary)
        val list = sources
        if (list == null) {
            Text("Chargement…", color = NuvioTheme.colors.TextSecondary)
            return@Column
        }
        if (list.isEmpty()) {
            Text(
                error ?: "Aucune source Live TV. Sur ton téléphone : Lumio → Live TV → Sources, ajoute ta liste IPTV ; elle apparaîtra ici toute seule.",
                color = NuvioTheme.colors.TextSecondary,
                textAlign = TextAlign.Start
            )
            return@Column
        }
        // Sources (s'il y en a plusieurs) + recharger
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (list.size > 1) list.forEach { s ->
                Chip(s.name, s.id == source?.id) {
                    source = s
                    prefs.edit().putString("source", s.id).apply()
                }
            }
            Chip("↻ Recharger la liste", false) { reload++ }
        }
        val all = channels
        if (all == null) {
            Text(error ?: "Chargement des chaînes… (la première fois peut être un peu longue)", color = NuvioTheme.colors.TextSecondary)
            return@Column
        }
        val groups = remember(all, favorites) {
            val g = all.map { it.group }.distinct().sortedWith(compareBy({ !isFrench(it) }, { it.lowercase() }))
            listOfNotNull(FAVORITES.takeIf { favorites.isNotEmpty() }, ALL) + g
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(groups) { g -> Chip(g, g == group) { group = g } }
        }
        val shown = remember(all, group, favorites) {
            when (group) {
                FAVORITES -> all.filter { it.key in favorites }
                ALL, null -> all.take(3000)
                else -> all.filter { it.group == group }
            }
        }
        Text("${shown.size} chaîne${if (shown.size > 1) "s" else ""} · appui long pour ajouter aux favoris", color = NuvioTheme.colors.TextTertiary, style = MaterialTheme.typography.labelMedium)
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 48.dp, top = 4.dp)
        ) {
            items(shown, key = { it.key }) { ch ->
                val shape = RoundedCornerShape(14.dp)
                Card(
                    onClick = { onPlay(ch) },
                    onLongClick = { toggleFavorite(ch) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 11f),
                    shape = CardDefaults.shape(shape),
                    border = CardDefaults.border(focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(3.dp, Color.White), shape = shape)),
                    scale = CardDefaults.scale(focusedScale = 1.06f),
                    colors = CardDefaults.colors(containerColor = Color(0xFF1C1C1E), focusedContainerColor = Color(0xFF2C2C2E))
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            if (ch.logo != null) {
                                AsyncImage(model = ch.logo, contentDescription = ch.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                            } else {
                                Text(ch.name.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                        Text(
                            (if (ch.key in favorites) "★ " else "") + ch.name,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(40.dp),
        colors = ButtonDefaults.colors(
            containerColor = if (on) Color.White else Color(0x22FFFFFF),
            contentColor = if (on) Color.Black else Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        )
    ) { Text(label, maxLines = 1) }
}

