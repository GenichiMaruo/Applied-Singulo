# 開発・配布ルール

- 既定・維持ブランチ `1.21.1`、開発は `feature/<version>`、修正は `fix/<topic>`。
- JAR は `Applied-Singulo-<version>-<Minecraft><loader>.jar`。NeoForge=N、将来Forgeを作る場合はF。
- 0.1.0 の配布区分は Beta。公開承認後のタグは `v0.1.0`。今回はタグ・Release・配布サイト公開をしません。
- `./gradlew --no-daemon build` と `python3 tools/check_distribution.py` を通す。依存 JAR は `tools/dependencies.json` に固定する（公開版は URL・SHA-256、未公開版はソースのコミット）。
- 実装後は実依存でクライアント・専用サーバー・GameTest を追加し、0件実行は失敗扱いにする。
- CI は contents:read のみ。秘密情報を使わず、pull_request_target は使わない。
- 公開時は成功CIの同一成果物を昇格し、依存・ローダー・既存公開物・ハッシュを確認。自動再送やタグ移動で検証を迂回しない。
- AE2 は appeng.api を優先。内部クラスの mixin/reflection を前提にしない。AE2 風の画面とパネルのため、AE2 の画面・メニュー基盤（`AEBaseScreen`/`AEBaseMenu`/`MenuTypeBuilder`/`InitScreens`/`PriorityMenu` 用の `IPriorityHost`）と部品基盤（`AEBasePart`/`PartItem`）だけは直接使う（継承のみ、mixin・reflection なし）。使う箇所は `me/AndroidInterface*`、`client/` に限る。AE2 の画面スタイル JSON とその背景画像は AE2 の名前空間でしか読まれないため、`assets/ae2/screens/applied_singulo/` と `assets/ae2/textures/guis/applied_singulo/` に置く。
- Singulo は公開 API `io.github.genichimaruo.singulo.api.android`（0.19.0 の `AndroidEscrows`・`AndroidOrders`）を使う。残る内部依存は作業場所カードの読み取りだけで、`bridge/SinguloLinks` に閉じ込める（[採用状況](0.1.0/singulo-api-proposal.md)）。
- メタデータの依存範囲は動作保証ではない。広げる際は実ゲームで再検証する。

## Singulo dependency

Applied Singulo now requires **Singulo 0.19.0**, for the private crafting escrow (`AndroidEscrows`) and recipe snapshots (`AndroidOrders.recipes`). Singulo 0.19.0 is **not released yet**: `tools/dependencies.json` pins the development commit `d12d72c0...` (see `tools/dependencies.json`) instead of a download URL and hash. It is `feature/0.19.0` (`b43db6ba`) plus the escrow trip-size fix (`bed2d08b`) the `AndroidSharedStorage` API, multi-step escrow, a fetch fix for large slots and machine/fluid escrow, on the local branch `feature/0.19.0-shared-storage`, **not pushed yet**: until it is pushed, only machines whose `../Singulo` clone has the commit (and not CI) can build it.

- `./gradlew runClient` (or any build) runs `fetchDependencies`. It `git archive`s that exact commit from `../Singulo` when that clone has it, or fetches it from GitHub, builds it with Singulo's own wrapper into `libs/Singulo-0.19.0-1.21.1N.jar` (first run only, about 1-2 minutes), and records the commit in `libs/Singulo-0.19.0-1.21.1N.jar.commit`. The Singulo working tree and branches are never touched; changing the pinned commit rebuilds.
- `-PsinguloJar=/path/Singulo.jar` still overrides it.
- `tools/check_distribution.py` prints **NOT PUBLISHABLE** while any dependency is unreleased. Before publishing, replace the entry with the released Singulo 0.19.0 URL and SHA-256 and re-run the GameTests.
- The metadata range is `[0.19.0,0.20.0)`.
