package com.nuvio.tv.lumio

// Lumio : recherche TMDB (films et séries) à la place de Cinemeta. Présentée comme un « addon » interne
// pour réutiliser l'écran de recherche de Nuvio ; les résultats ouvrent la fiche TMDB (id « tmdb:123 »),
// d'où l'on peut lire (si le titre est sur Jellyfin) ou demander.
// Une seule rangée « Résultats » : les affiches des films et séries trouvés, mélangés par popularité
// (plus de rangées par bibliothèque ni de catalogues : moins de chargements, plus léger sur les box TV).

import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonResource
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.CatalogExtra
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private data class SearchHit(
    val id: Int,
    val title: String?,
    val name: String?,
    val originalTitle: String?,
    val originalName: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val overview: String?,
    val releaseDate: String?,
    val firstAirDate: String?,
    val voteAverage: Double?,
    val voteCount: Int?,
    val popularity: Double?
) {
    companion object {
        fun fromJson(o: org.json.JSONObject) = SearchHit(
            id = o.optInt("id"),
            title = o.optString("title").takeIf { it.isNotBlank() },
            name = o.optString("name").takeIf { it.isNotBlank() },
            originalTitle = o.optString("original_title").takeIf { it.isNotBlank() },
            originalName = o.optString("original_name").takeIf { it.isNotBlank() },
            posterPath = o.optString("poster_path").takeIf { it.startsWith("/") },
            backdropPath = o.optString("backdrop_path").takeIf { it.startsWith("/") },
            overview = o.optString("overview").takeIf { it.isNotBlank() },
            releaseDate = o.optString("release_date").takeIf { it.isNotBlank() },
            firstAirDate = o.optString("first_air_date").takeIf { it.isNotBlank() },
            voteAverage = if (o.has("vote_average")) o.optDouble("vote_average") else null,
            voteCount = if (o.has("vote_count")) o.optInt("vote_count") else null,
            popularity = if (o.has("popularity")) o.optDouble("popularity") else null
        )

        fun fromDto(r: com.nuvio.tv.data.remote.api.TmdbDiscoverResult) = SearchHit(
            r.id, r.title, r.name, r.originalTitle, r.originalName, r.posterPath, r.backdropPath,
            r.overview, r.releaseDate, r.firstAirDate, r.voteAverage, r.voteCount, r.popularity
        )
    }
}

object LumioTmdbSearch {
    const val BASE_URL = "lumio-tmdb://search"
    const val ADDON_ID = "lumio.tmdb"
    private const val ALL_ID = "lumio-tmdb-all"

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
            CatalogDescriptor(type = ContentType.MOVIE, id = ALL_ID, name = "Résultats", extra = listOf(CatalogExtra(name = "search", isRequired = true)))
        ),
        types = listOf(ContentType.MOVIE, ContentType.SERIES),
        resources = listOf(AddonResource(name = "catalog", types = emptyList(), idPrefixes = null))
    )

    private fun img(path: String?, size: String) = path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/$size$it" }

    suspend fun catalog(
        api: TmdbApi,
        client: LumioClient,
        addonName: String,
        catalogId: String,
        catalogName: String,
        type: String,
        extraArgs: Map<String, String>
    ): CatalogRow {
        val query = extraArgs["search"].orEmpty().trim()

        // Films et séries en même temps, par le serveur Lumio (sa clé TMDB) ; sinon avec la clé de la TV
        suspend fun hits(isSeries: Boolean): List<SearchHit> = runCatching {
            val json = client.tmdbSearch(if (isSeries) "tv" else "movie", query)
            val arr = json.optJSONArray("results")
            (0 until (arr?.length() ?: 0)).mapNotNull { i -> arr!!.optJSONObject(i)?.let(SearchHit::fromJson) }
        }.getOrElse {
            if (!LumioTmdb.available) emptyList()
            else runCatching {
                val response = if (isSeries) api.searchTv(LumioTmdb.key, query, "fr-FR") else api.searchMovies(LumioTmdb.key, query, "fr-FR")
                response.body()?.results.orEmpty().map(SearchHit::fromDto)
            }.getOrDefault(emptyList())
        }

        val found: List<Pair<SearchHit, Boolean>> = if (query.isBlank()) emptyList() else coroutineScope {
            val movies = async { hits(false) }
            val series = async { hits(true) }
            movies.await().map { it to false } + series.await().map { it to true }
        }
        val items = found
            // Sans affiche en dernier, puis par popularité
            .sortedWith(compareBy<Pair<SearchHit, Boolean>> { it.first.posterPath == null }
                .thenByDescending { (it.first.popularity ?: 0.0) + (it.first.voteCount ?: 0) / 50.0 })
            .take(30)
            .map { (r, isSeries) ->
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
            type = ContentType.MOVIE,
            rawType = type,
            items = items,
            isLoading = false,
            hasMore = false,
            supportsSkip = false,
            extraArgs = extraArgs
        )
    }
}
