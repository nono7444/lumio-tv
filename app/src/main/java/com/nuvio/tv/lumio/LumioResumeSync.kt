package com.nuvio.tv.lumio

import android.util.Log
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.WatchProgressPreferences
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lumio : « Reprendre » synchronisé avec le téléphone.
 * Le téléphone et la TV enregistrent la position dans Jellyfin (même compte par profil). Ici, on relit
 * régulièrement les « en cours » de Jellyfin et on charge leur fiche : ServerUserStateProjection
 * recopie alors la position (si elle est plus récente) dans « Reprendre » de la TV.
 * Relit aussi la liste des bibliothèques de Jellyfin (au démarrage puis toutes les 30 min) : une bibliothèque
 * créée sur le serveur (« 4K »…) apparaît toute seule, sans passer par les réglages du serveur cachés dans Lumio.
 */
@Singleton
class LumioResumeSync @Inject constructor(
    private val repository: ServerRepository,
    private val catalog: ServerCatalog,
    private val watchProgressPreferences: WatchProgressPreferences,
    private val profileManager: ProfileManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val imported = mutableMapOf<String, String>() // protégé par syncMutex

    fun start() {
        if (!LumioMode.ENABLED) return
        // Tout de suite à chaque changement de profil ou de serveur, puis toutes les minutes
        scope.launch {
            repository.uiState
                .map { state -> state.enabledConnections.map { it.id } }
                .distinctUntilChanged()
                .collect {
                    syncMutex.withLock { imported.clear() } // autre profil ou autre serveur : tout relire
                    syncOnce()
                }
        }
        scope.launch {
            delay(INITIAL_DELAY_MS)
            var turn = 0
            while (true) {
                if (turn % LIBRARY_EVERY == 0) refreshLibraries()
                syncOnce()
                turn++
                delay(INTERVAL_MS)
            }
        }
    }

    private suspend fun refreshLibraries() {
        for (connection in repository.enabledConnections()) {
            try {
                repository.refreshLibraries(connection.id) // nouvelles bibliothèques : affichées d'office
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Bibliothèques : Jellyfin injoignable", error)
            }
        }
    }

    /** Peut aussi être appelé à l'ouverture de l'accueil. */
    fun syncNow() {
        if (LumioMode.ENABLED) scope.launch { syncOnce() }
    }

    private suspend fun syncOnce() = syncMutex.withLock {
        for (connection in repository.enabledConnections()) {
            try {
                val titles = repository.call(connection.id) { provider, session -> provider.resumeItems(session, LIMIT) }
                for (title in titles) {
                    val ref = ServerItemRef.parse(title.preview.id) ?: continue
                    // Fiche relue seulement si la reprise a bougé depuis le dernier passage
                    val stamp = title.resumeStamp
                    if (stamp != null && imported[title.preview.id] == stamp) continue
                    try {
                        catalog.details(ref) // émet detailsLoaded → position importée dans « Reprendre »
                        if (stamp != null) imported[title.preview.id] = stamp
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.w(TAG, "Fiche ${title.preview.name} illisible", error)
                    }
                }
                val listed = titles.map { it.preview.id }.toSet()
                imported.keys.retainAll { ServerItemRef.parse(it)?.connectionId != connection.id || it in listed }
                if (titles.size < LIMIT) removeFinishedElsewhere(connection.id, listed)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Reprendre : Jellyfin injoignable", error)
            }
        }
    }

    /**
     * Un titre en cours ici mais absent des « en cours » de Jellyfin a été terminé ou remis à zéro
     * sur un autre appareil : il sort de « Reprendre ». Prudent : seulement les titres nettement entamés,
     * pas terminés, et pas touchés ici depuis quelques minutes (la TV n'a peut-être pas encore prévenu Jellyfin).
     */
    private suspend fun removeFinishedElsewhere(connectionId: String, listed: Set<String>) {
        val profileId = profileManager.activeProfileId.value
        val now = System.currentTimeMillis()
        val stale = watchProgressPreferences.observeAllRawProgress(profileId).first().filter { progress ->
            ServerItemRef.parse(progress.contentId)?.connectionId == connectionId &&
                progress.contentId !in listed &&
                progress.duration > MIN_DURATION_MS &&
                progress.progressPercentage in MIN_SHARE..MAX_SHARE &&
                now - progress.lastWatched > GRACE_MS
        }
        for (progress in stale) {
            Log.d(TAG, "Retiré de Reprendre (terminé ailleurs) : ${progress.name}")
            watchProgressPreferences.removeProgress(progress.contentId, progress.season, progress.episode, profileId)
        }
    }

    private companion object {
        const val TAG = "LumioResumeSync"
        const val LIMIT = 60
        const val INITIAL_DELAY_MS = 4_000L
        const val INTERVAL_MS = 60_000L
        const val LIBRARY_EVERY = 30 // 30 × 1 min = toutes les 30 min
        const val MIN_DURATION_MS = 10 * 60_000L
        const val MIN_SHARE = 0.08f
        const val MAX_SHARE = 0.85f
        const val GRACE_MS = 3 * 60_000L
    }
}
