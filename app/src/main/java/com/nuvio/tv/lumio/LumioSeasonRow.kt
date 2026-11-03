package com.nuvio.tv.lumio

// Lumio : rangée de saison de l'accueil TV (comme sur le téléphone) : titres du serveur pour Halloween ou Noël.

data class LumioSeasonRowDef(val title: String, val genres: List<String>, val searches: List<String>)

object LumioSeasonRow {
    /** Plus de rangée de saison : accueil de Nuvio de base. */
    fun current(): LumioSeasonRowDef? = null

    private fun currentSeasonal(): LumioSeasonRowDef? = when (activeDecoSeason()) {
        LumioDecoSeason.HALLOWEEN -> LumioSeasonRowDef(
            title = "Halloween",
            genres = listOf("Horreur", "Horror", "Épouvante", "Epouvante", "Épouvante-Horreur"),
            searches = listOf("Halloween")
        )
        LumioDecoSeason.CHRISTMAS -> LumioSeasonRowDef(
            title = "Noël",
            genres = emptyList(),
            searches = listOf("Noël", "Christmas", "Santa", "Père Noël")
        )
        else -> null
    }
}
