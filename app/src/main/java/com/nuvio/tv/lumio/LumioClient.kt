package com.nuvio.tv.lumio

// Lumio : client du serveur Lumio (demandes façon Seerr), authentifié avec le jeton Jellyfin
// de la connexion déjà configurée dans l'appli. Aucune clé Radarr / Sonarr sur la TV.

import android.content.Context
import com.nuvio.tv.data.mediaserver.ServerRepository
import com.nuvio.tv.data.mediaserver.ServerSession
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LumioEntryPoint {
    fun serverRepository(): ServerRepository
}

/** Valeurs identiques à Seerr. */
object MediaStatus {
    const val UNKNOWN = 1
    const val PENDING = 2
    const val PROCESSING = 3
    const val PARTIALLY_AVAILABLE = 4
    const val AVAILABLE = 5
}

object RequestStatus {
    const val PENDING = 1
    const val APPROVED = 2
    const val DECLINED = 3
    const val FAILED = 4
    const val COMPLETED = 5
}

data class LumioSeason(val number: Int, val status: Int)
/** Suivi Radarr / Sonarr d'une demande validée : searching, downloading, importing, warning. */
data class LumioDownload(val state: String, val progress: Int?, val message: String?, val count: Int)
data class LumioRequest(
    val id: String,
    val status: Int,
    val requestedBy: String,
    val seasons: List<Int>,
    val mine: Boolean,
    val download: LumioDownload? = null
)
data class LumioMedia(
    val status: Int,
    val tmdbId: Int?,
    val tvdbId: Int?,
    val seasons: List<LumioSeason>,
    val requests: List<LumioRequest>
)
/** Une demande de la liste « Demandes ». */
data class LumioListedRequest(
    val id: String,
    val type: String,
    val title: String,
    val year: String?,
    val poster: String?,
    val status: Int,
    val requestedBy: String,
    val mine: Boolean,
    val seasons: List<Int>,
    val download: LumioDownload?,
    val error: String?,
    val reason: String?
)
data class LumioFolder(val path: String, val freeSpace: Long?)
/** Un titre de la liste « À voir ». */
data class LumioWatchItem(
    val type: String,
    val tmdbId: Int,
    val title: String,
    val year: String?,
    val poster: String?,
    val backdrop: String?,
    val status: Int
)
data class LumioMe(val admin: Boolean, val autoApprove: Boolean, val quotaRemaining: Int?, val quotaLimit: Int, val quotaDays: Int)

data class LumioVote(val name: String, val stars: Int, val comment: String?, val mine: Boolean)
data class LumioRating(val avg: Double?, val count: Int, val mine: Int?, val votes: List<LumioVote>)

class LumioException(message: String) : Exception(message)

class LumioClient(private val context: Context) {
    private val prefs = context.getSharedPreferences("lumio", Context.MODE_PRIVATE)
    private val repository: ServerRepository by lazy {
        EntryPointAccessors.fromApplication(context.applicationContext, LumioEntryPoint::class.java).serverRepository()
    }

    /** Session Jellyfin active (jeton + adresse), ou null si aucun serveur Jellyfin n'est connecté. */
    private fun jellyfin(): ServerSession? {
        val conn = repository.enabledConnections().firstOrNull { it.providerId == "jellyfin" } ?: return null
        return repository.session(conn.id)
    }

    var serverUrlOverride: String?
        get() = prefs.getString("server_url", null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString("server_url", value?.trim()?.trimEnd('/')).apply()
        }

    /** Adresses candidates du serveur Lumio, devinées depuis celle de Jellyfin. */
    private fun candidates(jellyfinAddress: String): List<String> {
        val out = mutableListOf<String>()
        serverUrlOverride?.let { out += it }
        runCatching {
            val uri = URI(jellyfinAddress.trim())
            val host = uri.host ?: return@runCatching
            val isIp = host.matches(Regex("""\d+\.\d+\.\d+\.\d+""")) || host == "localhost"
            if (isIp) out += "http://$host:3456"
            else {
                val parts = host.split('.')
                if (parts.size >= 3) out += "https://lumio." + parts.drop(1).joinToString(".")
                out += "https://lumio.$host"
                out += "${uri.scheme ?: "http"}://$host:3456"
            }
        }
        return out.distinct()
    }

    @Volatile
    private var resolvedBase: String? = null

