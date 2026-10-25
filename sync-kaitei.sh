#!/bin/sh
# 改訂版（kaitei/）の中身を通常版（いちばん上）に写す。
# 使い方：sh sync-kaitei.sh（そのあと Android アプリを作り直して push）
set -e
cd "$(dirname "$0")"
sed -e 's#<title>SPOTTER 改訂版</title>#<title>SPOTTER</title>#' \
    -e 's#<meta name="apple-mobile-web-app-title" content="SPOTTER改">#<meta name="apple-mobile-web-app-title" content="SPOTTER">#' \
    kaitei/index.html > index.html
# 通常版の版の番号を1つ上げて、開いている人にも新しい版を入れさせる
n=$(sed -n 's/^const CACHE = "spotter-v\([0-9]*\)";/\1/p' sw.js)
sed -i "s/^const CACHE = \"spotter-v$n\";/const CACHE = \"spotter-v$((n + 1))\";/" sw.js
echo "通常版に写しました（spotter-v$((n + 1))）"
