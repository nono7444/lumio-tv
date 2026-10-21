package com.nuvio.tv.lumio

// Lumio : la clé TMDB n'est pas dans l'appli — elle vient du serveur Lumio (/api/config/tmdb),
// comme pour les téléphones de la famille. Elle sert aux fiches (images nettes, résumés en français),
// à la recherche et aux collections. Gardée en mémoire et sur la TV pour les lancements suivants.

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import org.json.JSONObject

object LumioTmdb {
    @Volatile
    private var cached: String = ""

    /** Clé TMDB (v3) ou jeton de lecture (v4), vide tant qu'elle n'est pas reçue du serveur. */
    val key: String get() = cached

    val available: Boolean get() = cached.isNotBlank()

    private fun prefs(context: Context) = context.getSharedPreferences("lumio", Context.MODE_PRIVATE)

    /** Au démarrage : clé gardée sur la TV, puis mise à jour depuis le serveur (réessaie tant qu'il ne répond pas). */
    fun start(context: Context) {
        val app = context.applicationContext
        prefs(app).getString("tmdb_key", null)?.takeIf { it.isNotBlank() }?.let { cached = it }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repeat(40) {
                val ok = runCatching {
                    val k = LumioClient.get(app).tmdbKey()
                    if (!k.isNullOrBlank()) {
                        cached = k
                        prefs(app).edit().putString("tmdb_key", k).apply()
                    }
                    true
                }.getOrDefault(false)
                if (ok) return@launch
                delay(30_000)
            }
        }
    }

    /**
     * Requêtes TMDB : ajoute la clé. Jeton v4 (long) → en-tête « Authorization: Bearer »,
     * clé v3 → paramètre api_key (remplace celui, vide, mis par l'appli).
     */
    val interceptor = Interceptor { chain ->
        val k = cached
        val req = chain.request()
        if (k.isBlank()) return@Interceptor chain.proceed(req)
        val url = req.url.newBuilder().removeAllQueryParameters("api_key")
        val builder = req.newBuilder()
        if (k.length > 40) builder.header("Authorization", "Bearer $k")
        else url.addQueryParameter("api_key", k)
        chain.proceed(builder.url(url.build()).build())
    }
}

internal fun parseTmdbKey(json: String): String? =
    runCatching { JSONObject(json).optString("key") }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" }

