# Singulo 本体 API の採用状況

0.18.0 の公開 API だけでは実現できなかった点のために、Singulo 本体に API の追加を提案していました。提案は Singulo `feature/0.19.0`（開発版、未公開）で実装されました。Applied Singulo は、そのうち次の API を使っています。

| 目的 | 0.18.0 での回避策（廃止） | 採用した 0.19.0 API |
| --- | --- | --- |
| 材料の隔離 | 預かり材料を指令台の共有在庫として見せていた | `AndroidEscrows.submit` — まとめた材料を注文ごとの個別預かりへ渡す（`acceptedNow` のときだけ手放す） |
| 完成品を確実に受け取る | 納品口・指令台の納品箱から取り出していた | `AndroidEscrows.snapshot` / `claim` — 注文 ID ごとに完成品と余り材料を取り出す。空になったら `acknowledge` |
| 指令台から離して置く | 指令台・資材口・納品口に隣接させていた | 個別預かりは位置に依存しないため不要。注文先の指令台は作業場所カードで指定する |
| レシピの共有 | `WorkConsoleBlockEntity.recipes()` と `RecipeData` を直接読んでいた | `AndroidOrders.recipes` — 不変の `AndroidRecipe` スナップショット |

同じ 0.19.0 には、ブロックを資材口・納品口として指令台にリンクする `AndroidWorkEndpoint` と、取り出し元を限定する `AndroidOrders.submitFromEndpoint` もあります。Singulo の文書が明記しているとおり、これらは共有在庫のままで、個別預かりではありません。Applied Singulo は AE2 の「全受理か全拒否」と整合する `AndroidEscrows` だけを使います。

## 残っている内部依存

- 作業場所カードに記録された指令台の座標を読む `WorkLocationCardItem.isCard / pos / sameDimension`（`order/SinguloLinks`）。

## Singulo 側に残る課題（今後の提案候補）

- 加工機械・手順・燃料・触媒を使うレシピの隔離（個別預かりは作業台レシピ 1 工程のみ対応）。
- 個別預かりの容量（次元ごと 64 件）を事前に問い合わせる API。
- 作業場所カードの読み取りを公開 API にする。
