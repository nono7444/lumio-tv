package com.nuvio.tv.lumio

// Lumio : fond de l'accueil au choix (Paramètres → Lumio) : aucun, selon la saison, Halloween ou Noël.
// Illustrations propres à Lumio, affichées derrière les rangées, assombries pour garder le texte lisible.

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.nuvio.tv.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class LumioHomeBg(val label: String) { NONE("Aucun"), AUTO("Selon la saison"), HALLOWEEN("Halloween"), CHRISTMAS("Noël") }

object LumioHomeBackground {
    private val _choice = MutableStateFlow(LumioHomeBg.NONE)
    val choice: StateFlow<LumioHomeBg> = _choice

    fun start(context: Context) {
        val v = context.getSharedPreferences("lumio", Context.MODE_PRIVATE).getString("home_bg", null)
        _choice.value = LumioHomeBg.entries.firstOrNull { it.name == v } ?: LumioHomeBg.NONE
    }

    fun next(context: Context) {
        val all = LumioHomeBg.entries
        val n = all[(all.indexOf(_choice.value) + 1) % all.size]
        _choice.value = n
        context.getSharedPreferences("lumio", Context.MODE_PRIVATE).edit().putString("home_bg", n.name).apply()
    }

    fun drawable(c: LumioHomeBg): Int? = when (c) {
        LumioHomeBg.HALLOWEEN -> R.drawable.lumio_bg_halloween
        LumioHomeBg.CHRISTMAS -> R.drawable.lumio_bg_christmas
        LumioHomeBg.AUTO -> when (currentDecoSeason()) {
            LumioDecoSeason.HALLOWEEN -> R.drawable.lumio_bg_halloween
            LumioDecoSeason.CHRISTMAS, LumioDecoSeason.NEW_YEAR -> R.drawable.lumio_bg_christmas
            else -> null
        }
        LumioHomeBg.NONE -> null
    }
}

/** Fond de l'accueil (dessiné une seule fois, aucune animation). */
@Composable
fun LumioHomeBackdrop(modifier: Modifier = Modifier) {
    if (!LumioMode.ENABLED) return
    val choice by LumioHomeBackground.choice.collectAsState()
    val res = LumioHomeBackground.drawable(choice) ?: return
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(res),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Assombri en bas et à gauche : les rangées et le texte restent lisibles
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x33000000), Color(0x99000000), Color(0xE6000000)))
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xB3000000), Color.Transparent))
            )
        )
    }
}
