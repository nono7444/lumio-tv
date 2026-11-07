#!/usr/bin/env bash
# Essai de démarrage de Lumio TV sur l'émulateur : installe, ouvre, attend 90 s, puis cherche un plantage ou une boucle.
set -u
PKG=lol.wawanono.lumio.tv
adb install -r out/lumio-tv-x86.apk
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LEANBACK_LAUNCHER 1
sleep 90
adb logcat -d -v threadtime > logcat.txt || true
echo "── Démarrages / arrêts de Lumio TV"
grep -E "Start proc [0-9]+:$PKG|Process $PKG .*has died|Killing [0-9]+:$PKG|Force finishing activity $PKG|ANR in $PKG|am_crash.*$PKG|Displayed $PKG" logcat.txt | head -40
starts=$(grep -cE "Start proc [0-9]+:$PKG/" logcat.txt || true)
# Plantages de NOTRE appli seulement (l'émulateur TV a ses propres soucis de systemui)
awk -v pkg="$PKG" '/FATAL EXCEPTION/{buf=$0; getline; buf=buf"\n"$0; if (index($0, "Process: " pkg)) {print buf; for(i=0;i<60;i++){if(!getline) break; print}} }' logcat.txt > crash.txt
echo "Démarrages du processus : $starts · plantages : $(grep -c 'FATAL EXCEPTION' crash.txt)"
if [ -s crash.txt ]; then
  echo "::error title=Plantage de Lumio TV::$(head -40 crash.txt | sed 's/%/%25/g' | awk '{printf "%s%%0A", $0}')"
  cat crash.txt | head -150
  exit 1
fi
if [ "$starts" -gt 1 ]; then
  echo "::error title=Redémarre en boucle::$starts démarrages en 90 s"
  # Ce qui se passe autour de chaque redémarrage
  grep -nE "Start proc [0-9]+:$PKG/" logcat.txt | head -5
  pid=$(grep -oE "Start proc [0-9]+:$PKG/" logcat.txt | head -1 | grep -oE "[0-9]+")
  echo "── Dernières lignes du premier processus ($pid)"
  grep -E "^\S+ \S+ +$pid " logcat.txt | grep -E " [EW] " | tail -80
  exit 1
fi
if ! adb shell pidof "$PKG" > /dev/null; then
  echo "::error title=Lumio TV s'est arrêtée::plus de processus après 90 s"
  grep -E " E " logcat.txt | grep -i "lumio\|nuvio" | tail -60
  exit 1
fi
echo "::notice::Lumio TV tourne toujours après 90 s (un seul démarrage)"
