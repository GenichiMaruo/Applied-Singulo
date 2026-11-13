# Applied Singulo

Singulo のアンドロイド自動化と Applied Energistics 2 (AE2) の ME ネットワークをつなぐアドオンです。

**現在は開発の土台のみ。連携機能は未実装・未配布で、完成したプレイ用 MOD ではありません。**

## 対象・命名

- Minecraft **1.21.1** / NeoForge **21.1.256+** / Java **21**
- 開発基準: Singulo **0.17.1** / AE2 **19.2.19** (NeoForge)
- 既定・維持ブランチ `1.21.1`、開発ブランチ `feature/0.1.0`
- JAR: `Applied-Singulo-0.1.0-1.21.1N.jar` (`N` = NeoForge)
- 予定公開区分は **Beta**。Singulo と同様、JAR 名に beta は付けません。
- 1.20.1 移植は今回の対象外です。

## 開発

```sh
./gradlew --no-daemon build
python3 tools/check_distribution.py
./gradlew runClient -PsinguloJar=/path/Singulo-0.17.1-1.21.1N.jar -Pae2Jar=/path/appliedenergistics2-19.2.19.jar
```

依存 JAR は正式配布元から入手し、`libs/` 等へ置いてください。Git には含めません。GuideME が別途必要な配布構成では `-PguideMeJar=/path/guideme.jar` を追加します。サーバーは `runServer`。初回 EULA は利用者が確認してください。

ビルド成功はゲーム内連携の動作確認ではありません。GameTest はまだ追加しておらず、0件完走を成功扱いしません。

[開発・配布ルール](docs/development.md) / [変更履歴](CHANGELOG.md)

License: All Rights Reserved (Singulo / Singulo Arsenal と同方針)。AE2 チームの公式アドオンではありません。
