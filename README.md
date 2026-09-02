# X 即時翻訳（Android）

X公式アプリの通知をAndroidの `NotificationListenerService` でイベント受信し、英語通知をML Kitで端末内翻訳して日本語通知として表示します。Android Studioは不要で、GitHub ActionsがビルドしたAPKをスマホに直接インストールできます。

## APKの入手方法（Android Studio不要）

1. GitHubリポジトリの **Actions** タブを開く
2. 一番上にある `Android Debug APK Build` ワークフローの実行結果（緑のチェック）を開く
3. 画面下部の **Artifacts** に表示される `XInstantTranslator-debug-apk` をスマホでダウンロード（zip形式）
4. ダウンロードしたzipを展開すると `app-debug.apk` が出てくるので、スマホ側で開いてインストール
   - 「提供元不明のアプリ」の許可を求められた場合は許可してください

`main` ブランチや作業ブランチにpushするたびに、GitHub Actionsが自動でdebug APKをビルドします。

## 初回設定

1. 上記の手順でAPKをスマホにインストール
2. アプリを開き、通知の送信を許可
3. 「通知へのアクセスを許可」から「X即時翻訳」をオン
4. 「翻訳モデルを準備」を押す（初回のみ約30MB）
5. Androidのアプリ情報で、可能ならバッテリー使用量を「制限なし」に設定

## 動作上の注意

- 1秒ごとのポーリングではなく、X通知がAndroidに届いた瞬間のイベントで動作します。
- X側の配信遅延や、X公式アプリで届かなかった通知は取得できません。
- 英語と判定した通知だけ翻訳します。日本語通知はそのままです。
- 翻訳モデル準備後は、翻訳本文を外部サーバーへ送信しません。
- 通知をタップすると、元のX通知と同じ画面を開きます。

## 対応

- Android 8.0以降
- X公式アプリ（`com.twitter.android`）およびX Lite
