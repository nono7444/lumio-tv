package com.nuvio.tv.lumio

// Lumio : source des affiches des rangées Jellyfin.
// Par défaut : affiche TMDB (via le serveur Lumio, /poster/{movie|tv}/{id}, qui redirige vers image.tmdb.org),
// même si Jellyfin a des affiches modifiées (plugin Better Posters…). Si TMDB n'a rien, l'affiche de Jellyfin reste.
// Réglage « Affiches Better Posters » (Paramètres → Lumio) : affiches btttr.cc à la place.

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object LumioPosterSource {
    private const val BETTER = "https://btttr.cc/poster-g/imdb/poster-default/%s.jpg?lang=fr"

    private val _betterPosters = MutableStateFlow(false)
    val betterPosters: StateFlow<Boolean> = _betterPosters

    @Volatile
    private var server: String? = null

    private fun prefs(context: Context) = context.getSharedPreferences("lumio", Context.MODE_PRIVATE)

    fun start(context: Context) {
        val app = context.applicationContext
        val p = prefs(app)
        _betterPosters.value = p.getBoolean("better_posters", false)
        server = p.getString("poster_server", null)?.takeIf { it.isNotBlank() }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repeat(40) {
                val addr = runCatching { LumioClient.get(app).serverAddress() }.getOrNull()
                if (!addr.isNullOrBlank()) {
                    server = addr.trimEnd('/')
                    p.edit().putString("poster_server", server).apply()
                    return@launch
                }
                delay(30_000)
            }
        }
    }

    fun setBetterPosters(context: Context, on: Boolean) {
        _betterPosters.value = on
        prefs(context.applicationContext).edit().putBoolean("better_posters", on).apply()
    }

    /**
     * Affiche à utiliser pour un titre du serveur : (affiche, affiche de secours).
     * L'affiche de secours (celle de Jellyfin) est chargée automatiquement si la première échoue.
     */
    fun resolve(isMovie: Boolean, tmdbId: Long?, imdbId: String?, jellyfinPoster: String?): Pair<String?, String?> {
        if (!LumioMode.ENABLED) return jellyfinPoster to null
        if (_betterPosters.value) {
            val imdb = imdbId?.takeIf { it.startsWith("tt") } ?: return jellyfinPoster to null
            return BETTER.format(imdb) to jellyfinPoster
        }
        val base = server
        if (base == null || tmdbId == null || tmdbId <= 0) return jellyfinPoster to null
        return "$base/poster/${if (isMovie) "movie" else "tv"}/$tmdbId" to jellyfinPoster
    }
}
