# Applied Singulo 0.1.0 Beta 実装チェックリスト

作成日: 2026-11-13。**これは提案と実装計画です。以下の機能はまだ実装していません。**
対象: Minecraft 1.21.1 / NeoForge / Singulo 0.17.1 / AE2 19.2.19。

## ねらいと最小構成

「ME 端末で注文すると、Singulo のアンドロイドが工場を動かし、完成品が ME に戻る」を初版の到達点とする。

`Crafting CPU → Pattern Provider → Android 連携ブリッジ → 作業指令台 → アンドロイド → 専用納品口 → Import Bus → ME`

ブリッジを Pattern Provider に隣接させ、AE2 公開 capability `AECapabilities.CRAFTING_MACHINE` / `ICraftingMachine` を実装する。既存の加工パターンと Import Bus を利用し、初版から独自 ME ノードや端末を作らない。

**0.1.0 の範囲:** アイテムのみ、指令台1台へのリンク、ブリッジごと同時1注文、単一主出力、登録済みレシピ。液体・化学物質、複数主出力、在庫維持、逆方向の素材発注、自動パターン生成、遠隔次元は後回し。

## 0. 土台（今回準備したもの）

- [x] `1.21.1` 維持ブランチと `feature/0.1.0` 開発ブランチ。
- [x] Java 21 / NeoForge の起動入口、必須依存メタデータ、Gradle wrapper。
- [x] `Applied-Singulo-0.1.0-1.21.1N.jar` の命名と Beta 区分。
- [x] ビルド・配布物検査用 CI と依存 JAR 非同梱の方針。
- [ ] 実依存入り専用サーバー・クライアントの起動確認。
- [ ] 実装後の GameTest を追加し、0件実行を失敗にする。

## 1. 最初に解決するAPI境界（着手ゲート）

Singulo 0.17.1 の `WorkConsoleBlockEntity.start(...)` は `ServerPlayer` を受け、成功時 null / 失敗時 Component を返す。安定した外部受付ID APIではない。`orders()` の可変リストを直接変更しない。完了でも取消でも注文が消えるので **注文が見つからないことを成功扱いしない**。

- [ ] 本体側に正式APIを足す案と、版固定の薄いアダプター案を比較して採用方式を記録。本体側の変更が必要なら別PRでレビューする。
- [ ] `submit(requestId, owner, recipe, count, materialReservation)` → 明示的受付結果と注文ID、`status(orderId)`、`cancel(orderId)` の契約を定義する（これは提案名であり既存APIではない）。
- [ ] 受付IDによる重複抑止、永続化、状態照会、失敗理由、取消・復旧手順を定義する。
- [ ] private な FakePlayer 生成処理を反射で呼ばず、権限を持つ自動注文経路を設計する。
- [ ] 必要APIが成立するまでは、受け付けた材料を消費する実装を有効にしない。

## 2. ブリッジと利用者操作

- [ ] ブロック・BlockEntity・アイテム・レシピ・モデル・翻訳を登録する。
- [ ] 設置者UUID、指令台/納品口座標と次元、許可レシピ、スキーマ版を保存する。
- [ ] リンク時と注文時に所有者・`mayOperate`・距離・ロード状態を検証する。
- [ ] AE2 capability 呼び出しにプレイヤーがいない点を考慮し、設置者承認から権限を解決する。リンクだけで第三者の工場を操作させない。
- [ ] UIに待機、材料受領、実行、納品待ち、出力詰まり、取消、失敗理由を表示する。
- [ ] サーバー側でGUI操作を再検証し、範囲外・別所有者の変更を拒否する。

## 3. AE2 の加工受付

- [ ] `ICraftingMachine.acceptsPlans()` と `getCraftingMachineInfo()` を実装する。
- [ ] `pushPattern(IPatternDetails, KeyCounter[], Direction)` の **全受理か全拒否** を守る。
- [ ] 受付前にアイテム限定、レシピ一致、全材料量、バッファ容量、所有権、接続先、実行枠を検証する。
- [ ] 検査中は状態を変更せず、全検査成功後に原子的に材料所有権を受け取る。部分受理して true を返さない。
- [ ] 対応しないキー、加工条件、材料代替、コンポーネント、返却容器は明示的に対応または拒否する。
- [ ] Singulo の数量上限4096を超える要求は黙って丸めず拒否または明示分割する。

## 4. 材料・注文・出力の一貫性

