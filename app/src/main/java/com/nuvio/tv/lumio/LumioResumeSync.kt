package com.nuvio.tv.lumio

import android.util.Log
import com.nuvio.tv.data.mediaserver.ServerCatalog
import com.nuvio.tv.data.mediaserver.ServerItemRef
import com.nuvio.tv.data.mediaserver.ServerRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Lumio : « Reprendre » synchronisé avec le téléphone.
 * Le téléphone et la TV enregistrent la position dans Jellyfin (même compte par profil). Ici, on relit
 * régulièrement les « en cours » de Jellyfin et on charge leur fiche : ServerUserStateProjection
 * recopie alors la position (si elle est plus récente) dans « Reprendre » de la TV.
 */
@Singleton
class LumioResumeSync @Inject constructor(
    private val repository: ServerRepository,
    private val catalog: ServerCatalog
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        if (!LumioMode.ENABLED) return
        // Tout de suite à chaque changement de profil ou de serveur, puis toutes les 3 minutes
        scope.launch {
            repository.uiState
                .map { state -> state.enabledConnections.map { it.id } }
                .distinctUntilChanged()
                .collect { syncOnce() }
        }
        scope.launch {
            delay(INITIAL_DELAY_MS)
            while (true) {
                syncOnce()
                delay(INTERVAL_MS)
            }
        }
    }

    /** Peut aussi être appelé à l'ouverture de l'accueil. */
    fun syncNow() {
        if (LumioMode.ENABLED) scope.launch { syncOnce() }
    }

    private suspend fun syncOnce() {
        for (connection in repository.enabledConnections()) {
            try {
                val titles = repository.call(connection.id) { provider, session -> provider.resumeItems(session, LIMIT) }
                for (title in titles) {
                    val ref = ServerItemRef.parse(title.preview.id) ?: continue
                    try {
                        catalog.details(ref) // émet detailsLoaded → position importée dans « Reprendre »
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.w(TAG, "Fiche ${title.preview.name} illisible", error)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Reprendre : Jellyfin injoignable", error)
            }
        }
    }

    private companion object {
        const val TAG = "LumioResumeSync"
        const val LIMIT = 12
        const val INITIAL_DELAY_MS = 4_000L
        const val INTERVAL_MS = 3 * 60_000L
    }
}
