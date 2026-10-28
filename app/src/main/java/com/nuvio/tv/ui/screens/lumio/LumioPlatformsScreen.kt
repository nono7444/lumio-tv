package com.nuvio.tv.ui.screens.lumio

// Lumio : plateformes (Netflix, Disney+, Prime Video, Apple TV+, Max, Canal+), comme sur le téléphone.
// En haut les logos (vignettes noires en verre, logos en blanc, façon Apple TV) ; en dessous, pour la plateforme
// choisie, ses sorties mélangées (déjà sur ton serveur ou à demander), par date de sortie, la plus récente ou la plus ancienne d'abord.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.json.JSONObject

data class LumioPlatform(val id: String, val name: String, val logo: String?)
data class LumioPlatformTitle(val type: String, val tmdbId: Int, val title: String, val year: String?, val date: String?, val poster: String?, val available: Boolean)

private fun JSONObject.titles(key: String): List<LumioPlatformTitle> {
    val arr = optJSONArray(key) ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        LumioPlatformTitle(
            type = o.optString("type", "movie"),
            tmdbId = o.optInt("tmdbId"),
            title = o.optString("title"),
            year = o.optString("year").takeIf { it.isNotBlank() && it != "null" },
            date = o.optString("date").takeIf { it.isNotBlank() && it != "null" },
            poster = o.optString("poster").takeIf { it.startsWith("http") },
            available = o.optBoolean("available")
        )
    }.filter { it.tmdbId > 0 }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LumioPlatformsScreen(
    showBuiltInHeader: Boolean = true,
    onOpen: (type: String, tmdbId: Int) -> Unit
) {
    val context = LocalContext.current
    val client = remember { LumioClient.get(context) }
    var platforms by remember { mutableStateOf<List<LumioPlatform>?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf("desc") }
    var shown by remember { mutableStateOf<List<LumioPlatformTitle>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { client.platforms() }
            .onSuccess { json ->
                val arr = json.optJSONArray("platforms")
                platforms = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                    arr!!.optJSONObject(i)?.let { LumioPlatform(it.optString("id"), it.optString("name"), it.optString("logo").takeIf { l -> l.startsWith("http") }) }
                }
                if (selected == null) selected = platforms?.firstOrNull()?.id
            }
            .onFailure { error = it.message ?: "Serveur Lumio injoignable" }
    }
    LaunchedEffect(selected, sort) {
        val id = selected ?: return@LaunchedEffect
        shown = null
        runCatching { client.platformPage(id, sort) }
            .onSuccess {
                // Tout mélangé par date de sortie (ancien serveur : on fusionne ici)
                shown = if (it.has("items")) it.titles("items") else {
                    val latest = it.titles("latest")
                    val keys = latest.map { x -> "${x.type}:${x.tmdbId}" }.toSet()
                    val all = latest + it.titles("library").filter { x -> "${x.type}:${x.tmdbId}" !in keys }
                    if (sort == "asc") all.sortedBy { x -> x.date ?: "" } else all.sortedByDescending { x -> x.date ?: "" }
                }
                error = null
            }
            .onFailure { error = it.message ?: "Plateforme indisponible" }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (showBuiltInHeader) item {
            Text("Plateformes", style = MaterialTheme.typography.headlineLarge, color = NuvioTheme.colors.TextPrimary)
        }
        // Logos des plateformes : verre noir, logo en blanc
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(platforms ?: emptyList(), key = { it.id }) { pf ->
                    val on = pf.id == selected
                    Card(
                        onClick = { selected = pf.id },
                        shape = CardDefaults.shape(RoundedCornerShape(18.dp)),
                        colors = CardDefaults.colors(containerColor = Color(0xFF0A0A0B), focusedContainerColor = Color(0xFF0A0A0B)),
                        modifier = Modifier.width(190.dp).height(104.dp)
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(Color(0xFF232327), Color(0xFF050506))))
                                .border(
                                    width = if (on) 2.dp else 1.dp,
                                    color = if (on) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                        ) {
                            // Reflet du verre en haut
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.55f)
                                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.04f), Color.Transparent)))
                            )
                            Box(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp), contentAlignment = Alignment.Center) {
                                if (pf.logo != null) {
                                    AsyncImage(
                                        model = pf.logo,
                                        contentDescription = pf.name,
                                        contentScale = ContentScale.Fit,
                                        colorFilter = ColorFilter.tint(Color.White),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(pf.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("desc" to "Plus récents", "asc" to "Plus anciens").forEach { (key, label) ->
                    val on = key == sort
                    Button(
                        onClick = { sort = key },
                        colors = ButtonDefaults.colors(
                            containerColor = if (on) Color.White else Color(0x22FFFFFF),
                            contentColor = if (on) Color.Black else Color.White,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black
                        )
                    ) { Text(label) }
                }
            }
        }
        error?.let { e -> item { Text(e, color = NuvioTheme.colors.TextSecondary) } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionTitle(if (sort == "desc") "Dernières sorties" else "Plus anciennes sorties")
                shown?.let { l ->
                    val missing = l.count { !it.available }
                    Text("${l.size - missing} sur ton serveur · $missing à demander", color = NuvioTheme.colors.TextSecondary, fontSize = 14.sp)
                }
            }
        }
        val list = shown
        when {
            list == null -> item { Text("Chargement…", color = NuvioTheme.colors.TextSecondary) }
            list.isEmpty() -> item { Text("Rien pour cette plateforme pour l'instant.", color = NuvioTheme.colors.TextSecondary) }
            // Grille : 7 affiches par ligne
            else -> items(list.chunked(7)) { row -> PosterGridRow(row, onOpen) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = NuvioTheme.colors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterGridRow(row: List<LumioPlatformTitle>, onOpen: (String, Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        row.forEach { t ->
            Column(modifier = Modifier.width(140.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Card(
                    onClick = { onOpen(t.type, t.tmdbId) },
                    shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                    modifier = Modifier.fillMaxWidth().height(210.dp)
                ) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF1C1C1E))) {
                        if (t.poster != null) {
                            AsyncImage(model = t.poster, contentDescription = t.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Text(t.title, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(8.dp))
                        }
                        // Pastille : déjà sur le serveur ou à demander
                        Text(
                            if (t.available) "✓ Dispo" else "À demander",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .background(
                                    if (t.available) Color(0xEB1EA05A) else Color(0xB8000000),
                                    RoundedCornerShape(50)
                                )
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(t.title, color = NuvioTheme.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    listOfNotNull(if (t.type == "tv") "Série" else "Film", t.year).joinToString(" · "),
                    color = NuvioTheme.colors.TextSecondary,
                    maxLines = 1,
                    fontSize = 12.sp
                )
            }
        }
    }
}
