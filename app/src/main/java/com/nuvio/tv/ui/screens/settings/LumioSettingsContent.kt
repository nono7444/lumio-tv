@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

// Lumio : rubrique des paramètres — état des serveurs en direct (Lumio, Jellyfin, Radarr, Sonarr) et mise à jour de l'appli.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.lumio.LumioClient
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay
import org.json.JSONObject

private val Green = Color(0xFF30D158)
private val Orange = Color(0xFFFF9F0A)
private val Red = Color(0xFFFF453A)

private data class ServiceRow(val name: String, val ok: Boolean?, val detail: String?, val bars: List<Double>)

private fun JSONObject.service(key: String, name: String): ServiceRow? {
    val o = optJSONObject(key) ?: return null
    val uptime = if (o.isNull("uptime")) null else o.optDouble("uptime")
    val parts = buildList {
        if (key == "lumio") o.optString("version").takeIf { it.isNotBlank() }?.let { add("version $it") }
        uptime?.let { add("${if (it % 1.0 == 0.0) it.toInt() else it} % sur 24 h") }
        if (o.has("queue")) add("${o.optInt("queue")} en file")
    }
    val bars = o.optJSONArray("bars")?.let { a -> (0 until a.length()).map { a.optDouble(it, -1.0) } }.orEmpty()
    return ServiceRow(name, o.optBoolean("ok"), parts.joinToString(" · ").ifBlank { null }, bars)
}

@Composable
fun LumioSettingsContent(
    initialFocusRequester: FocusRequester? = null,
    onNavigateToCatalogOrder: () -> Unit = {},
    onNavigateToAddons: () -> Unit = {}
) {
    val context = LocalContext.current
    val client = remember { LumioClient.get(context) }
    var rows by remember { mutableStateOf<List<ServiceRow>>(emptyList()) }
    var address by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var machine by remember { mutableStateOf<String?>(null) }

    // Rafraîchi toutes les 15 s tant que la rubrique est affichée
    LaunchedEffect(Unit) {
        address = client.serverAddress()
        while (true) {
            runCatching { client.publicStatus() }
                .onSuccess { s ->
                    error = null
                    rows = listOfNotNull(
                        s.service("lumio", "Serveur Lumio"),
                        s.service("jellyfin", "Jellyfin"),
                        s.service("radarr", "Radarr (films)"),
                        s.service("sonarr", "Sonarr (séries)")
                    )
                    machine = s.optJSONObject("machine")?.let { m ->
                        listOfNotNull(
                            if (m.isNull("cpu")) null else "processeur ${m.optInt("cpu")} %",
                            if (m.isNull("temp")) null else "${m.optInt("temp")} °C"
                        ).joinToString(" · ").ifBlank { null }
                    }
                }
                .onFailure {
                    error = it.message ?: "Serveur Lumio injoignable"
                    rows = listOf(ServiceRow("Serveur Lumio", false, "injoignable", emptyList()))
                }
            delay(15_000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsDetailHeader(
            title = "Lumio",
            subtitle = "État des serveurs et mise à jour de l'appli"
        )
        SettingsGroupCard(modifier = Modifier.fillMaxWidth().weight(1f), title = null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Mise à jour (depuis les Releases de nono7444/lumio-tv)
                UpdateChannelSettings(initialFocusRequester)

                // Affiches : TMDB par défaut, Better Posters au choix
                val betterPosters by com.nuvio.tv.lumio.LumioPosterSource.betterPosters.collectAsState()
                SettingsToggleRow(
                    title = "Affiches Better Posters",
                    subtitle = if (betterPosters) "Activé : affiches Better Posters (genre, note). Relance l'appli pour tout mettre à jour."
                    else "Désactivé : affiches TMDB pour tous les titres. Relance l'appli pour tout mettre à jour.",
                    checked = betterPosters,
                    onToggle = { com.nuvio.tv.lumio.LumioPosterSource.setBetterPosters(context, !betterPosters) }
                )

                // Personnalisation de l'accueil : ordre des rangées, rangées masquées
                SettingsActionRow(
                    title = "Personnaliser l'accueil",
                    subtitle = "Choisir les rangées affichées et leur ordre",
                    onClick = onNavigateToCatalogOrder
                )

                // Addons Nuvio / Stremio : leurs catalogues deviennent des rangées de l'accueil (pas de lecture via les addons)
                SettingsActionRow(
                    title = "Addons",
                    subtitle = "Ajouter des addons Nuvio / Stremio : leurs catalogues apparaissent sur l'accueil",
                    onClick = onNavigateToAddons
                )

                Text(
                    "Serveurs",
                    style = MaterialTheme.typography.titleMedium,
                    color = NuvioTheme.colors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (rows.isEmpty() && error == null) {
                    Text("Vérification…", color = NuvioTheme.colors.TextSecondary)
                }
                rows.forEach { row -> ServiceStatusRow(row) }
                machine?.let { Text("Machine : $it", color = NuvioTheme.colors.TextSecondary, style = MaterialTheme.typography.labelMedium) }
                address?.let { Text("Adresse : $it", color = NuvioTheme.colors.TextTertiary, style = MaterialTheme.typography.labelSmall) }
                Text(
                    "Lumio TV ${BuildConfig.VERSION_NAME}",
                    color = NuvioTheme.colors.TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun ServiceStatusRow(row: ServiceRow) {
    val color = when (row.ok) {
        true -> Green
        false -> Red
        null -> Orange
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x14FFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
        Column(modifier = Modifier.weight(1f)) {
            Text(row.name, color = NuvioTheme.colors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                (if (row.ok == true) "En ligne" else "Hors ligne") + (row.detail?.let { " · $it" } ?: ""),
                color = NuvioTheme.colors.TextSecondary,
                style = MaterialTheme.typography.labelMedium
            )
        }
        // Barres des dernières 24 h, façon UptimeRobot
        if (row.bars.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                row.bars.forEach { b ->
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(18.dp)
                            .background(
                                when {
                                    b < 0 -> Color(0x33FFFFFF)
                                    b >= 1.0 -> Green
                                    b > 0 -> Orange
                                    else -> Red
                                },
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
    }
}
