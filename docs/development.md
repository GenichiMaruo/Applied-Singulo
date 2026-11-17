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

Applied Singulo requires **Singulo 0.19.0**. `tools/dependencies.json` now pins the official CurseForge
CDN file 9617080 and SHA-256 `ec45f7d6a6a9dbac654c7a2048df085ca709f055868def39aeee4132d44ed942`.
Those downloaded bytes match the actual successful Singulo CI 43628318212 JAR byte-for-byte.
Tag v0.19.0 / commit `0f6fcff7645a2a5066a072acce55d4a03884080e` and tested CI commit have the same tree.

The CF file is now Approved and generally public. The dependency publication gate is open for
preparing a CI receipt. This does not authorize publishing Applied Singulo itself.

- Normal `./gradlew build` / `runGameTestServer` downloads and verifies all three pinned dependencies.
- Local `-PsinguloJar` overrides remain for development only, not publication preparation.
- `tools/prepare_release.py` requires the successful GameTest log, all public dependency pins, clean tracked
  source and the current GitHub Actions commit. Use only a fresh CI build -> tests -> manifest artifact,
  never an old local JAR relabelled with a new commit.
- Metadata requires `[0.19.0,0.20.0)`. No Applied CF/Modrinth publication or merge is authorized here.

## Singulo 0.20.0 compatibility preparation

The fix/singulo-020-compat-1211 branch prepares Applied Singulo 0.1.1 Beta for Minecraft 1.21.1 NeoForge and Singulo [0.20.0,0.21.0). The dependency is temporarily pinned to reviewed PR #79 source, not a published release. This branch must not be published until the final Singulo commit has passed compatibility tests, the official dependency URL and SHA-256 are pinned, and fresh CI produces a release receipt. The owner's API-key preparation is a separate publication hold. Earlier 0.19.0 notes above describe the previous release.
