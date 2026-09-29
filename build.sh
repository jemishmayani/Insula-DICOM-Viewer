#!/usr/bin/env bash
# Insula DICOM Viewer
# Copyright (C) 2026 Jemish Mayani
# SPDX-License-Identifier: GPL-3.0-or-later
# Builds Insula DICOM Viewer without Gradle, using Ubuntu's Android packages:
#   sudo apt install openjdk-21-jdk-headless android-sdk-platform-23 aapt dalvik-exchange apksigner zipalign
#
# Signing: set INSULA_KEYSTORE, INSULA_KEYSTORE_PASS and INSULA_KEY_ALIAS to sign with your release key.
# Without them, a local debug key (debug.jks) is created on first run and reused afterwards.
# Android only installs an update over an existing install if both are signed with the same key.
set -euo pipefail
cd "$(dirname "$0")"

AJ=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
KS=${INSULA_KEYSTORE:-debug.jks}
PASS=${INSULA_KEYSTORE_PASS:-debug-only-password}
ALIAS=${INSULA_KEY_ALIAS:-insula}
VERSION=$(grep -o 'versionName="[^"]*"' AndroidManifest.xml | cut -d'"' -f2)

for tool in javac aapt dalvik-exchange zipalign apksigner keytool; do
  command -v "$tool" >/dev/null || { echo "Missing $tool. See the install line at the top of this script."; exit 1; }
done
[ -f "$AJ" ] || { echo "Missing $AJ (package android-sdk-platform-23)."; exit 1; }

rm -rf gen obj out && mkdir -p gen obj out
echo "Generating resources..."
aapt package -f -m -J gen -M AndroidManifest.xml -S res -I "$AJ"

echo "Compiling Java..."
if ! javac -nowarn -Xlint:none -source 8 -target 8 -encoding UTF-8 -bootclasspath "$AJ" -d obj \
     $(find src gen -name '*.java') > out/javac.log 2>&1; then
  grep -B1 -A3 "error" out/javac.log || cat out/javac.log
  echo "COMPILE FAILED"; exit 1
fi

echo "Converting to DEX..."
dalvik-exchange --dex --min-sdk-version=24 --output=out/classes.dex obj

echo "Packaging..."
aapt package -f -M AndroidManifest.xml -S res -I "$AJ" -F out/unaligned.apk -0 arsc
(cd out && aapt add unaligned.apk classes.dex >/dev/null)
zipalign -f -p 4 out/unaligned.apk out/aligned.apk

if [ ! -f "$KS" ]; then
  echo "No keystore at $KS; creating a local debug key. Keep it to install future builds as updates."
  keytool -genkeypair -keystore "$KS" -storepass "$PASS" -keypass "$PASS" -alias "$ALIAS" \
          -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Insula DICOM Viewer (debug)" 2>/dev/null
fi
APK="out/InsulaDICOMViewer-v$VERSION.apk"
apksigner sign --ks "$KS" --ks-pass "pass:$PASS" --ks-key-alias "$ALIAS" --out "$APK" out/aligned.apk
apksigner verify "$APK"
echo "BUILD OK: $APK"
