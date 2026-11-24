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

Applied Singulo 0.1.1 Beta targets Minecraft 1.21.1 NeoForge and Singulo [0.20.0,0.21.0). The dependency is the approved, generally public CurseForge file 9660320, pinned to its official HTTPS CDN URL and verified SHA-256. Its bytes match the final Singulo release CI artifact (merge 01a91af281c88423ba9066aa435cb1e304a8f04d). Fresh CI, independent review and a maintained-branch release receipt remain mandatory before publication. The owner has configured the curseforge Environment and authorized its use. Earlier 0.19.0 notes above describe the previous release.

## CurseForge publication (1.21.1)

Run Publish CurseForge manually on 1.21.1 after review and merge. Supply a successful first-attempt maintained-branch Build run and immutable v<mod_version> tag at that exact commit. Complete artifact ZIP digest, manifest, JAR and log hashes are checked before promotion. No rebuild occurs. Public dependency pins are mandatory. Use only configured curseforge Environment secrets.

The permanent create-only upload-lock tag precedes the asset-free draft audit release and single upload attempt. Never delete/adopt the lock or automatically retry failed/uncertain submissions. Inspect CurseForge first. Keep lock tags and maintained branches during cleanup. Submission is not moderation approval; verify the file separately.


## 0.2.0 fluid crafting

Use only the public AndroidWorkshops discovery/card API and AndroidOrders.fluidCraftingRecipes.
The latter validates actual crafting remainders before replacing bucket inputs with fluid mB.
Do not infer fluid equivalence from item appearance or swap arbitrary fluid containers.
Original and fluid patterns are exported; private AndroidEscrows custody remains mandatory.
Processing, liquid main products, tools, fuel, catalysts and chemicals remain unsupported.
The added test recipe lives under data/applied_singulo_test and is excluded from production JARs.
Run all existing GameTests plus fluidCraftingUsesPrivateCustodyAndReturnsNoSyntheticBucket.
Never promote local override builds: pin the final approved public 0.21.0 core artifact first,
then rerun build, all GameTests, distribution checks and release receipt preparation.

Recipe selection: Singulo's planner selects the first loaded card for each output identity.
The new fluid variant API follows that order. Do not rely on later cards with the same output
and different ingredients as independently selectable escrow recipes; the existing order API
selects an output, not a recipe ID. One intended recipe per output avoids this ambiguity.

## Singulo 0.22 compatibility (Applied Singulo 0.2.1)

The validation pin is the public CurseForge Singulo 0.22.0 file 9778100 for Minecraft 1.21.1 / NeoForge. Normal CI verifies its SHA-256 before running all integration tests. The runtime range is [0.21.0,0.23.0); the older 0.21.0 instructions above are historical. Publication still requires a passing maintained-branch CI receipt, immutable exact tag, and existing CurseForge guard.