- [ ] ブリッジID/注文ID/材料/期待出力/進捗を永続化する。
- [ ] 状態遷移を `ACCEPTED → SUBMITTED → RUNNING → OUTPUT_PENDING → COMPLETED` と失敗/取消系で定義する。
- [ ] 書込み順序と再起動復旧を定義し、受付直後・発注直後・納品直後の中断でも重複/消失しない。
- [ ] 共有資材口の材料とAE2預かり材料を予約/隔離し、別注文が横取りできないようにする。
- [ ] 既存在庫の転送を新規製作完了と誤認しない。`fresh=true` だけでは材料隔離を保証しない。
- [ ] 納品口の自動搬出（10tick）や隣接倉庫による横取り・戻り循環を防ぐ。
- [ ] ME容量不足なら完成品を保持し、廃棄も再製作もしない。
- [ ] 本体停止、アンドロイド死亡/充電切れ、経路不達、チャンクアンロードでは安全に保留・失敗へ遷移する。
- [ ] ブリッジ/指令台破壊、取消時の返却・残材・完成品の所有権を確定する。再配置で重複返却しない。
- [ ] 無制限探索や毎tick全ネットワーク走査を避け、再試行間隔と最大保留件数を設定する。

## 5. 受入テスト（Beta公開前の必須条件）

- [ ] 実際の ME端末 → CPU → Provider → ブリッジ → Android → Import Bus で注文1件完了。
- [ ] 同じ注文の連続入力、同一出力の別注文で二重製作/混同が起きない。
- [ ] 材料不足、満杯、未対応レシピ/キー、スタック上限、数量境界を拒否できる。
- [ ] コンポーネント付きアイテムと返却容器の収支が保存される。
- [ ] 受領・発注・作業・納品の各時点で再起動/アンロードし、消失/複製がない。
- [ ] アンドロイド死亡、経路不達、電力不足、ME切断/満杯、指令台破壊を再現。
- [ ] 権限取消、別所有者、距離外、別次元、未ロードへの操作を拒否。
- [ ] 複数ブリッジで工場を共有しても予約材料を奪わず、計算負荷が上限内。
- [ ] 依存不足・依存範囲外で分かるエラー。専用サーバーでクライアント専用クラスをロードしない。
- [ ] GameTest実行件数を固定検証し、全件成功に加え実クライアントで操作と表示を確認。
- [ ] 成功CI成果物・SHA・版/ローダー/依存を確認し、公開承認後にBetaとして配布。

## 6. 初版以降の案

- [ ] Android側から不足材料をMEへ要求: `ICraftingService.beginCraftingCalculation` / `submitJob`、`ICraftingRequester` を検討。
- [ ] 非同期計算をサーバースレッドで待たず、`ICraftingLink` と `getRequestedJobs()` による復旧を設計。
- [ ] 在庫維持や自動パターン登録を追加。MEStorage insert/extract の戻り値は実処理数であり全量ではない。SIMULATE を予約と混同しない。
- [ ] 液体・化学物質・複数出力・複数同時注文は初版の材料収支試験が安定してから扱う。

## 調査元（固定コミット、2026-11-13確認）

- [AE2 ICraftingMachine](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/da9bd7f09a94272a67f9825e4c0f4f6b61983b69/src/main/java/appeng/api/implementations/blockentities/ICraftingMachine.java)
- [AE2 PatternProviderLogic](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/da9bd7f09a94272a67f9825e4c0f4f6b61983b69/src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java)
- [AE2 ICraftingService](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/da9bd7f09a94272a67f9825e4c0f4f6b61983b69/src/main/java/appeng/api/networking/crafting/ICraftingService.java)
- [Singulo WorkConsoleBlockEntity 0.17.1](https://github.com/GenichiMaruo/Singulo/blob/2242440d050232de7359caa1abc81797effd2bd1/src/main/java/io/github/genichimaruo/singulo/android/work/WorkConsoleBlockEntity.java)
- [Singulo DeliveryPortBlockEntity 0.17.1](https://github.com/GenichiMaruo/Singulo/blob/2242440d050232de7359caa1abc81797effd2bd1/src/main/java/io/github/genichimaruo/singulo/android/work/DeliveryPortBlockEntity.java)

AE2の参照コミットは1.21.1ブランチの調査時点。依存を固定した19.2.19とのAPI差分は実装着手時に再照合する。ここにある提案メソッド名や状態は既存APIの存在を意味しない。
