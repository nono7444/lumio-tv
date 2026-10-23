package com.nuvio.tv.lumio

// Lumio : liste « À voir » (la même que sur le téléphone, gardée sur le serveur Lumio).
// Les titres présents sont partagés entre la fiche (bouton signet) et l'écran « À voir ».

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object LumioWatchlist {
    private val _items = MutableStateFlow<List<LumioWatchItem>?>(null)
    val items: StateFlow<List<LumioWatchItem>?> = _items

    private fun key(type: String, id: Int) = "$type:$id"

    fun contains(type: String, tmdbId: Int): Boolean = _items.value?.any { key(it.type, it.tmdbId) == key(type, tmdbId) } == true

    suspend fun load(context: Context): List<LumioWatchItem> {
        val list = LumioClient.get(context).watchlist()
        _items.value = list
        return list
    }

    suspend fun add(context: Context, item: LumioWatchItem) {
        _items.value = listOf(item) + (_items.value ?: emptyList()).filterNot { key(it.type, it.tmdbId) == key(item.type, item.tmdbId) }
        LumioClient.get(context).watchlistAdd(item.type, item.tmdbId, item.title, item.year, item.poster, item.backdrop)
    }

    suspend fun remove(context: Context, type: String, tmdbId: Int) {
        _items.value = (_items.value ?: emptyList()).filterNot { key(it.type, it.tmdbId) == key(type, tmdbId) }
        LumioClient.get(context).watchlistRemove(type, tmdbId)
    }
}
