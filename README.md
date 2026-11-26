> Applied Singulo 0.2.2 Beta / Minecraft 26.1.2 / Singulo 0.21.x–0.23.x.

AE2 can offer liquid inputs for supported Android workbench recipes. An android needs a tank upgrade. Orders keep their private material custody; machine processing and liquid main products are not exported.


# Applied Singulo

Singulo のアンドロイド自動化と Applied Energistics 2 (AE2) の ME ネットワークをつなぐアドオンです。

ME ケーブルに直接つなぐ「アンドロイドMEインターフェース」に注文先の作業指令台を指定すると、指令台の作業台レシピが AE2 のクラフト候補になり、ME 端末からの注文をアンドロイドがまとめて製作して ME へ戻します。材料は Singulo の注文ごとの個別預かりに入り、他の注文と混ざりません。ケーブルに貼るパネル版と、AE2 風の専用画面（優先度設定つき）があります（[検証記録](docs/0.1.0/verification.md)）。

## 必須環境

- Minecraft **26.1.2** / NeoForge **26.1.2.126+** / Java **25**
- **Singulo 0.21.x–0.23.x**（Minecraft とローダーが一致するもの）
- **AE2 26.1.13-beta 以上、26.2 未満**、**GuideME**（検証に使用した版: 26.1.14-beta）
- [Applied Singulo 0.2.2 Beta のファイル一覧](https://www.curseforge.com/minecraft/mc-mods/applied-singulo/files)

## 命名・ブランチ

- 維持ブランチ: `26.1.2`
- JAR: `Applied-Singulo-0.2.2-26.1.2N.jar`（`N`: NeoForge）

## 開発

```sh
./gradlew runClient                 # 依存 JAR を自動取得してクライアント起動
./gradlew build                     # ビルド
./gradlew runGameTestServer         # 実依存入り GameTest
python3 tools/check_distribution.py # 配布 JAR 検査
```

Singulo 0.23.0・AE2・GuideME は `tools/dependencies.json` に固定した公式配布 URL から `libs/` へ自動ダウンロードされ、SHA-256 を照合します（[詳細](docs/development.md)）。依存 JAR は Git に含めず、アドオンにも同梱しません。

手元でビルドした Singulo などを試すときは `-PsinguloJar=/path/Singulo.jar`（`-Pae2Jar` / `-PguideMeJar` も同様）で上書きできます。上書きした JAR はハッシュ検査されないため、公開前の検証には使わないでください。サーバーは `runServer`。初回 EULA は利用者が確認してください。

## 使い方

- [アンドロイドMEインターフェース](docs/0.1.0/me-interface.md) — おすすめ。ケーブル直結、置き場所自由、レシピ自動共有、まとめ注文、材料の個別預かり
- [開発用コマンド](docs/0.1.0/development-commands.md) — パターンから手動で注文、受付状況の一覧

[実装チェックリスト](docs/0.1.0/implementation-checklist.md) / [開発・配布ルール](docs/development.md) / [変更履歴](CHANGELOG.md)

License: All Rights Reserved (Singulo / Singulo Arsenal と同方針)。AE2 チームの公式アドオンではありません。
