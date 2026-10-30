# SPOTTER

チョコザップのマシンで組む、お腹まわりを引き締めるためのトレーニング記録アプリです（非公式）。

- コースを4つから選べる（腹回り・健康・健康とガチの間・ガチ）。ジム（チョコザップ・ミラフィットネス・エニタイム・すべての器具）を選ぶと、そこにある器具でメニューを組む
- 毎日「筋トレ／有酸素だけ／休み」をアプリが決める（週に筋トレ3日・有酸素だけ1〜2日・休み2日。筋トレは 上半身→下半身→全身 の順で、毎回腹筋＋有酸素つき）
- 前回の重さ・回数から今日の目標を自動で出し、セットを終えると休憩タイマーが自動で始まる
- 有酸素と毎日の早歩きは速さを指示し、時間は目安を見ながら自分で入れる
- 週1回の体重を記録し、変化がなければ有酸素や食事の見直しを提案

## 通常版と改訂版
- 通常版：https://seiji36723-oss.github.io/spotter/ （ふだん使う版。Android アプリもこちら）
- 改訂版：https://seiji36723-oss.github.io/spotter/kaitei/ （手直しを先に入れて試す版）
- 改訂版の Android アプリ：https://seiji36723-oss.github.io/spotter/kaitei/SPOTTER-kaitei.apk （「SPOTTER改」として通常版と並べて入れられる）

改訂版の記録は通常版とは別に保存されます（初めて開いた時だけ通常版の記録を写します）。
改訂版の内容を通常版に入れる時は `sh sync-kaitei.sh` を実行し、Android アプリを作り直して push します。

## 使い方（iPhone）
1. Safari でアプリのURLを開く
2. 共有ボタン →「ホーム画面に追加」
3. ホーム画面の SPOTTER のアイコンから開く（全画面で開き、電波がなくても動きます）

記録はそれぞれのスマホの中だけに保存されます。機種変更のときは「設定 → 記録を書き出す」でコピーし、新しいスマホの「記録を読み込む」に貼り付けてください。

## ファイル
- `index.html` … アプリ本体（1ファイル）
- `kaitei/` … 改訂版（`index.html`・`sw.js`・`manifest.webmanifest`・`icons/` の改訂版用）
- `sync-kaitei.sh` … 改訂版を通常版に写す
- `manifest.webmanifest` … アプリの名前・アイコンの設定
- `sw.js` … オフラインで動くようにするしくみ
- `icons/` … アイコン画像

## Android アプリ
Android のスマホで https://seiji36723-oss.github.io/spotter/SPOTTER.apk を開いてダウンロードし、開くと入れられます（「提供元不明のアプリ」を許可する画面が出たら許可）。

- `SPOTTER.apk` … Android アプリ本体（`index.html` を変えたら作り直す）
- `android/` … Android アプリのしくみ（`index.html` を全画面で表示するだけ）
- `android/build-apk.sh` … Gradle を使わずに `SPOTTER.apk` を作る手順（署名の鍵が必要）
  - `EDITION=kaitei` をつけると、改訂版の `kaitei/SPOTTER-kaitei.apk` を作る
- Android アプリでは、トレーニング中の状態を通知（ロック画面・通知センター）に出し、完了・飛ばす・一時停止のボタンで操作できる（`SpotterService.java`）
- `setup/android.yml` … GitHub に APK を自動で作らせる手順（使う場合は `.github/workflows/android.yml` に移し、署名の鍵を Secrets に登録）
