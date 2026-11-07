#!/usr/bin/env bash
# Essai de démarrage de Lumio TV sur l'émulateur : installe, ouvre, attend 60 s, puis cherche un plantage.
set -u
PKG=lol.wawanono.lumio.tv
adb install -r out/lumio-tv-x86.apk
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LEANBACK_LAUNCHER 1
sleep 60
adb logcat -d > logcat.txt || true
starts=$(grep -c "Start proc [0-9]*:$PKG" logcat.txt || true)
crashes=$(grep -c "FATAL EXCEPTION" logcat.txt || true)
echo "Démarrages du processus : $starts · plantages : $crashes"
if [ "$crashes" -gt 0 ] || ! adb shell pidof "$PKG" > /dev/null; then
  msg=$(grep -B2 -A40 "FATAL EXCEPTION" logcat.txt | head -60 | sed 's/%/%25/g' | awk '{printf "%s%%0A", $0}')
  echo "::error title=Plantage au démarrage::$msg"
  grep -A60 "FATAL EXCEPTION" logcat.txt | head -120
  exit 1
fi
if [ "$starts" -gt 1 ]; then
  echo "::error title=Redémarre en boucle::$starts démarrages en 60 s"
  grep -E "ActivityManager|$PKG" logcat.txt | grep -iE "kill|died|start proc|crash|anr|install" | head -60
  exit 1
fi
echo "::notice::Lumio TV tourne toujours après 60 s"
