package com.nuvio.tv.lumio

// Lumio : recherche TMDB (films et séries) à la place de Cinemeta. Présentée comme un « addon » interne
// pour réutiliser l'écran de recherche de Nuvio ; les résultats ouvrent la fiche TMDB (id « tmdb:123 »),
// d'où l'on peut lire (si le titre est sur Jellyfin) ou demander.

import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonResource
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.CatalogExtra
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape

object LumioTmdbSearch {
    const val BASE_URL = "lumio-tmdb://search"
    const val ADDON_ID = "lumio.tmdb"
    private const val MOVIE_ID = "lumio-tmdb-movie"
    private const val SERIES_ID = "lumio-tmdb-series"

    fun isTmdbSearch(baseUrl: String?): Boolean = baseUrl == BASE_URL

    val addon: Addon = Addon(
        id = ADDON_ID,
        name = "TMDB",
        displayName = "TMDB",
        version = "1",
        description = null,
        logo = null,
        baseUrl = BASE_URL,
        catalogs = listOf(
            CatalogDescriptor(type = ContentType.MOVIE, id = MOVIE_ID, name = "Films", extra = listOf(CatalogExtra(name = "search", isRequired = true))),
            CatalogDescriptor(type = ContentType.SERIES, id = SERIES_ID, name = "Séries", extra = listOf(CatalogExtra(name = "search", isRequired = true)))
        ),
        types = listOf(ContentType.MOVIE, ContentType.SERIES),
        resources = listOf(AddonResource(name = "catalog", types = emptyList(), idPrefixes = null))
    )

    private fun img(path: String?, size: String) = path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/$size$it" }

    suspend fun catalog(
        api: TmdbApi,
        addonName: String,
        catalogId: String,
        catalogName: String,
        type: String,
        extraArgs: Map<String, String>
    ): CatalogRow {
        val query = extraArgs["search"].orEmpty().trim()
        val isSeries = catalogId == SERIES_ID
        val results = if (query.isBlank()) emptyList() else {
            val response = if (isSeries) api.searchTv(LumioTmdb.key, query, "fr-FR") else api.searchMovies(LumioTmdb.key, query, "fr-FR")
            response.body()?.results.orEmpty()
        }
        val items = results
            .sortedByDescending { (it.popularity ?: 0.0) + (it.voteCount ?: 0) / 50.0 }
            .map { r ->
                MetaPreview(
                    id = "tmdb:${r.id}",
                    type = if (isSeries) ContentType.SERIES else ContentType.MOVIE,
                    name = (if (isSeries) r.name else r.title) ?: r.originalTitle ?: r.originalName ?: "",
                    poster = img(r.posterPath, "w342"),
                    posterShape = PosterShape.POSTER,
                    background = img(r.backdropPath, "w1280"),
                    logo = null,
                    description = r.overview,
                    releaseInfo = (r.releaseDate ?: r.firstAirDate)?.take(4),
                    imdbRating = r.voteAverage?.takeIf { it > 0 }?.toFloat(),
                    genres = emptyList(),
                    released = r.releaseDate ?: r.firstAirDate
                )
            }
        return CatalogRow(
            addonId = ADDON_ID,
            addonName = addonName,
            addonBaseUrl = BASE_URL,
            catalogId = catalogId,
            catalogName = catalogName,
            type = if (isSeries) ContentType.SERIES else ContentType.MOVIE,
            rawType = type,
            items = items,
            isLoading = false,
            hasMore = false,
            supportsSkip = false,
            extraArgs = extraArgs
        )
    }
}
