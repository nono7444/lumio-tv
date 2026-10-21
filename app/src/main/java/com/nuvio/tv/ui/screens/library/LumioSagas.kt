package com.nuvio.tv.ui.screens.library

// Lumio : les sagas (collections TMDB des films présents dans Jellyfin), comme sur l'iPhone.
// Liste des sagas → films de la saga → fiche du film.

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.lumio.LumioClient
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay

data class LumioSagaItem(val jfId: String, val title: String, val year: Int?)
data class LumioSaga(val id: Int, val name: String, val poster: String?, val backdrop: String?, val total: Int, val items: List<LumioSagaItem>)

private fun parseSagas(json: org.json.JSONObject): List<LumioSaga> {
    val arr = json.optJSONArray("collections") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val c = arr.optJSONObject(i) ?: return@mapNotNull null
        val items = c.optJSONArray("items")?.let { a ->
            (0 until a.length()).mapNotNull { j ->
                a.optJSONObject(j)?.let { o ->
                    LumioSagaItem(o.optString("jfId"), o.optString("title"), if (o.isNull("year")) null else o.optInt("year"))
                }
            }
        }.orEmpty()
        LumioSaga(
            id = c.optInt("id"),
            name = c.optString("name"),
            poster = c.optString("poster").takeIf { it.startsWith("http") },
            backdrop = c.optString("backdrop").takeIf { it.startsWith("http") },
            total = c.optInt("total"),
            items = items
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LumioSagas(
    posterFor: (String) -> String?,
    onOpenMovie: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val client = remember { LumioClient.get(context) }
    var sagas by remember { mutableStateOf<List<LumioSaga>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<LumioSaga?>(null) }
    val first = remember { FocusRequester() }

    BackHandler { if (open != null) open = null else onBack() }

    LaunchedEffect(Unit) {
        // Première construction sur le serveur : on réessaie quelques fois
        repeat(6) {
            val r = runCatching { client.collections() }
            r.onFailure { error = it.message ?: "Serveur Lumio injoignable" }
            r.onSuccess { json ->
                val list = parseSagas(json)
                if (list.isNotEmpty() || !json.optBoolean("building")) {
                    sagas = list
                    return@LaunchedEffect
                }
            }
            delay(8_000)
        }
        if (sagas == null) sagas = emptyList()
    }

    val current = open
    Column(modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 28.dp)) {
        Text(
            current?.name ?: "Sagas",
            style = MaterialTheme.typography.headlineLarge,
            color = NuvioTheme.colors.TextPrimary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            if (current != null) "${current.items.size} film${if (current.items.size > 1) "s" else ""} sur ${current.total} dans ta bibliothèque"
            else "Les collections de films de ta bibliothèque",
            color = NuvioTheme.colors.TextSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        val list = sagas
        when {
            list == null -> Text(error ?: "Chargement des sagas…", color = NuvioTheme.colors.TextSecondary)
            list.isEmpty() -> Text("Aucune saga pour le moment.", color = NuvioTheme.colors.TextSecondary)
            current == null -> {
                LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(bottom = 48.dp, top = 6.dp)
                ) {
                    itemsIndexed(list, key = { _, s -> s.id }) { i, saga ->
                        Tile(
                            image = saga.backdrop ?: saga.poster,
                            title = saga.name,
                            subtitle = "${saga.items.size} / ${saga.total} films",
                            ratio = 16f / 9f,
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            onClick = { open = saga }
                        )
                    }
                }
            }
            else -> {
                val firstFilm = remember(current.id) { FocusRequester() }
                LaunchedEffect(current.id) { runCatching { firstFilm.requestFocus() } }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(bottom = 48.dp, top = 6.dp)
                ) {
                    itemsIndexed(current.items, key = { _, f -> f.jfId }) { i, film ->
                        Tile(
                            image = posterFor(film.jfId),
                            title = film.title,
                            subtitle = film.year?.toString(),
                            ratio = 2f / 3f,
                            modifier = if (i == 0) Modifier.focusRequester(firstFilm) else Modifier,
                            onClick = { onOpenMovie(film.jfId) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Tile(image: String?, title: String, subtitle: String?, ratio: Float, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().aspectRatio(ratio),
        shape = CardDefaults.shape(shape),
        border = CardDefaults.border(focusedBorder = Border(border = androidx.compose.foundation.BorderStroke(3.dp, Color.White), shape = shape)),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        colors = CardDefaults.colors(containerColor = Color(0xFF1C1C1E), focusedContainerColor = Color(0xFF1C1C1E))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            image?.let { AsyncImage(model = it, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, color = Color(0xCCFFFFFF), style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}
