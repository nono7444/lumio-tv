package com.nuvio.tv.ui.screens.library

// Lumio : bibliothèque comme sur l'iPhone — une grande tuile par bibliothèque Jellyfin (Films, Séries, Animés…)
// avec l'image du dernier ajout ; on choisit une tuile pour voir toute la bibliothèque, de la plus récente à la plus ancienne.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.data.mediaserver.ServerConnection
import com.nuvio.tv.data.mediaserver.ServerLibrary
import com.nuvio.tv.data.mediaserver.ServerMediaKind
import com.nuvio.tv.data.mediaserver.ServerRepository
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LumioLibraryTile(val connection: ServerConnection, val library: ServerLibrary)

/** Image de fond du dernier titre ajouté dans la bibliothèque (ou l'image de la bibliothèque). */
private suspend fun latestBackdrop(repository: ServerRepository, tile: LumioLibraryTile): String? = withContext(Dispatchers.IO) {
    val session = repository.session(tile.connection.id) ?: return@withContext null
    val base = tile.connection.address.trimEnd('/')
    val user = tile.connection.remoteUserId
    val fallback = "$base/Items/${tile.library.id}/Images/Primary?maxWidth=900&api_key=${session.token}"
    runCatching {
        val url = "$base/Users/$user/Items/Latest?ParentId=${URLEncoder.encode(tile.library.id, "UTF-8")}&Limit=6&Fields=BackdropImageTags&ImageTypeLimit=1&api_key=${session.token}"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 15000
        val text = try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
        val arr = org.json.JSONArray(text)
        for (i in 0 until arr.length()) {
            val it: JSONObject = arr.getJSONObject(i)
            val tags = it.optJSONArray("BackdropImageTags")
            if (tags != null && tags.length() > 0) {
                return@runCatching "$base/Items/${it.optString("Id")}/Images/Backdrop/0?maxWidth=900&tag=${tags.optString(0)}&api_key=${session.token}"
            }
            val parent = it.optString("ParentBackdropItemId")
            val ptags = it.optJSONArray("ParentBackdropImageTags")
            if (parent.isNotBlank() && ptags != null && ptags.length() > 0) {
                return@runCatching "$base/Items/$parent/Images/Backdrop/0?maxWidth=900&tag=${ptags.optString(0)}&api_key=${session.token}"
            }
        }
        null
    }.getOrNull() ?: fallback
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LumioLibraryTiles(
    repository: ServerRepository,
    connections: List<ServerConnection>,
    onOpen: (LumioLibraryTile) -> Unit,
    onOpenDetail: (itemId: String, type: String, addonBaseUrl: String?) -> Unit = { _, _, _ -> }
) {
    var showSagas by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val jellyfin = connections.firstOrNull()
    if (showSagas && jellyfin != null) {
        val token = remember(jellyfin.id) { repository.session(jellyfin.id)?.token }
        LumioSagas(
            posterFor = { jfId -> token?.let { "${jellyfin.address.trimEnd('/')}/Items/$jfId/Images/Primary?maxHeight=450&api_key=$it" } },
            onOpenMovie = { jfId ->
                onOpenDetail(
                    com.nuvio.tv.data.mediaserver.ServerItemRef(jellyfin.id, jfId).encode(),
                    "movie",
                    com.nuvio.tv.data.mediaserver.ServerCatalog.baseUrl(jellyfin.id)
                )
            },
            onBack = { showSagas = false }
        )
        return
    }
    val tiles = remember(connections) {
        connections.flatMap { c ->
            c.selectedLibraries.filter { it.kind != ServerMediaKind.COLLECTION }.map { LumioLibraryTile(c, it) }
        }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(tiles.size) { if (tiles.isNotEmpty()) runCatching { first.requestFocus() } }

    Column(modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 28.dp)) {
        Text(
            "Bibliothèque",
            style = MaterialTheme.typography.headlineLarge,
            color = NuvioTheme.colors.TextPrimary,
            modifier = Modifier.padding(bottom = 18.dp)
        )
        if (tiles.isEmpty()) {
            Text("Aucune bibliothèque Jellyfin.", color = NuvioTheme.colors.TextSecondary)
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            contentPadding = PaddingValues(bottom = 48.dp, top = 6.dp)
        ) {
            itemsIndexed(tiles, key = { _, t -> t.connection.id + t.library.id }) { index, tile ->
                var image by remember(tile) { mutableStateOf<String?>(null) }
                LaunchedEffect(tile) { image = latestBackdrop(repository, tile) }
                val shape = RoundedCornerShape(18.dp)
                Card(
                    onClick = { onOpen(tile) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 8f)
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                    shape = CardDefaults.shape(shape),
                    border = CardDefaults.border(focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(3.dp, Color.White), shape = shape)),
                    scale = CardDefaults.scale(focusedScale = 1.04f),
                    colors = CardDefaults.colors(containerColor = Color(0xFF1C1C1E), focusedContainerColor = Color(0xFF1C1C1E))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        image?.let {
                            AsyncImage(model = it, contentDescription = tile.library.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        )
                        Text(
                            tile.library.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)
                        )
                    }
                }
            }
            // Sagas de Lumio (à la place des « Collections » de Jellyfin)
            item(key = "lumio-sagas") {
                val shape = RoundedCornerShape(18.dp)
                Card(
                    onClick = { showSagas = true },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 8f),
                    shape = CardDefaults.shape(shape),
                    border = CardDefaults.border(focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(3.dp, Color.White), shape = shape)),
                    scale = CardDefaults.scale(focusedScale = 1.04f),
                    colors = CardDefaults.colors(containerColor = Color(0xFF1C1C1E), focusedContainerColor = Color(0xFF1C1C1E))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(listOf(Color(0xFF3A2A6E), Color(0xFF8E3B5A))))
                    ) {
                        Column(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                            Text("Sagas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Les collections de films", color = Color(0xCCFFFFFF))
                        }
                    }
                }
            }
        }
    }
}
