# 0.1.0 の 1.20.1 移植記録

状態: **最終互換検証中。依存のCF一般公開確認待ち。マージ・公開不可。**

## 対象

- Minecraft 1.20.1、Java 17
- Forge 47.4.26 / NeoForge 47.1.106（同じ net.minecraftforge API の世代）
- AE2 Forge 15.4.12。公式タグ `forge/v15.4.12`、ソース `5a544ce4aa4d51a1e92a21d316bb89c4a5f4a75a`
- GuideME 20.1.7（AE215.4.12実JARの必須依存、非同梱）
- Singulo 0.19.0 の対応ローダー版。AndroidEscrows / AndroidOrders / AndroidSharedStorage が必要。

AE2公式JAR SHA-256:
`b9c5928893a20d3feb0b4393e2c23d756a16d9abee24d9c14c98e6096d53844d`

## 互換処理

- Java21のList.getFirst・Math.ceilDivをJava17相当に変更。
- DataComponentsから1.20.1のNBTへ、パック形式15と複数形のdataディレクトリへ変更。
- ForgeのLazyOptional capability、LevelTickEvent、旧AE2パート操作APIを使用。
- AE2 15 の MenuTypeBuilder はAE2自身の名前空間専用のため、Forge IForgeMenuTypeとAE2 MenuOpenerを用いてアドオン自身の名前空間へメニュー登録。所有権をサーバー側で確認。
- SinguloとAE2をlegacyForgeのSRG→公式名リマップ経由で取り込み、配布JARはreobfJar側のみを検査。
- 内部台帳・材料隔離・納品の機能を省略して互換扱いにはしない。全GameTestを両ローダーで実行する。

## 初回移植時点の未完了（最新結果は末尾）

- [ ] Singulo 0.19.0 1.20.1 の実開発JARでコンパイル・GameTest
- [ ] 本体の確定公開ファイルをURL・SHA-256固定
- [ ] 全件GameTestをForge/NeoForgeで実行し、配布検査
- [x] 親側レビュー修正 `6d66249` を同期（資材返却・液体復旧・在庫二重発行・パターン更新、全39テスト）
- [ ] draft PR作成（マージ・公開はこの依頼の対象外）

参考:
- https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/forge/v15.4.12
- https://github.com/neoforged/ModDevGradle/blob/main/LEGACY.md

## 本体の材料隔離制限（6254d2同期）

機械のprocessing工程は、レシピ一覧で取得できても私有材料の受付対象ではありません。
AE2へ提示するのは通常crafting工程のみです。対応する流体サンプルは本体の契約内で使用できますが、
燃料・触媒・化学物質・手順/機械工程は提示しません。
提示後にラックが機械レシピへ変更された場合も、本体へ材料を渡す前に失敗扱いにして預かった材料を返します。
旧「機械工程成功」テストは非表示・custody UNKNOWN・原鉄2個返却・燃料不消費の検証へ置換しました。
初回時点ではこのテストを含む42件のlegacy実行は本体JAR待ちでした。共通文書の成功記録は1.21.1版の履歴です。

後続 `8aae650` の二重返金対策も同期。unsupported注文のローカル返金は本体受付がUNKNOWNの場合だけです。
既受理注文の古い保存コピーは本体の再送照合を通し、材料のローカルコピーを消して二重返金しません。
本体が受付後にラックがprocessingへ変わった再送を1件追加し、legacy期待総数は42件です。


## 2026-11-15 更新: 公式受理済み依存での検証

- 本体成功CI `43628507887` のtreeとmerge/tag `98bc49f7ab0805bad808b7ce62a160b9747b01ec` のtree `66c0e56b721163e5c66acad276078ddaa90f29df` が一致。
- Forge公式publish `43632195931` / CF `9617113`、NeoForge公式publish `43632292641` / CF `9617131` のJARを取得。成功CI、公式publish成果物、作者画面から確認したCF CDNの3者が同一bytes。固定URL/完全SHA-256は `tools/dependencies.json`。
- 受理・CDN取得は一般公開の証明ではないため、`pending_public` ゲートを維持。ローカル互換試験成果物は公開準備レシートではない。
- 共通45件（完全item/fluid NBT識別・chain材料別扱い・表示文字列dedup撤去の5回帰を含む）とlegacy専用2件の計47件。
- 旧版のmock接続はEmbeddedChannelを持つ実ServerPlayerで置換。成功/失敗/timeout時にplayer listとchannelを清掃。これはテスト専用で配布JARには含まない。
- 正式レシートは、固定公開依存・clean tree・GITHUB_SHA一致のfresh GitHub Actions buildと全件GameTestだけが生成可能。


NeoForge 47.1.106 は公式本体JARでbuild・47/47 GameTest・配布検査に成功。
専用無音クライアントでもtitle到達、5 item models、AE2 GUI style、GUI classの読込を確認。
これは画面操作・実ワールドでの全機能を手動確認したという意味ではない。
Forge検証は同じworktreeのloader切替を並列にした出力消失を是正し、完全逐次で再実行する。


Forge 47.4.26 の逐次再検証でも build・47/47 GameTest・配布検査が成功。
両loaderの全47件は成功済み。初回並列検証のClassNotFound失敗を隠さず、上記の運用原因と再検証で区別した。
正式公開・マージは実施していない。専用クライアントスモークは `-PclientSmoke=true` を指定し、
`build/<loader>/client-smoke/options.txt` に音量0を設定して実行する。ユーザーのゲーム設定は変更しない。


## 正式依存確認後の追検証

2026-11-14 20:00 UTC、本体の両CFファイルの作者Approved・一般公開DLを確認。
URL/hashを変えずpending-publicフラグを解除。Applied自身のマージ・公開はしない。
PR CI43634409228ではForgeの量子作業台102柵テストが1件不安定失敗したため、
以前の成功だけを根拠に無視せず、終了した注文の状態/理由・返却bufferを含む診断を追加して調査する。
