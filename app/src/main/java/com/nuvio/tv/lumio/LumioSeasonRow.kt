package com.nuvio.tv.lumio

// Lumio : rangée de saison de l'accueil TV (comme sur le téléphone) : titres du serveur pour Halloween ou Noël.

data class LumioSeasonRowDef(val title: String, val genres: List<String>, val searches: List<String>)

object LumioSeasonRow {
    fun current(): LumioSeasonRowDef? = when (currentDecoSeason()) {
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
