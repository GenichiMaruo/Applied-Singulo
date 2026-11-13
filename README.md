# Applied Singulo

Singulo のアンドロイド自動化と Applied Energistics 2 (AE2) の ME ネットワークをつなぐアドオンです。

**開発途中です。AE2 パターンの出力から Android 注文 API を呼ぶ検証用コマンドを実装しています。Pattern Provider の全自動連携や材料移送はまだ実装しておらず、配布版ではありません。**

## 対象・命名

- Minecraft **1.21.1** / NeoForge **21.1.256+** / Java **21**
- 開発基準: API 追加済みの未公開 Singulo **0.18.0 開発版** / AE2 **19.2.19** (NeoForge)
- 既定・維持ブランチ `1.21.1`、開発ブランチ `feature/0.1.0`
- JAR: `Applied-Singulo-0.1.0-1.21.1N.jar` (`N` = NeoForge)
- 予定公開区分は **Beta**。Singulo と同様、JAR 名に beta は付けません。
- 1.20.1 移植は今回の対象外です。

## 開発

```sh
./gradlew --no-daemon build -PsinguloJar=/path/Singulo-0.18.0-1.21.1N.jar -Pae2Jar=/path/appliedenergistics2-19.2.19.jar -PguideMeJar=/path/guideme-21.1.1.jar
python3 tools/check_distribution.py
./gradlew runClient -PsinguloJar=/path/Singulo-0.18.0-1.21.1N.jar -Pae2Jar=/path/appliedenergistics2-19.2.19.jar -PguideMeJar=/path/guideme-21.1.1.jar
```

依存 JAR は正式配布元から入手し、`libs/` 等へ置いてください。Git には含めません。基準の AE2 19.2.19 は GuideME 21.1.1 以上を必須とするため、その対応 JAR も指定します。サーバーは `runServer`。初回 EULA は利用者が確認してください。

ビルド成功はゲーム内連携の動作確認ではありません。実 AE2 パターンを使う GameTest を追加しています。実装範囲と検証手順は [開発コマンド](docs/0.1.0/development-commands.md) を参照してください。

[開発・配布ルール](docs/development.md) / [変更履歴](CHANGELOG.md)

License: All Rights Reserved (Singulo / Singulo Arsenal と同方針)。AE2 チームの公式アドオンではありません。

## 0.1.0 開発計画

[Android × AE2 自動化連携の実装チェックリスト](docs/0.1.0/implementation-checklist.md)
