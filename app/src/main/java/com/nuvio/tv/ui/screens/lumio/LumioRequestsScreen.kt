@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.lumio

// Lumio : écran « Demandes » (comme sur l'iPhone) : statut, téléchargement en direct, validation par l'admin.

import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.lumio.LumioClient
import com.nuvio.tv.lumio.LumioListedRequest
import com.nuvio.tv.lumio.LumioMe
import com.nuvio.tv.lumio.RequestStatus
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Blue = Color(0xFF3D8BFF)
private val Green = Color(0xFF30D158)
private val Orange = Color(0xFFFF9F0A)
private val Red = Color(0xFFFF453A)
private val Grey = Color(0xFF8E8E93)

private val FILTERS = listOf(
    "all" to "Toutes",
    "pending" to "En attente",
    "processing" to "En cours",
    "available" to "Disponibles",
    "failed" to "En échec"
)

private fun statusOf(r: LumioListedRequest): Triple<String, Color, Float?> {
    val d = r.download
    return when (r.status) {
        RequestStatus.PENDING -> Triple("En attente de validation", Orange, null)
        RequestStatus.DECLINED -> Triple("Refusée" + (r.reason?.let { " : $it" } ?: ""), Red, null)
        RequestStatus.FAILED -> Triple("Échec" + (r.error?.let { " : $it" } ?: ""), Red, null)
        RequestStatus.COMPLETED -> Triple("Disponible", Green, null)
        else -> when {
            // Groupe de secours : Radarr / Sonarr hors service, Lumio Secour télécharge à leur place
            d != null && d.via == "secour" -> Triple("🛟 ${d.title ?: "Lumio Secour"}" + (d.progress?.let { " · $it %" } ?: "") + (if (d.count > 1) " · ${d.count} épisodes restants" else ""), Green, (d.progress ?: 0) / 100f)
            else -> statusOfArr(d)
        }
    }
}

private fun statusOfArr(d: com.nuvio.tv.lumio.LumioDownload?): Triple<String, Color, Float?> =
        when (d?.state) {
            "downloading" -> Triple("Téléchargement ${d.progress?.let { "$it %" } ?: "…"}" + (if (d.count > 1) " · ${d.count} épisodes" else ""), Blue, (d.progress ?: 0) / 100f)
            "importing" -> Triple("Ajout au serveur…", Green, 1f)
            "warning" -> Triple("Bloqué : ${d.message ?: "à vérifier"}", Orange, 1f)
            else -> Triple("Recherche d'une source…", Grey, 0f)
        }

