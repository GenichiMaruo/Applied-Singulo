> Applied Singulo 0.2.2 Beta / Minecraft 1.20.1 / Singulo 0.21.x–0.23.x.

AE2 can offer liquid inputs for supported Android workbench recipes. An android needs a tank upgrade. Orders keep their private material custody; machine processing and liquid main products are not exported.


# Applied Singulo

Singulo のアンドロイド自動化と Applied Energistics 2 (AE2) の ME ネットワークをつなぐアドオンです。

ME ケーブルに直接つなぐ「アンドロイドMEインターフェース」に注文先の作業指令台を指定すると、指令台の作業台レシピが AE2 のクラフト候補になり、ME 端末からの注文をアンドロイドがまとめて製作して ME へ戻します。材料は Singulo の注文ごとの個別預かりに入り、他の注文と混ざりません。ケーブルに貼るパネル版と、AE2 風の専用画面（優先度設定つき）があります（[検証記録](docs/0.1.0/verification.md)）。

## 必須環境

- Minecraft **1.20.1** / Forge **47.4.26+** または NeoForge **47.1.106+** / Java **17**
- **Singulo 0.21.x–0.23.x**（Minecraft とローダーが一致するもの）
- **AE2 15.4.12 以上、16.0.0 未満**（Forge 版）、**GuideME**（検証に使用した版: 20.1.7）
- [Applied Singulo 0.2.2 Beta のファイル一覧](https://www.curseforge.com/minecraft/mc-mods/applied-singulo/files)

## 命名・ブランチ

- 維持ブランチ: `1.20.1`
- JAR: `Applied-Singulo-0.2.2-1.20.1F.jar` / `Applied-Singulo-0.2.2-1.20.1N.jar`（`F`: Forge、`N`: NeoForge）

## 開発

```sh
python3 tools/fetch_dependencies.py
./gradlew fetchDependencies -Ploader=forge
./gradlew build -Ploader=forge
./gradlew runGameTestServer -Ploader=forge
python3 tools/check_distribution.py --loader forge
```

NeoForge は `-Ploader=neoforge` / `--loader neoforge` に置き換えます。同一作業ツリーで異なるloaderのビルドや実行を並行させず、一方のプロセスが終了してから切り替えてください（Gradleの古い出力清掃との競合回避）。
検証用に別の本体を使う場合だけ各 Gradle コマンドに `-PsinguloJar=/path/Singulo-0.23.0-1.20.1F.jar`
（NeoForge は末尾N）を追加してください。対象が異なる本体では起動検証できません。
`runClient` / `runServer` も同じ loader と依存を指定します。依存JARをGitや配布JARに同梱しません。
上書きJAR・未公開依存を使ったビルドは公開用の検証ではありません。

## 使い方

- [アンドロイドMEインターフェース](docs/0.1.0/me-interface.md) — おすすめ。ケーブル直結、置き場所自由、レシピ自動共有、まとめ注文、材料の個別預かり
- [開発用コマンド](docs/0.1.0/development-commands.md) — パターンから手動で注文、受付状況の一覧

[実装チェックリスト](docs/0.1.0/implementation-checklist.md) / [開発・配布ルール](docs/development.md) / [変更履歴](CHANGELOG.md)

License: All Rights Reserved (Singulo / Singulo Arsenal と同方針)。AE2 チームの公式アドオンではありません。