    private suspend fun base(session: ServerSession): String {
        resolvedBase?.let { return it }
        for (c in candidates(session.connection.address)) {
            val ok = runCatching {
                val json = JSONObject(http("GET", "$c/health", null, null))
                json.optJSONArray("features")?.let { arr -> (0 until arr.length()).any { arr.optString(it) == "requests" } } == true
            }.getOrDefault(false)
            if (ok) {
                resolvedBase = c
                return c
            }
        }
        throw LumioException("Serveur Lumio introuvable (essayé : ${candidates(session.connection.address).joinToString()})")
    }

    private fun http(method: String, url: String, token: String?, body: JSONObject?): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 8000
        conn.readTimeout = 25000
        conn.setRequestProperty("Accept", "application/json")
        token?.let { conn.setRequestProperty("X-Jellyfin-Token", it) }
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(text).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP $code"
            throw LumioException(msg)
        }
        return text
    }

    private suspend fun call(method: String, path: String, body: JSONObject? = null): String = withContext(Dispatchers.IO) {
        val session = jellyfin() ?: throw LumioException("Connecte d'abord ton serveur Jellyfin (Paramètres → Serveurs média).")
        http(method, base(session) + path, session.token, body)
    }

    /** État public des serveurs (Lumio, Jellyfin, Radarr, Sonarr) : en ligne, disponibilité sur 24 h. */
    suspend fun publicStatus(): JSONObject = JSONObject(call("GET", "/status/public"))

    /** Sagas de Lumio (collections TMDB des films présents dans Jellyfin), calculées par le serveur. */
    suspend fun collections(): JSONObject = JSONObject(call("GET", "/api/collections"))

    /** Sources Live TV du compte (ajoutées sur le téléphone). */
    suspend fun liveSources(): JSONObject = JSONObject(call("GET", "/api/live/sources"))

    /** Moyennes des notes de la famille (clés « movie:123 », « tv:123 » et « imdb:tt… »). */
    suspend fun ratingSummary(): JSONObject = JSONObject(call("GET", "/api/ratings/summary"))

    /** Liste « À voir » de l'utilisateur (la même que sur son téléphone). */
    suspend fun watchlist(): List<LumioWatchItem> {
        val arr = JSONObject(call("GET", "/api/watchlist")).optJSONArray("items")
        return arr.objects().map { o ->
            LumioWatchItem(
                type = o.optString("type", "movie"),
                tmdbId = o.optInt("tmdbId"),
                title = o.optString("title"),
                year = o.optString("year").takeIf { it.isNotBlank() && it != "null" },
                poster = o.optString("poster").takeIf { it.startsWith("http") },
                backdrop = o.optString("backdrop").takeIf { it.startsWith("http") },
                status = o.optInt("status", MediaStatus.UNKNOWN)
            )
        }
    }

    suspend fun watchlistAdd(type: String, tmdbId: Int, title: String, year: String?, poster: String?, backdrop: String?) {
        call("POST", "/api/watchlist", JSONObject().apply {
            put("type", type); put("tmdbId", tmdbId); put("title", title)
            year?.let { put("year", it) }
            poster?.takeIf { it.startsWith("https://") }?.let { put("poster", it) }
            backdrop?.takeIf { it.startsWith("https://") }?.let { put("backdrop", it) }
        })
    }

    suspend fun watchlistRemove(type: String, tmdbId: Int) {
        call("DELETE", "/api/watchlist?type=$type&tmdbId=$tmdbId")
    }

    /** Recherche TMDB faite par le serveur Lumio (la clé reste sur le serveur). */
    suspend fun tmdbSearch(type: String, query: String): JSONObject =
        JSONObject(call("GET", "/api/tmdb/search?type=$type&q=" + java.net.URLEncoder.encode(query, "UTF-8")))

    /** Adresse du serveur Lumio + jeton Jellyfin, pour passer les requêtes TMDB par le serveur. */
    suspend fun proxyInfo(): Pair<String, String>? = runCatching {
        withContext(Dispatchers.IO) { jellyfin()?.let { s -> base(s) to s.token } }
    }.getOrNull()

    /** Clé TMDB partagée par le serveur Lumio (null si l'admin ne l'a pas encore confiée au serveur). */
    suspend fun tmdbKey(): String? = parseTmdbKey(call("GET", "/api/config/tmdb"))

    /**
     * Segments d'un titre Jellyfin (intro, récap, générique…) détectés par l'extension « Intro Skipper » :
     * (type, début en s, fin en s). Liste vide si Jellyfin n'en a pas.
     */
    suspend fun mediaSegments(itemId: String): List<Triple<String, Double, Double>> = withContext(Dispatchers.IO) {
        val s = jellyfin() ?: return@withContext emptyList()
        runCatching {
            val base = s.connection.address.trimEnd('/')
            val conn = URL("$base/MediaSegments/${java.net.URLEncoder.encode(itemId, "UTF-8")}").openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("X-Emby-Token", s.token)
            if (conn.responseCode !in 200..299) return@runCatching emptyList()
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            json.optJSONArray("Items").objects().mapNotNull { o ->
                val start = o.optLong("StartTicks", -1) / 10_000_000.0
                val end = o.optLong("EndTicks", -1) / 10_000_000.0
                if (start < 0 || end <= start) null else Triple(o.optString("Type"), start, end)
            }
        }.getOrDefault(emptyList())
    }

    /** Adresse du serveur Lumio trouvée (pour l'afficher). */
    suspend fun serverAddress(): String? = runCatching { withContext(Dispatchers.IO) { jellyfin()?.let { base(it) } } }.getOrNull()

    suspend fun isAvailable(): Boolean = runCatching { withContext(Dispatchers.IO) { jellyfin()?.let { base(it) } != null } }.getOrDefault(false)

    suspend fun media(type: String, tmdbId: Int?, imdbId: String?, seasons: List<Int>): LumioMedia {
        val q = buildString {
            append("/api/media?type=").append(type)
            tmdbId?.let { append("&tmdbId=").append(it) }
            imdbId?.let { append("&imdbId=").append(it) }
            if (seasons.isNotEmpty()) append("&seasons=").append(seasons.joinToString(","))
        }
        val json = JSONObject(call("GET", q))
        val me = jellyfin()?.connection?.remoteUserId
        return LumioMedia(
            status = json.optInt("status", MediaStatus.UNKNOWN),
            tmdbId = json.optInt("tmdbId").takeIf { it > 0 },
            tvdbId = json.optInt("tvdbId").takeIf { it > 0 },
            seasons = json.optJSONArray("seasons").objects().map { LumioSeason(it.optInt("seasonNumber"), it.optInt("status")) },
            requests = json.optJSONArray("requests").objects().map {
                val by = it.optJSONObject("requestedBy")
                LumioRequest(
                    id = it.optString("id"),
                    status = it.optInt("status"),
                    requestedBy = by?.optString("name").orEmpty(),
                    seasons = it.optJSONArray("seasons").ints(),
                    mine = by?.optString("id") == me,
                    download = it.optJSONObject("download")?.toDownload()
                )
            }
        )
    }

    suspend fun me(): LumioMe {
        val json = JSONObject(call("GET", "/api/me"))
        val perm = json.optJSONObject("permissions") ?: JSONObject()
        val quota = json.optJSONObject("quota")
        val movie = quota?.optJSONObject("movie")
        return LumioMe(
            admin = json.optBoolean("admin"),
            autoApprove = perm.optBoolean("autoApprove", true),
            quotaRemaining = movie?.takeIf { !it.isNull("remaining") }?.optInt("remaining"),
            quotaLimit = movie?.optInt("limit") ?: 0,
            quotaDays = movie?.optInt("days") ?: 7
        )
    }

    suspend fun folders(type: String): Pair<List<LumioFolder>, String?> {
        val json = JSONObject(call("GET", "/api/arr/options?type=$type"))
        val list = json.optJSONArray("folders").objects().map {
            LumioFolder(it.optString("path"), it.optLong("freeSpace").takeIf { v -> v > 0 })
        }
        return list to json.optString("default").takeIf { it.isNotBlank() && it != "null" }
    }

    /** Envoie la demande ; renvoie le statut de la demande créée. */
    suspend fun request(
        type: String,
        tmdbId: Int?,
        imdbId: String?,
        tvdbId: Int?,
        title: String,
        year: String?,
        poster: String?,
        backdrop: String?,
        seasons: List<Int>,
        folder: String?
    ): Int {
        val body = JSONObject().apply {
            put("type", type)
            tmdbId?.let { put("tmdbId", it) }
            imdbId?.let { put("imdbId", it) }
            tvdbId?.let { put("tvdbId", it) }
            put("title", title)
            year?.let { put("year", it) }
            poster?.takeIf { it.startsWith("https://") }?.let { put("poster", it) }
            backdrop?.takeIf { it.startsWith("https://") }?.let { put("backdrop", it) }
            if (seasons.isNotEmpty()) put("seasons", JSONArray(seasons))
            folder?.let { put("rootFolderPath", it) }
        }
        return JSONObject(call("POST", "/api/requests", body)).optInt("status", RequestStatus.PENDING)
    }

    /** filter : all, pending, processing, available, declined, failed */
    suspend fun list(filter: String = "all"): List<LumioListedRequest> {
        val json = JSONObject(call("GET", "/api/requests?filter=$filter&take=60"))
        val me = jellyfin()?.connection?.remoteUserId
        return json.optJSONArray("results").objects().map {
            val by = it.optJSONObject("requestedBy")
            LumioListedRequest(
                id = it.optString("id"),
                type = it.optString("type", "movie"),
                title = it.optString("title"),
                year = it.optString("year").takeIf { y -> y.isNotBlank() && y != "null" },
                poster = it.optString("poster").takeIf { p -> p.startsWith("http") },
                status = it.optInt("status"),
                requestedBy = by?.optString("name").orEmpty(),
                mine = by?.optString("id") == me,
                seasons = it.optJSONArray("seasons").ints(),
                download = it.optJSONObject("download")?.toDownload(),
                error = it.optString("error").takeIf { e -> e.isNotBlank() && e != "null" },
                reason = it.optString("reason").takeIf { e -> e.isNotBlank() && e != "null" }
            )
        }
    }

    suspend fun retry(id: String) { call("POST", "/api/requests/$id/retry", JSONObject()) }

    suspend fun approve(id: String) { call("POST", "/api/requests/$id/approve", JSONObject()) }
    suspend fun decline(id: String) { call("POST", "/api/requests/$id/decline", JSONObject()) }
    suspend fun cancel(id: String) { call("DELETE", "/api/requests/$id") }

    /** Relance la recherche Radarr / Sonarr d'une demande en cours (recherche ou partielle). */
    suspend fun recheck(id: String): String {
        val json = JSONObject(call("POST", "/api/requests/$id/search", JSONObject()))
        return json.optString("message").takeIf { it.isNotBlank() } ?: "Recherche relancée"
    }

    // ——— Notes de la famille (1 à 10 étoiles) ———

    suspend fun rating(type: String, tmdbId: Int): LumioRating {
        val json = JSONObject(call("GET", "/api/ratings?type=$type&tmdbId=$tmdbId"))
        val mine = json.optJSONObject("mine")
        return LumioRating(
            avg = if (json.isNull("avg")) null else json.optDouble("avg"),
            count = json.optInt("count"),
            mine = mine?.optInt("stars")?.takeIf { it > 0 },
            votes = json.optJSONArray("list").objects().map { v ->
                LumioVote(name = v.optString("name"), stars = v.optInt("stars"), comment = v.optString("comment").takeIf { it.isNotBlank() }, mine = v.optBoolean("mine"))
            }
        )
    }

    suspend fun rate(type: String, tmdbId: Int, stars: Int, title: String?, poster: String?, imdb: String? = null) {
        val body = JSONObject().put("type", type).put("tmdbId", tmdbId).put("stars", stars)
        imdb?.takeIf { it.startsWith("tt") }?.let { body.put("imdb", it) }
        title?.let { body.put("title", it) }
        poster?.takeIf { it.startsWith("https://") }?.let { body.put("poster", it) }
        call("POST", "/api/ratings", body)
    }

    companion object {
        @Volatile
        private var instance: LumioClient? = null
        fun get(context: Context): LumioClient =
            instance ?: synchronized(this) { instance ?: LumioClient(context.applicationContext).also { instance = it } }
    }
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.ints(): List<Int> =
    if (this == null) emptyList() else (0 until length()).map { optInt(it) }

private fun JSONObject.toDownload(): LumioDownload = LumioDownload(
    state = optString("state", "searching"),
    progress = if (isNull("progress") || !has("progress")) null else optInt("progress"),
    message = optString("message").takeIf { m -> m.isNotBlank() && m != "null" },
    count = optInt("count", 1)
)