@Composable
fun LumioRequestsScreen(showBuiltInHeader: Boolean = true) {
    val context = LocalContext.current
    val client = remember { LumioClient.get(context) }
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf("all") }
    var list by remember { mutableStateOf<List<LumioListedRequest>?>(null) }
    var me by remember { mutableStateOf<LumioMe?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { me = runCatching { client.me() }.getOrNull() }
    LaunchedEffect(filter, tick) {
        runCatching { client.list(filter) }
            .onSuccess { list = it; error = null }
            .onFailure { error = it.message ?: "Serveur Lumio injoignable" }
    }
    // Rafraîchi toutes les 3 s : pourcentages de téléchargement en direct
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            tick++
        }
    }

    fun act(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }.onFailure { error = it.message }
            tick++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, end = 48.dp, top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showBuiltInHeader) {
            Text("Demandes", style = MaterialTheme.typography.headlineLarge, color = NuvioTheme.colors.TextPrimary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FILTERS.forEach { (key, label) ->
                val on = key == filter
                Button(
                    onClick = { filter = key; list = null },
                    colors = ButtonDefaults.colors(
                        containerColor = if (on) Color.White else Color(0x22FFFFFF),
                        contentColor = if (on) Color.Black else Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    )
                ) { Text(label) }
            }
        }
        error?.let { Text(it, color = Red) }
        val rows = list
        when {
            rows == null && error == null -> Text("Chargement…", color = NuvioTheme.extendedColors.textSecondary)
            rows != null && rows.isEmpty() -> Text("Aucune demande.", color = NuvioTheme.extendedColors.textSecondary)
            rows != null -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 48.dp)
            ) {
                items(rows, key = { it.id }) { r ->
                    RequestCard(
                        r = r,
                        admin = me?.admin == true,
                        onApprove = { act { client.approve(r.id) } },
                        onDecline = { act { client.decline(r.id) } },
                        onRetry = { act { client.retry(r.id) } },
                        onCancel = { act { client.cancel(r.id) } },
                        onRecheck = {
                            scope.launch {
                                val msg = runCatching { client.recheck(r.id) }.getOrElse { it.message ?: "Relance impossible" }
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                tick++
                            }
                        },
                        onSecour = {
                            scope.launch {
                                val msg = runCatching { client.secourViaRequest(r.id); "« ${r.title} » part avec Lumio Secour" }
                                    .getOrElse { it.message ?: "Lumio Secour : impossible" }
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                tick++
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestCard(
    r: LumioListedRequest,
    admin: Boolean,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onRecheck: () -> Unit,
    onSecour: () -> Unit
) {
    val (label, color, progress) = statusOf(r)
    // Chaque carte peut recevoir le focus : on descend / monte dans la liste avec la télécommande
    var focused by remember { mutableStateOf(false) }
    val canRecheck = r.status == RequestStatus.APPROVED &&
        (r.download == null || r.download.state == "searching" || r.download.state == "partial" || r.download.state == "warning") &&
        (admin || r.mine)
    // « LS » (admin) : Radarr / Sonarr bloqués ou en échec → Lumio Secour prend le relais
    val canSecour = admin && r.download?.via != "secour" && (
        r.status == RequestStatus.FAILED ||
            (r.status == RequestStatus.APPROVED && (r.download == null || r.download.state == "searching" || r.download.state == "partial" || r.download.state == "warning"))
        )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (focused) Color(0x33FFFFFF) else Color(0x14FFFFFF))
            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(16.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = r.poster,
            contentDescription = r.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(72.dp)
                .height(108.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x22FFFFFF))
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                r.title + (r.year?.let { " ($it)" } ?: ""),
                style = MaterialTheme.typography.titleLarge,
                color = NuvioTheme.colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(
                    if (r.type == "tv") "Série" else "Film",
                    r.seasons.takeIf { it.isNotEmpty() }?.let { "saison${if (it.size > 1) "s" else ""} ${it.joinToString(", ")}" },
                    "demandé par ${if (r.mine) "toi" else r.requestedBy}"
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.extendedColors.textSecondary
            )
            Text(label, color = color, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0x22FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(color)
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (admin && r.status == RequestStatus.PENDING) {
                SmallButton("Valider", Icons.Default.Check, Green, onApprove)
                SmallButton("Refuser", Icons.Default.Close, Red, onDecline)
            } else if (admin && r.status == RequestStatus.FAILED) {
                SmallButton("Réessayer", Icons.Default.Refresh, Blue, onRetry)
            } else if (r.mine && r.status == RequestStatus.PENDING) {
                SmallButton("Annuler", Icons.Default.Close, Red, onCancel)
            }
            if (canRecheck) {
                SmallButton("Relancer", Icons.Default.Refresh, Blue, onRecheck)
            }
            if (canSecour) {
                Button(
                    onClick = onSecour,
                    colors = ButtonDefaults.colors(
                        containerColor = Green.copy(alpha = 0.2f),
                        contentColor = Green,
                        focusedContainerColor = Green,
                        focusedContentColor = Color.Black
                    )
                ) {
                    Text("LS", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun SmallButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = color.copy(alpha = 0.2f),
            contentColor = color,
            focusedContainerColor = color,
            focusedContentColor = Color.Black
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 6.dp))
        Text(text)
    }
}
