@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.lumio

// Lumio : écran « Calendrier » (le même que sur le téléphone) : radar des sorties VOD d'amtVOD, lu par le serveur Lumio.
// Onglets : disponible en VOD, sortie VOD confirmée (J-X), au cinéma (prédiction). OK : ouvrir la fiche du film.

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
import com.nuvio.tv.lumio.LumioClient
import com.nuvio.tv.lumio.LumioRadar
import com.nuvio.tv.lumio.LumioRadarItem
import com.nuvio.tv.ui.theme.NuvioTheme

private fun statusLabel(status: String) = when (status) {
    "available" -> "Disponible en VOD"
    "confirmed" -> "Sortie VOD confirmée"
    "cinema" -> "Au cinéma"
    else -> "Autres"
}

private fun statusColor(status: String) = when (status) {
    "available" -> Color(0xFF30D158)
    "confirmed" -> Color(0xFFFF9F0A)
    "cinema" -> Color(0xFFFF453A)
    else -> Color(0xFF8E8E93)
}

@Composable
fun LumioCalendarScreen(
    showBuiltInHeader: Boolean = true,
    onOpen: (item: LumioRadarItem) -> Unit
) {
    val context = LocalContext.current
    var radar by remember { mutableStateOf<LumioRadar?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { LumioClient.get(context).radar() }
            .onSuccess { radar = it; error = it.error }
            .onFailure { error = it.message ?: "Serveur Lumio injoignable" }
    }

    val sections = radar?.sections.orEmpty().filter { it.items.isNotEmpty() }
    val cur = sections.firstOrNull { it.id == tab } ?: sections.firstOrNull()

    Column(
        modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (showBuiltInHeader) {
            Text("Calendrier", style = MaterialTheme.typography.headlineLarge, color = NuvioTheme.colors.TextPrimary)
        }
        Text("Radar des sorties VOD · amtVOD", color = NuvioTheme.colors.TextSecondary)
        if (sections.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                sections.forEach { s ->
                    val on = s.id == cur?.id
                    Button(
                        onClick = { tab = s.id },
                        colors = ButtonDefaults.colors(
                            containerColor = if (on) Color.White else Color(0x22FFFFFF),
                            contentColor = if (on) Color.Black else Color.White,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black
                        )
                    ) { Text("${statusLabel(s.status)} · ${s.items.size}") }
                }
            }
        }
        when {
            radar == null && error != null -> Text("Calendrier indisponible : $error", color = NuvioTheme.colors.TextSecondary)
            radar == null -> Text("Chargement…", color = NuvioTheme.colors.TextSecondary)
            cur == null -> Text(if (error != null) "Calendrier indisponible : $error" else "Rien pour le moment.", color = NuvioTheme.colors.TextSecondary)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                contentPadding = PaddingValues(bottom = 48.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(cur.items, key = { it.id.ifBlank { it.name } }) { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Card(
                            onClick = { if (item.tmdbId != null) onOpen(item) },
                            shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                        ) {
                            Box(Modifier.fillMaxSize().background(Color(0xFF1C1C1E))) {
                                if (item.poster != null) {
                                    AsyncImage(model = item.poster, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Text(item.name, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(8.dp))
                                }
                            }
                        }
                        Text(item.name, color = NuvioTheme.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(
                            item.releaseInfo ?: statusLabel(cur.status),
                            color = statusColor(cur.status),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
