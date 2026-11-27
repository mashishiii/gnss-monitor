# GNSS モニター (gnss-monitor)

移動中に、スマホの内蔵 GNSS（GPS・みちびき・GLONASS・Galileo・BeiDou など）が **今どの衛星をつかんでいるか** を見るための Android アプリです。カーナビ（Sony nav-u / Panasonic ストラーダ）の「測位情報」画面のような、暗い背景の見た目を目指しています。

- Android 専用のネイティブアプリ（Kotlin + Jetpack Compose）
- 配布は APK のみ（Google Play では公開しません）
- 要件は [docs/requirements.md](docs/requirements.md)、画面ラフは [こちら](https://cursor.com/canvas/store-6b8df988-9d93-4e08-80bf-9b57d543e5ff/32fc77e4-3355-45ae-becf-fe84d10a0c34)

## 機能とロードマップ

| バージョン | 内容 |
| --- | --- |
| **v0.1**（v0.1.1 で多周波端末のクラッシュを修正） | 測位状態・衛星数・緯度経度・精度のヘッダーと、衛星の一覧（測位系・SVID・C/N0 とバー・方位・仰角・使用の有無）を 1 秒ごとに更新 |
| v0.2 | スカイプロット（北を上にした衛星の円）と受信強度の棒グラフ |
| v0.3 | 地図（OpenStreetMap・オフライン対応）と移動の軌跡、画面オフでも続く記録、CSV / GPX の書き出し |
| v0.4 | カーナビ風の見た目の仕上げ |

色の意味: 緑 = 位置計算に使用中 / 青 = 捕捉（受信できている）/ 灰 = 弱い。

## インストール方法（APK）

1. このリポジトリの [Releases](https://github.com/mashishiii/gnss-monitor/releases) を Android のスマホで開く
2. 最新バージョンの `gnss-monitor-vX.Y.Z.apk` をダウンロードする
3. ダウンロードした APK を開く。「この提供元のアプリを許可」のような画面が出たら、ブラウザ（または使っているファイル管理アプリ）に **「提供元不明のアプリ」のインストールを許可** してインストールする
4. 初回起動時に位置情報の許可（ACCESS_FINE_LOCATION）を求められるので許可する。屋外で空が開けた場所のほうが衛星を多くつかめます

> Play ストアを通さない APK のため、インストール時に上記の許可が必要です。インストール後は許可を戻して構いません。

## 位置情報について

位置情報や衛星のデータは端末の中だけで処理し、外部のサーバーには送りません。将来の記録機能で書き出す CSV / GPX などの **位置情報のログは、リポジトリにコミットしません**（`.gitignore` で `*.csv` `*.gpx` `logs/` を除外しています）。署名用の鍵（`*.jks` `*.keystore`）も同様です。

## 開発

必要なもの: JDK 17、Android SDK（compileSdk 37.1）。

```sh
./gradlew testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

- `main` への push と PR で CI（[.github/workflows/ci.yml](.github/workflows/ci.yml)）がテストとデバッグ APK のビルドを実行します
- `v0.1.0` のようなタグを push すると、[release.yml](.github/workflows/release.yml) が署名付き APK をビルドし、GitHub Releases に公開します
  - 署名には Secrets の `KEYSTORE_BASE64` `KEYSTORE_PASSWORD` `KEY_ALIAS` `KEY_PASSWORD` を使います（ローカルでは環境変数 `KEYSTORE_FILE` なども同じ名前。未設定なら未署名でビルドされます）
  - バージョン名はタグから、バージョンコードは Actions の実行番号から自動で決まります

## ディレクトリ構成

```
app/src/main/java/io/github/mashishiii/gnssmonitor/
├─ data/        GNSS の取得（LocationManager / GnssStatus）と測位系名・測位状態のロジック
├─ ui/list/     ViewModel と、ヘッダー + 衛星一覧の画面
└─ ui/theme/    カーナビ風ダークテーマ
```
