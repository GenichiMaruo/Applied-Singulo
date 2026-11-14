# Applied Singulo

Singulo のアンドロイド自動化と Applied Energistics 2 (AE2) の ME ネットワークをつなぐアドオンです。

**開発中 (0.1.0 Beta 予定、未公開)。** ME ケーブルに直接つなぐ「アンドロイドMEインターフェース」に注文先の作業指令台を指定すると、指令台の作業台レシピが AE2 のクラフト候補になり、ME 端末からの注文をアンドロイドがまとめて製作して ME へ戻します。材料は Singulo の注文ごとの個別預かりに入り、他の注文と混ざりません。ケーブルに貼るパネル版と、AE2 風の専用画面（優先度設定つき）があります（[検証記録](docs/0.1.0/verification.md)）。

## 必須環境

- Minecraft **1.21.1** / NeoForge **21.1.256+** / Java **21**
- **Singulo 0.19.0 (0.19.x)** — 0.19.0 の個別預かり API `AndroidEscrows` を使います。**0.19.0 は未公開**のため、現在は開発版のコミットから自動ビルドして使います。Singulo 0.19.0 が公開されるまで Applied Singulo も公開できません。
- **AE2 19.2.19+** (NeoForge) と、AE2 が必要とする **GuideME 21.1.1+**

## 命名・ブランチ

- 既定・維持ブランチ `1.21.1`、開発ブランチ `feature/0.1.0`
- JAR: `Applied-Singulo-0.1.0-1.21.1N.jar` (`N` = NeoForge)。予定公開区分は **Beta**（JAR 名に beta は付けません）。
- 1.20.1 Forge / NeoForge は対応ブランチで準備中です。

## 開発

```sh
./gradlew runClient                 # 依存 JAR を自動取得してクライアント起動
./gradlew build                     # ビルド
./gradlew runGameTestServer         # 実依存入り GameTest (39件)
python3 tools/check_distribution.py # 配布 JAR 検査
```

AE2・GuideME は `tools/dependencies.json` に固定した公式配布 URL から `libs/` へ自動ダウンロードされ、SHA-256 が一致しない JAR は削除して拒否します。未公開の Singulo 0.19.0 は、固定したコミットを `../Singulo`（なければ GitHub）から取り出して初回だけビルドします（[詳細](docs/development.md#singulo-dependency)）。依存 JAR は Git に含めず、アドオンにも同梱しません。

手元でビルドした Singulo などを試すときは `-PsinguloJar=/path/Singulo.jar`（`-Pae2Jar` / `-PguideMeJar` も同様）で上書きできます。上書きした JAR はハッシュ検査されないため、公開前の検証には使わないでください。サーバーは `runServer`。初回 EULA は利用者が確認してください。

## 使い方

- [アンドロイドMEインターフェース](docs/0.1.0/me-interface.md) — おすすめ。ケーブル直結、置き場所自由、レシピ自動共有、まとめ注文、材料の個別預かり
- [開発用コマンド](docs/0.1.0/development-commands.md) — パターンから手動で注文、受付状況の一覧

[実装チェックリスト](docs/0.1.0/implementation-checklist.md) / [開発・配布ルール](docs/development.md) / [変更履歴](CHANGELOG.md)

License: All Rights Reserved (Singulo / Singulo Arsenal と同方針)。AE2 チームの公式アドオンではありません。
