package com.nuvio.tv.lumio

// Lumio : à la maison, la vidéo est lue sur l'adresse locale de Jellyfin (réseau de la maison)
// au lieu de passer par Internet (tunnel Cloudflare), qui fait démarrer / s'arrêter les films lourds.
// L'adresse locale vient du serveur Lumio (/tv/setup → localJellyfinUrl), sinon de l'adresse habituelle.
// On vérifie toujours que c'est bien le même serveur Jellyfin (même identifiant) avant de l'utiliser.

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object LumioLan {
    private const val DEFAULT_LOCAL = "http://192.168.1.71:8096"
    private const val CACHE_MS = 5 * 60_000L

    private data class Cached(val remote: String, val at: Long, val local: String?)

    @Volatile
    private var cache: Cached? = null

    private fun get(url: String, timeoutMs: Int): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("Accept", "application/json")
        try {
            if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun serverId(base: String, timeoutMs: Int): String? =
        get("${base.trimEnd('/')}/System/Info/Public", timeoutMs)
            ?.let { runCatching { JSONObject(it).optString("Id") }.getOrNull() }
            ?.takeIf { it.isNotBlank() }

    /** Adresse du serveur Lumio devinée depuis celle de Jellyfin (jellyfin.domaine → lumio.domaine). */
    private fun lumioCandidates(remote: String): List<String> = runCatching {
        val host = URI(remote).host ?: return@runCatching emptyList()
        val parts = host.split('.')
        buildList {
            if (parts.size >= 3) add("https://lumio." + parts.drop(1).joinToString("."))
            add("https://lumio.$host")
        }
    }.getOrDefault(emptyList())

    private fun isLocal(address: String): Boolean =
        Regex("""^https?://(192\.168\.|10\.|172\.(1[6-9]|2\d|3[01])\.|127\.|localhost)""").containsMatchIn(address)

    /**
     * Adresse locale à utiliser pour la lecture, ou null (on garde l'adresse habituelle).
     * [remoteServerId] : identifiant du serveur Jellyfin connu, pour ne jamais lire sur un autre serveur.
     */
    suspend fun localBase(remoteAddress: String, remoteServerId: String?): String? = withContext(Dispatchers.IO) {
        val remote = remoteAddress.trimEnd('/')
        if (isLocal(remote)) return@withContext null
        cache?.let { if (it.remote == remote && System.currentTimeMillis() - it.at < CACHE_MS) return@withContext it.local }

        val candidates = mutableListOf<String>()
        for (lumio in lumioCandidates(remote)) {
            val setup = get("$lumio/tv/setup", 2500) ?: continue
            runCatching { JSONObject(setup).optString("localJellyfinUrl") }.getOrNull()
                ?.takeIf { it.startsWith("http") }
                ?.let { candidates += it.trimEnd('/') }
            break
        }
        candidates += DEFAULT_LOCAL

        val expected = remoteServerId?.takeIf { it.isNotBlank() } ?: serverId(remote, 4000)
        var found: String? = null
        for (c in candidates.distinct()) {
            val id = serverId(c, 1500) ?: continue
            if (expected == null || id.equals(expected, ignoreCase = true)) {
                found = c
                break
            }
        }
        cache = Cached(remote, System.currentTimeMillis(), found)
        found
    }
}
