package com.nuvio.tv.lumio

/**
 * « Mode Lumio » : l'appli TV fonctionne comme l'appli iPhone, tout depuis Jellyfin.
 * - accueil : uniquement les rangées du serveur Jellyfin (les addons ne servent plus qu'aux fiches) ;
 * - lecture directe de la source Jellyfin, sans écran de choix des sources ;
 * - gestion des addons masquée dans les paramètres.
 */
object LumioMode {
    const val ENABLED = true

    /** Addons : seulement leurs catalogues, sauf Cinemeta (installé par défaut, sert aux fiches). */
    fun catalogAddons(installed: List<com.nuvio.tv.domain.model.Addon>): List<com.nuvio.tv.domain.model.Addon> =
        installed.map { addon ->
            if (addon.baseUrl.contains("cinemeta.strem.io", ignoreCase = true)) addon.copy(catalogs = emptyList()) else addon
        }

    /** Sans la bibliothèque « Collections » de Jellyfin : Lumio a ses propres sagas (Bibliothèque → Sagas). */
    fun withoutCollections(addons: List<com.nuvio.tv.domain.model.Addon>): List<com.nuvio.tv.domain.model.Addon> =
        addons.map { addon ->
            addon.copy(catalogs = addon.catalogs.filter { it.apiType != com.nuvio.tv.data.mediaserver.ServerMediaKind.COLLECTION.contentType })
        }
}
