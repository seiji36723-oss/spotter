#!/bin/sh
# Gradle を使わずに APK を作る（Ubuntu の aapt・dalvik-exchange・zipalign・apksigner・android-sdk-platform-23 を使う）
# 使い方：KEYSTORE_FILE=鍵のファイル KEYSTORE_PASSWORD=パスワード sh android/build-apk.sh
# できた APK はリポジトリ直下の SPOTTER.apk（GitHub Pages から配る）
# 改訂版：EDITION=kaitei をつけると kaitei/index.html から kaitei/SPOTTER-kaitei.apk を作る（別のアプリ「SPOTTER改」として並べて入れられる）
set -e
cd "$(dirname "$0")/.."
SRC=android/app/src/main
JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
B=$(mktemp -d)
mkdir -p "$B/gen" "$B/obj" "$B/assets" "$B/dex"
if [ "$EDITION" = kaitei ]; then
  HTML=kaitei/index.html; OUT=kaitei/SPOTTER-kaitei.apk; LABEL="SPOTTER改"; RENAME="--rename-manifest-package app.spotter.kaitei"
else
  HTML=index.html; OUT=SPOTTER.apk; LABEL="SPOTTER"; RENAME=""
fi
cp "$HTML" "$B/assets/index.html"
# package 名は build.gradle の namespace と同じ
sed -e 's#<manifest xmlns:android="http://schemas.android.com/apk/res/android">#<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="app.spotter">#' \
    -e "s#android:label=\"SPOTTER\"#android:label=\"$LABEL\"#" "$SRC/AndroidManifest.xml" > "$B/AndroidManifest.xml"
# 更新できるように、バージョン番号は毎回大きくする（1970年からの分数）
VC=$(( $(date +%s) / 60 ))
VN="1.$(date +%Y%m%d)"
aapt package -f -m -J "$B/gen" -M "$B/AndroidManifest.xml" -S "$SRC/res" -I "$JAR"
javac -nowarn -source 8 -target 8 -bootclasspath "$JAR" -d "$B/obj" "$B/gen/app/spotter/R.java" "$SRC"/java/app/spotter/*.java
dalvik-exchange --dex --output="$B/dex/classes.dex" "$B/obj"
aapt package -f -M "$B/AndroidManifest.xml" -S "$SRC/res" -A "$B/assets" -I "$JAR" \
  --min-sdk-version 24 --target-sdk-version 33 $RENAME --version-code "$VC" --version-name "$VN" -F "$B/unsigned.apk"
(cd "$B/dex" && aapt add "$B/unsigned.apk" classes.dex > /dev/null)
zipalign -f 4 "$B/unsigned.apk" "$B/aligned.apk"
apksigner sign --v4-signing-enabled false --ks "$KEYSTORE_FILE" --ks-key-alias "${KEY_ALIAS:-spotter}" --ks-pass "pass:$KEYSTORE_PASSWORD" --key-pass "pass:${KEY_PASSWORD:-$KEYSTORE_PASSWORD}" --out "$OUT" "$B/aligned.apk"
rm -rf "$B"
echo "$OUT（バージョン $VN / $VC）"
