package com.nuvio.tv.ui.screens.detail

// Lumio : notes de la famille (1 à 10 étoiles), stockées sur le serveur Lumio — même système que l'appli iPhone.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.lumio.LumioClient
import com.nuvio.tv.lumio.LumioRating
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.launch

private val Gold = Color(0xFFFFC93C)

/** Verdict d'une note sur 10, comme sur le téléphone. */
internal fun lumioVerdict(n: Double): Pair<String, Color> = when {
    n >= 7.5 -> "Top" to Color(0xFF34C759)
    n >= 5.0 -> "Moyen" to Color(0xFFFF9F0A)
    else -> "Bouse" to Color(0xFFB5651D)
}

private fun fmt(n: Double): String = if (n % 1.0 == 0.0) n.toInt().toString() else String.format(java.util.Locale.FRANCE, "%.1f", n)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LumioRatingDialog(
    client: LumioClient,
    type: String,
    tmdbId: Int,
    title: String,
    poster: String?,
    imdb: String? = null,
    onDismiss: () -> Unit,
    onChanged: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var info by remember { mutableStateOf<LumioRating?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var stars by remember { mutableIntStateOf(0) }
    var hovered by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val firstStar = remember { FocusRequester() }

    LaunchedEffect(tmdbId) {
        runCatching { client.rating(type, tmdbId) }
            .onSuccess {
                info = it
                stars = it.mine ?: 0
            }
            .onFailure { error = it.message }
        runCatching { firstStar.requestFocus() }
    }

    fun save(value: Int) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { client.rate(type, tmdbId, value, title, poster, imdb) }
                .onSuccess {
                    com.nuvio.tv.lumio.LumioRatingBadges.refresh(context.applicationContext, force = true)
                    onChanged()
                    onDismiss()
                }
                .onFailure { error = it.message }
            busy = false
        }
    }

    val avg = info?.avg
    NuvioDialog(
        onDismiss = onDismiss,
        title = "Notes de la famille",
        subtitle = title,
        width = 640.dp
    ) {
        // Moyenne de la famille + verdict
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Gold, modifier = Modifier.size(26.dp))
            if (avg != null) {
                val (label, color) = lumioVerdict(avg)
                Text("${fmt(avg)} / 10", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Text("· $label", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
                Text("(${info?.count ?: 0} note${if ((info?.count ?: 0) > 1) "s" else ""})", color = NuvioTheme.extendedColors.textSecondary)
            } else {
                Text(if (info == null && error == null) "Chargement…" else "Pas encore de note", color = NuvioTheme.extendedColors.textSecondary)
            }
        }

        // Ma note : 10 étoiles, gauche / droite pour choisir, OK pour valider
        Text(
            if (stars > 0) "Ta note : $stars / 10" else "Ta note",
            style = MaterialTheme.typography.labelLarge,
            color = NuvioTheme.extendedColors.textSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 1..10) {
                val lit = i <= (if (hovered > 0) hovered else stars)
                IconButton(
                    onClick = { save(i) },
                    enabled = !busy,
                    modifier = Modifier
                        .size(44.dp)
                        .then(if (i == maxOf(1, stars)) Modifier.focusRequester(firstStar) else Modifier)
                        .onFocusChanged { if (it.isFocused) hovered = i else if (hovered == i) hovered = 0 },
                    colors = IconButtonDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = Gold,
                        focusedContainerColor = Color(0x33FFFFFF),
                        focusedContentColor = Gold
                    )
                ) {
                    Icon(if (lit) Icons.Filled.Star else Icons.Outlined.StarOutline, contentDescription = "$i étoiles", tint = Gold)
                }
            }
        }

        if (stars > 0) {
            Button(
                onClick = { save(0) },
                enabled = !busy,
                colors = ButtonDefaults.colors(containerColor = Color(0x22FFFFFF), contentColor = Color.White)
            ) { Text("Retirer ma note") }
        }

        error?.let { Text(it, color = Color(0xFFFF453A), style = MaterialTheme.typography.labelMedium) }

        // Avis de la famille
        val votes = info?.votes.orEmpty()
        if (votes.isNotEmpty()) {
            LazyColumn(modifier = Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(votes) { v ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(Color(0x33FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text(v.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold) }
                        Column(modifier = Modifier.padding(end = 8.dp)) {
                            Text(
                                (if (v.mine) "Toi" else v.name) + " · ${v.stars}/10",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            v.comment?.let { Text(it, color = NuvioTheme.extendedColors.textSecondary, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}
