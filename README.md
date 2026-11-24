# Applied Singulo

Singulo のアンドロイド自動化と Applied Energistics 2（AE2）の ME ネットワークをつなぐアドオンです。アンドロイドMEインターフェースを作業指令台に接続すると、ME端末から対応する作業台レシピを注文できます。材料は注文ごとに分けて管理し、完成品をMEネットワークへ戻します。

Applied Singulo connects Singulo's androids to Applied Energistics 2 (AE2) autocrafting. Link an Android ME Interface to a work order station to order supported crafting-table recipes from an ME terminal. Materials are kept separate by order, and finished items return to the ME network.

Applied Singulo **0.2.1** adds compatibility with Singulo **0.22.x**, while retaining **0.21.x** support. Choose the matching Minecraft version and loader on the [CurseForge files page](https://www.curseforge.com/minecraft/mc-mods/applied-singulo/files). The table below links to the original 0.2.0 files for Singulo 0.21.x.

## 導入 / Installation

Applied Singulo 0.2.0 ベータは CurseForge で公開中です。Minecraft とローダーが一致する行を選び、Singulo 0.21.x、対応する AE2 とその依存 MOD（GuideME など）、Applied Singulo を導入してください。

Applied Singulo 0.2.0 beta is available on CurseForge. Use the row matching your Minecraft version and loader. Install Singulo 0.21.x, a matching AE2 build and its dependencies (such as GuideME), and Applied Singulo.

| Minecraft | ローダー / Loader | Java | Singulo 0.21.0 | Applied Singulo 0.2.0 | AE2 |
| --- | --- | --- | --- | --- | --- |
| 1.21.1 | NeoForge 21.1.256+ | 21 | [本体 / Core](https://www.curseforge.com/minecraft/mc-mods/singulo/files/9709614) | [1.21.1N](https://www.curseforge.com/minecraft/mc-mods/applied-singulo/files/9710201) | 19.2.19 ≤ AE2 < 20.0.0 |
| 1.20.1 | Forge 47.4.26+ | 17 | [本体 / Core](https://www.curseforge.com/minecraft/mc-mods/singulo/files/9709620) | [1.20.1F](https://www.curseforge.com/minecraft/mc-mods/applied-singulo/files/9710204) | 15.4.12 ≤ AE2 < 16.0.0 |
| 1.20.1 | NeoForge 47.1.106+ | 17 | [本体 / Core](https://www.curseforge.com/minecraft/mc-mods/singulo/files/9709627) | [1.20.1N](https://www.curseforge.com/minecraft/mc-mods/applied-singulo/files/9710207) | 15.4.12 ≤ AE2 < 16.0.0 |
| 26.1.2 | NeoForge 26.1.2.126+ | 25 | [本体 / Core](https://www.curseforge.com/minecraft/mc-mods/singulo/files/9709632) | [26.1.2N](https://www.curseforge.com/minecraft/mc-mods/applied-singulo/files/9710210) | 26.1.13-beta ≤ AE2 < 26.2 |

JAR 名の末尾は `N` が NeoForge、`F` が Forge です。 / JAR names end in `N` for NeoForge or `F` for Forge.

## 液体を使うクラフト / Crafting with fluids

対象の作業台レシピでは、バケツの代わりに AE2 の液体ストレージから材料を供給できます。アンドロイドにはタンクアップグレードが必要です。従来のバケツ入りパターンも使えます。完成品はアイテムのみで、液体を主な完成品とするレシピや一般の機械加工は対象外です。

For eligible crafting-table recipes, AE2 fluid storage can supply the ingredient in place of a filled bucket. Equip the android with a tank upgrade. Existing filled-bucket patterns still work. Recipes that primarily output fluids and general machine processing are outside the scope of this feature.

以前のリリースは [変更履歴 / Release history](CHANGELOG.md) と [CurseForge のファイル一覧 / All files](https://www.curseforge.com/minecraft/mc-mods/applied-singulo/files) を参照してください。

## 開発

```sh
./gradlew runClient                 # 依存 JAR を自動取得してクライアント起動
./gradlew build                     # ビルド
./gradlew runGameTestServer         # 実依存入り GameTest
python3 tools/check_distribution.py # 配布 JAR 検査
```

AE2・GuideME は `tools/dependencies.json` に固定した公式配布 URL から `libs/` へ自動ダウンロードされ、SHA-256 が一致しない JAR は削除して拒否します。Singulo 0.22.0 も公開済み公式 CurseForge JARをSHA-256照合して取得します（[詳細](docs/development.md#singulo-dependency)）。依存 JAR は Git に含めず、アドオンにも同梱しません。

手元でビルドした Singulo などを試すときは `-PsinguloJar=/path/Singulo.jar`（`-Pae2Jar` / `-PguideMeJar` も同様）で上書きできます。上書きした JAR はハッシュ検査されないため、公開前の検証には使わないでください。サーバーは `runServer`。初回 EULA は利用者が確認してください。

## 使い方

- [アンドロイドMEインターフェース](docs/0.1.0/me-interface.md) — おすすめ。ケーブル直結、置き場所自由、レシピ自動共有、まとめ注文、材料の個別預かり
- [開発用コマンド](docs/0.1.0/development-commands.md) — パターンから手動で注文、受付状況の一覧

[実装チェックリスト](docs/0.1.0/implementation-checklist.md) / [開発・配布ルール](docs/development.md) / [変更履歴](CHANGELOG.md)

License: All Rights Reserved (Singulo / Singulo Arsenal と同方針)。AE2 チームの公式アドオンではありません。
