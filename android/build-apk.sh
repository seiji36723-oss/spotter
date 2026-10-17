#!/bin/sh
# Gradle を使わずに APK を作る（Ubuntu の aapt・dalvik-exchange・zipalign・apksigner・android-sdk-platform-23 を使う）
# 使い方：KEYSTORE_FILE=鍵のファイル KEYSTORE_PASSWORD=パスワード sh android/build-apk.sh
# できた APK はリポジトリ直下の SPOTTER.apk（GitHub Pages から配る）
set -e
cd "$(dirname "$0")/.."
SRC=android/app/src/main
JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
B=$(mktemp -d)
mkdir -p "$B/gen" "$B/obj" "$B/assets" "$B/dex"
cp index.html "$B/assets/index.html"
# package 名は build.gradle の namespace と同じ
sed 's#<manifest xmlns:android="http://schemas.android.com/apk/res/android">#<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="app.spotter">#' "$SRC/AndroidManifest.xml" > "$B/AndroidManifest.xml"
# 更新できるように、バージョン番号は日時（年月日時）で毎回大きくする
VC=$(date +%y%m%d%H)
VN="1.$(date +%Y%m%d)"
aapt package -f -m -J "$B/gen" -M "$B/AndroidManifest.xml" -S "$SRC/res" -I "$JAR"
javac -nowarn -source 8 -target 8 -bootclasspath "$JAR" -d "$B/obj" "$B/gen/app/spotter/R.java" "$SRC/java/app/spotter/MainActivity.java"
dalvik-exchange --dex --output="$B/dex/classes.dex" "$B/obj"
aapt package -f -M "$B/AndroidManifest.xml" -S "$SRC/res" -A "$B/assets" -I "$JAR" \
  --min-sdk-version 24 --target-sdk-version 34 --version-code "$VC" --version-name "$VN" -F "$B/unsigned.apk"
(cd "$B/dex" && aapt add "$B/unsigned.apk" classes.dex > /dev/null)
zipalign -f 4 "$B/unsigned.apk" "$B/aligned.apk"
apksigner sign --v4-signing-enabled false --ks "$KEYSTORE_FILE" --ks-key-alias "${KEY_ALIAS:-spotter}" --ks-pass "pass:$KEYSTORE_PASSWORD" --key-pass "pass:${KEY_PASSWORD:-$KEYSTORE_PASSWORD}" --out SPOTTER.apk "$B/aligned.apk"
rm -rf "$B"
echo "SPOTTER.apk（バージョン $VN / $VC）"
