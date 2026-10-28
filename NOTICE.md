# Lumio TV

Lumio TV est un **fork de [NuvioTV](https://github.com/NuvioMedia/NuvioTV)** (© les auteurs de NuvioTV), distribué sous la même licence **GNU GPL v3** (voir `LICENSE`).

Base : NuvioMedia/NuvioTV, commit `17f410a` (15 octobre 2026).

## Modifications apportées par Lumio
- Nom, icônes, bannières et logos remplacés (Lumio TV). Les marques et logos Nuvio ne sont pas utilisés.
- Identifiant d'application `lol.wawanono.lumio.tv`, mises à jour depuis les Releases de `nono7444/lumio-tv`.
- Services en ligne de Nuvio (compte, soutiens, rapports) non configurés.
- Ajout des demandes de films et séries via le serveur Lumio (façon Seerr), avec le compte Jellyfin.
- Notes de la famille (1 à 10 étoiles) sur les fiches, partagées avec l'appli Lumio du téléphone.
- Relance de la recherche d'une demande en cours depuis la fiche.
- Bibliothèque : uniquement les bibliothèques du serveur Jellyfin.
- Lecture sur l'adresse locale de Jellyfin quand la TV est sur le réseau de la maison.
- Paramètres réduits à l'état des serveurs, la mise à jour, le thème et la personnalisation de l'accueil.
- Recherche et fiches via TMDB (clé fournie par le serveur Lumio), affiches TMDB (ou Better Posters au choix dans Paramètres → Lumio), notes de la famille sur les affiches.
- Addons Nuvio / Stremio : seulement leurs catalogues (rangées de l'accueil) ; la lecture vient uniquement de Jellyfin.
- Choix de la qualité dans le lecteur, bibliothèque en tuiles avec les sagas de Lumio, Live TV, connexion par QR code, décorations de saison, fond de l'accueil (Halloween, Noël), pas d'écran de démarrage, nom « lumio » dans le style de Nuvio (lettres reprises du logo Nuvio), profils de la famille (un compte Jellyfin chacun), passer l'intro via les segments Jellyfin, état des disques, liste « À voir », accueil épuré (Reprendre + Nouveautés par bibliothèque) et rangée de saison « Halloween » / « Noël ».
- Recherche simplifiée : une seule rangée d'affiches (films et séries TMDB mélangés), sans « Découvrir » ni catalogues ; la fiche propose Lire ou Demander.
- Sous-titres coupés au début de chaque lecture (on les active soi-même dans le lecteur).
- « Mon rang » : nom du compte et écusson du rang (dessiné pour Lumio) en haut de l'accueil, écran du classement avec points et progression.
- Fiche et demandes (admin) : bouton « DL » pour télécharger avec Lumio Secour (aussi sur une demande bloquée ou en échec) ; progression « Lumio Secour » dans les demandes quand Radarr / Sonarr ne répondent pas ou restent bloqués.
- « Plateformes » en rangée sur l'accueil (Netflix, Disney+, Prime Video, Apple TV+, Max, Canal+) : vignettes noires en verre avec logos blancs, sorties mélangées (sur le serveur ou à demander) par date de sortie, la plus récente ou la plus ancienne d'abord.
- Pas de marque « vu » sur les affiches (elle reste dans la liste des épisodes).
- Optimisations pour les box TV : images du serveur à la taille de l'écran, pas d'agrandissement du fond au focus par défaut, décorations fixes (accueil seulement), notes chargées une seule fois.

Le code source complet est disponible dans ce dépôt, conformément à la GPL v3.
