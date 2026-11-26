package com.nuvio.tv.lumio

// Lumio : copie des rangées de l'accueil gardée sur la box. Au lancement, l'accueil s'affiche tout de suite avec
// la dernière copie (affiches déjà dans le cache d'images), puis chaque rangée est remplacée dès que la version
// fraîche arrive de Jellyfin. Fichier propre à Lumio TV (pas de code NuvioTV).

import android.content.Context
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object LumioHomeSnapshot {
    private const val FILE = "lumio_home_snapshot_v1.json"
    private const val MAX_ITEMS = 40
    @Volatile private var lastSavedHash = 0

    private fun file(context: Context, profileId: Int) = File(context.filesDir, "p${profileId}_$FILE")

    fun save(context: Context, profileId: Int, rows: List<Pair<String, CatalogRow>>) {
        if (!LumioMode.ENABLED || rows.isEmpty()) return
        runCatching {
            val arr = JSONArray()
            for ((key, r) in rows) {
                if (r.items.isEmpty()) continue
                val items = JSONArray()
                r.items.take(MAX_ITEMS).forEach { m ->
                    items.put(
                        JSONObject()
                            .put("id", m.id)
                            .put("type", m.type.name)
                            .put("rawType", m.rawType)
                            .put("name", m.name)
                            .putOpt("poster", m.poster)
                            .put("shape", m.posterShape.name)
                            .putOpt("background", m.background)
                            .putOpt("logo", m.logo)
                            .putOpt("description", m.description)
                            .putOpt("releaseInfo", m.releaseInfo)
                            .putOpt("imdbRating", m.imdbRating?.toDouble())
                            .put("genres", JSONArray(m.genres))
                            .putOpt("runtime", m.runtime)
                            .putOpt("released", m.released)
                            .putOpt("imdbId", m.imdbId)
                            .putOpt("landscapePoster", m.landscapePoster)
                            .putOpt("ageRating", m.ageRating)
                            .putOpt("sourceAddonBaseUrl", m.sourceAddonBaseUrl)
                    )
                }
                arr.put(
                    JSONObject()
                        .put("key", key)
                        .put("addonId", r.addonId)
                        .put("addonName", r.addonName)
                        .put("addonBaseUrl", r.addonBaseUrl)
                        .put("catalogId", r.catalogId)
                        .put("catalogName", r.catalogName)
                        .put("type", r.type.name)
                        .put("rawType", r.rawType)
                        .put("supportsSkip", r.supportsSkip)
                        .put("skipStep", r.skipStep)
                        .put("items", items)
                )
            }
            val text = arr.toString()
            if (text.hashCode() == lastSavedHash) return
            val f = file(context, profileId)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(text)
            tmp.renameTo(f)
            lastSavedHash = text.hashCode()
        }
    }

    fun load(context: Context, profileId: Int): List<Pair<String, CatalogRow>> {
        if (!LumioMode.ENABLED) return emptyList()
        return runCatching {
            val f = file(context, profileId)
            if (!f.exists()) return emptyList()
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val itemsJson = o.getJSONArray("items")
                val items = (0 until itemsJson.length()).map { j ->
                    val m = itemsJson.getJSONObject(j)
                    val genres = m.optJSONArray("genres")
                    MetaPreview(
                        id = m.getString("id"),
                        type = runCatching { ContentType.valueOf(m.getString("type")) }.getOrDefault(ContentType.UNKNOWN),
                        rawType = m.optString("rawType"),
                        name = m.getString("name"),
                        poster = m.str("poster"),
                        posterShape = runCatching { PosterShape.valueOf(m.getString("shape")) }.getOrDefault(PosterShape.POSTER),
                        background = m.str("background"),
                        logo = m.str("logo"),
                        description = m.str("description"),
                        releaseInfo = m.str("releaseInfo"),
                        imdbRating = if (m.has("imdbRating")) m.optDouble("imdbRating").toFloat() else null,
                        genres = if (genres != null) (0 until genres.length()).map { genres.getString(it) } else emptyList(),
                        runtime = m.str("runtime"),
                        released = m.str("released"),
                        imdbId = m.str("imdbId"),
                        landscapePoster = m.str("landscapePoster"),
                        ageRating = m.str("ageRating"),
                        sourceAddonBaseUrl = m.str("sourceAddonBaseUrl")
                    )
                }
                if (items.isEmpty()) return@mapNotNull null
                o.getString("key") to CatalogRow(
                    addonId = o.getString("addonId"),
                    addonName = o.getString("addonName"),
                    addonBaseUrl = o.getString("addonBaseUrl"),
                    catalogId = o.getString("catalogId"),
                    catalogName = o.getString("catalogName"),
                    type = runCatching { ContentType.valueOf(o.getString("type")) }.getOrDefault(ContentType.UNKNOWN),
                    rawType = o.optString("rawType"),
                    items = items,
                    isLoading = false,
                    // La suite de la rangée se charge quand la version fraîche arrive
                    hasMore = false,
                    supportsSkip = o.optBoolean("supportsSkip"),
                    skipStep = o.optInt("skipStep", 100)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
}
