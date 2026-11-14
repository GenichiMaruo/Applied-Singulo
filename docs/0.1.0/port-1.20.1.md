# 0.1.0 の 1.20.1 移植記録

状態: **移植中。ビルド・実ゲーム検証はまだ完了していません。公開不可。**

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

## 未完了

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
このテストを含む42件のlegacy実行は本体JAR待ちです。共通文書の成功記録は1.21.1版の履歴です。

後続 `8aae650` の二重返金対策も同期。unsupported注文のローカル返金は本体受付がUNKNOWNの場合だけです。
既受理注文の古い保存コピーは本体の再送照合を通し、材料のローカルコピーを消して二重返金しません。
本体が受付後にラックがprocessingへ変わった再送を1件追加し、legacy期待総数は42件です。
