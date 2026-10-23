package com.nuvio.tv.ui.screens.settings

internal enum class SettingsRailGroup {
    YOU,
    LOOK,
    WATCH,
    SERVICES,
    SYSTEM
}

internal enum class SettingsCategory(val group: SettingsRailGroup) {
    LUMIO(SettingsRailGroup.YOU),
    ACCOUNT(SettingsRailGroup.YOU),
    PROFILES(SettingsRailGroup.YOU),
    APPEARANCE(SettingsRailGroup.LOOK),
    LAYOUT(SettingsRailGroup.LOOK),
    CONTENT_DISCOVERY(SettingsRailGroup.WATCH),
    PLAYBACK(SettingsRailGroup.WATCH),
    INTEGRATION(SettingsRailGroup.SERVICES),
    TRACKING(SettingsRailGroup.SERVICES),
    ADVANCED(SettingsRailGroup.SYSTEM),
    ABOUT(SettingsRailGroup.SYSTEM),
    DEBUG(SettingsRailGroup.SYSTEM),
    EXPERIENCE(SettingsRailGroup.SYSTEM)
}

internal fun visibleSettingsCategories(
    isPrimaryProfile: Boolean,
    isEssentialMode: Boolean,
    isDebugBuild: Boolean
): List<SettingsCategory> = SettingsCategory.entries.filter { category ->
    // Lumio : paramètres épurés — état des serveurs + mise à jour, thème et personnalisation de l'accueil
    if (com.nuvio.tv.lumio.LumioMode.ENABLED) {
        // + Profils (famille : un profil par personne, chacun avec son compte Jellyfin), géré depuis le profil principal
        return@filter category == SettingsCategory.LUMIO || category == SettingsCategory.APPEARANCE || category == SettingsCategory.LAYOUT ||
            (category == SettingsCategory.PROFILES && isPrimaryProfile)
    }
    when (category) {
        SettingsCategory.LUMIO -> false
        // Lumio : pas de compte Nuvio (service en ligne de Nuvio), la connexion se fait au serveur Jellyfin
        SettingsCategory.ACCOUNT -> false
        SettingsCategory.PROFILES -> isPrimaryProfile
        SettingsCategory.DEBUG -> isDebugBuild && !isEssentialMode
        SettingsCategory.EXPERIENCE -> false
        else -> true
    }
}

internal fun List<SettingsCategory>.startsNewGroup(index: Int): Boolean =
    index > 0 && this[index - 1].group != this[index].group
