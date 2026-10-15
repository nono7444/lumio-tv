# Lumio TV

Fork Android TV de [NuvioTV](https://github.com/NuvioMedia/NuvioTV) pour Lumio : Jellyfin, catalogues Stremio et demandes de films et séries via le serveur Lumio. Voir [NOTICE.md](NOTICE.md) pour la liste des modifications. Licence : GPL-3.0.

**Installation** : sur la TV, dans *Downloader*, ouvre `https://github.com/nono7444/lumio-tv/releases/latest/download/lumio-tv.apk`.

---

*README d'origine de NuvioTV :*

<div align="center">

  

  <p>
    A free, open-source media app for your phone, your desktop, and the TV you already own.
    <br />
    Bring your own sources. Nuvio turns them into a library with artwork, ratings, subtitles, and your place saved on every screen.
  </p>

  [Website](https://nuvio.tv) · [GitHub releases](https://github.com/NuvioMedia/NuvioTV/releases/latest) · [Support Nuvio](https://nuvio.tv/support)

</div>

## Get Nuvio TV

- [Android TV on Google Play](https://play.google.com/store/apps/details?id=com.nuvio.app)
- [Android TV APK](https://github.com/NuvioMedia/NuvioTV/releases/latest)

## Build from source

```bash
git clone https://github.com/NuvioMedia/NuvioTV.git
cd NuvioTV
./gradlew :app:assembleFullDebug
```

Nuvio TV is built with Kotlin, Jetpack Compose, TV Material 3, and Media3. Development requires Android Studio, a JDK, and the Android SDK.

## License

[GNU General Public License v3.0](./LICENSE)
