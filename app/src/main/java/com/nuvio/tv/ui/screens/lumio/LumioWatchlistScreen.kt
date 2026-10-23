@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.lumio

// Lumio : écran « À voir » (liste personnelle de films et séries, la même que sur le téléphone).
// OK : ouvrir la fiche. Appui long : retirer de la liste.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.lumio.LumioWatchItem
import com.nuvio.tv.lumio.LumioWatchlist
import com.nuvio.tv.lumio.MediaStatus
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.launch

private val FILTERS = listOf("all" to "Tout", "available" to "Disponibles", "missing" to "Pas encore là", "movie" to "Films", "tv" to "Séries")

private fun LumioWatchItem.isThere() = status == MediaStatus.AVAILABLE || status == MediaStatus.PARTIALLY_AVAILABLE

@Composable
fun LumioWatchlistScreen(
    showBuiltInHeader: Boolean = true,
    onOpen: (item: LumioWatchItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by LumioWatchlist.items.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("all") }

    LaunchedEffect(Unit) {
        runCatching { LumioWatchlist.load(context) }.onFailure { error = it.message ?: "Serveur Lumio injoignable" }
    }

    val list = (items ?: emptyList()).filter {
        when (filter) {
            "available" -> it.isThere()
            "missing" -> !it.isThere()
            "movie", "tv" -> it.type == filter
            else -> true
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showBuiltInHeader) {
            Text("À voir", style = MaterialTheme.typography.headlineLarge, color = NuvioTheme.colors.TextPrimary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FILTERS.forEach { (key, label) ->
                val on = key == filter
                Button(
                    onClick = { filter = key },
                    colors = ButtonDefaults.colors(
                        containerColor = if (on) Color.White else Color(0x22FFFFFF),
                        contentColor = if (on) Color.Black else Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    )
                ) { Text(label) }
            }
        }
        when {
            items == null && error != null -> Text(error ?: "", color = NuvioTheme.colors.TextSecondary)
            items == null -> Text("Chargement…", color = NuvioTheme.colors.TextSecondary)
            list.isEmpty() -> Text(
                if (items.isNullOrEmpty()) "Ta liste est vide. Sur la fiche d'un film ou d'une série, appuie sur le signet pour l'ajouter."
                else "Rien dans ce filtre.",
                color = NuvioTheme.colors.TextSecondary
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(bottom = 48.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(list, key = { "${it.type}:${it.tmdbId}" }) { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Card(
                            onClick = { onOpen(item) },
                            onLongClick = { scope.launch { runCatching { LumioWatchlist.remove(context, item.type, item.tmdbId) } } },
                            shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                        ) {
                            Box(Modifier.fillMaxSize().background(Color(0xFF1C1C1E))) {
                                if (item.poster != null) {
                                    AsyncImage(model = item.poster, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Text(item.title, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(8.dp))
                                }
                            }
                        }
                        Text(item.title, color = NuvioTheme.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        val (label, color) = when {
                            item.isThere() -> "● Disponible" to Color(0xFF30D158)
                            item.status == MediaStatus.PROCESSING -> "● En cours" to Color(0xFF3D8BFF)
                            item.status == MediaStatus.PENDING -> "● Demandé" to Color(0xFFFF9F0A)
                            else -> "○ Pas encore là" to Color(0xFF9AA3B2)
                        }
                        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Text("Appui long sur une affiche : la retirer de la liste.", color = NuvioTheme.colors.TextTertiary, style = MaterialTheme.typography.labelSmall)
    }
}
