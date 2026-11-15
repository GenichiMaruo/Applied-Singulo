# Applied Singulo 0.1.0 Beta 実装チェックリスト

作成日: 2026-11-13、更新: 2026-11-14。**チェックのない項目は未完了です。** 実装済みのブリッジは [アンドロイド加工ブリッジ](bridge.md)、手動経路は [開発コマンド](development-commands.md)。
対象: Minecraft 1.21.1 / NeoForge / **公開版 Singulo 0.18.0 以上 (必須)** / AE2 19.2.19。

## ねらいと最小構成

「ME 端末で注文すると、Singulo のアンドロイドが工場を動かし、完成品が ME に戻る」を初版の到達点とする。

`Crafting CPU → Pattern Provider → Android 連携ブリッジ → 作業指令台 → アンドロイド → 専用納品口 → Import Bus → ME`

（実装では専用納品口の代わりに、ブリッジに接した Singulo 納品口から完成品を受け取り、Import Bus ではなく Pattern Provider の返却口から ME に戻す。）

ブリッジを Pattern Provider に隣接させ、AE2 公開 capability `AECapabilities.CRAFTING_MACHINE` / `ICraftingMachine` を実装する。既存の加工パターンと Import Bus を利用し、初版から独自 ME ノードや端末を作らない。

**0.1.0 の範囲:** アイテムのみ、指令台1台へのリンク、ブリッジごと同時1注文、単一主出力、登録済みレシピ。液体・化学物質、複数主出力、在庫維持、逆方向の素材発注、自動パターン生成、遠隔次元は後回し。

## 0. 土台（今回準備したもの）

- [x] `1.21.1` 維持ブランチと `feature/0.1.0` 開発ブランチ。
- [x] Java 21 / NeoForge の起動入口、必須依存メタデータ、Gradle wrapper。
- [x] `Applied-Singulo-0.1.0-1.21.1N.jar` の命名と Beta 区分。
- [x] ビルド・配布物検査用 CI と依存 JAR 非同梱の方針。
- [x] 実依存入りクライアントの起動確認 (`./gradlew runClient` 引数なし、2026-11-14)。
- [ ] 実依存入り専用サーバーの起動確認。
- [x] 実装後の GameTest を追加し、0件実行を失敗にする (13件固定)。
- [x] 依存 JAR (公開版 Singulo 0.18.0 / AE2 / GuideME) を固定 URL・SHA-256 で自動取得。

## 1. 最初に解決するAPI境界（着手ゲート）

Singulo 0.17.1 の `WorkConsoleBlockEntity.start(...)` は `ServerPlayer` を受け、成功時 null / 失敗時 Component を返す。安定した外部受付ID APIではない。`orders()` の可変リストを直接変更しない。完了でも取消でも注文が消えるので **注文が見つからないことを成功扱いしない**。

- [x] 本体側に正式API (`AndroidOrders`) を追加する方式を採用し、Singulo 0.18.0 で公開済み。
- [x] `submit / status / cancel / acknowledge / snapshot` と明示状態 (RUNNING/COMPLETED/CANCELLED/FAILED/REJECTED/UNKNOWN/UNAVAILABLE)。材料予約は API の範囲外。
- [x] 受付 UUID による重複抑止、永続化、状態照会、失敗理由、取消。
- [x] 反射を使わず、指令台所有者の UUID で権限を検証する自動注文経路。
- [x] 受け付けた材料は、注文受付が成功した場合だけ預かる（拒否時はロールバック）。

## 2. ブリッジと利用者操作

- [x] ブロック・BlockEntity・アイテム・レシピ・モデル・翻訳 (en/ja)・ルートテーブル・クリエイティブタブ。
- [x] 設置者UUID、注文ごとの指令台座標、スキーマ版を保存。リンクは隣接で判定するため次元・距離の保存は不要。許可レシピの保存は未実装。
- [x] 注文時に所有者・`mayOperate`・ロード状態を本体 API が検証する。
- [x] 設置者 UUID を actor にし、指令台の持ち主と一致しなければ本体が拒否する。
- [x] 右クリック/コマンドで接続、実行中注文、預かり量、出力詰まり、拒否理由を表示（所有者・OPのみ）。
- [ ] 専用GUI画面（現状はチャット表示）。

## 3. AE2 の加工受付

- [x] `ICraftingMachine.acceptsPlans()` と `getCraftingMachineInfo()` を実装する。
- [x] `pushPattern(IPatternDetails, KeyCounter[], Direction)` の **全受理か全拒否** を守る。
- [x] 受付前にアイテム限定、全材料量、バッファ容量、所有権、接続先、実行枠を検証する（レシピ一致は本体の注文受付で判定）。
- [x] 検査中は状態を変更せず、全検査成功後に材料を預かる。部分受理して true を返さない。
- [ ] 対応しないキー、加工条件、材料代替、コンポーネント、返却容器は明示的に対応または拒否する。
- [x] Singulo の数量上限4096を超える要求は拒否する。

## 4. 材料・注文・出力の一貫性

- [x] 注文UUID/指令台/期待出力/納品数/返却数/終端理由と、預かり・返却待ちアイテムを永続化する。
- [ ] 状態遷移を `ACCEPTED → SUBMITTED → RUNNING → OUTPUT_PENDING → COMPLETED` と失敗/取消系で定義する。
- [ ] 書込み順序と再起動復旧を定義し、受付直後・発注直後・納品直後の中断でも重複/消失しない。
- [ ] 共有資材口の材料とAE2預かり材料を予約/隔離し、別注文が横取りできないようにする。
- [ ] 既存在庫の転送を新規製作完了と誤認しない。`fresh=true` だけでは材料隔離を保証しない。
- [ ] 納品口の自動搬出（10tick）や隣接倉庫による横取り・戻り循環を防ぐ。
- [x] ME容量不足なら完成品を保持し、廃棄も再製作もしない。
- [x] 終端結果を記録してから本体受付履歴を acknowledge し、履歴枠を自動で空ける。
- [x] 納品口からは、実行中/直近終端の注文が待っている物と数だけを受け取る。
- [ ] 本体停止、アンドロイド死亡/充電切れ、経路不達、チャンクアンロードでは安全に保留・失敗へ遷移する。
- [ ] ブリッジ/指令台破壊、取消時の返却・残材・完成品の所有権を確定する。再配置で重複返却しない。（ブリッジ破壊時: 注文取消＋預かり・完成品をドロップ、は実装済み）
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

## 6.5 ME 直結・レシピ共有・まとめ注文（2026-11-14 追加要望）

- [x] **まとめ注文**: 同じ出力の push を 1 件の Android 注文に統合（10 tick 無投入／100 tick 経過／4096 個で発注、拒否時は預かったまま再試行、受理済み注文は再送しない）。ブリッジと ME インターフェースで共通 (`AndroidOrderQueue`)。
- [x] **レシピ共有**: 指令台のレシピ棚を AE2 の `ICraftingProvider` パターンとして自動公開（`AndroidRecipePattern`、100 tick ごとに差分更新）。
- [x] **ME 直結ブロック**: アンドロイドMEインターフェース（`IInWorldGridNodeHost`、チャンネル1、2 AE/t）。
- [x] **Singulo 0.19.0 個別預かりへ移行**（開発版 `b43db6ba`）: まとめた材料を `AndroidEscrows.submit` で注文ごとに渡し、完成品・余り材料を注文 ID で `claim`、空になってから `acknowledge`。資材口・納品口・隣接配置は不要に。注文先は作業場所カードか隣接配置で指定。
- [x] 共有するレシピを個別預かりで動く作業台レシピ（燃料・触媒・液体なし）に限定。ブリッジも同じレシピがなければ push を断る。
- [x] 壊した装置の発注済み注文を `OrphanedEscrows` で回収し、元の場所に落とす。
- [x] GameTest 18 件: 実アンドロイドでの通し（まとめ 4 回→1 注文、共有チェスト不使用、16 個回収、預かり解放）と、破壊後の材料回収を含む。
- [ ] 実クライアントで ME 端末 → インターフェース → アンドロイド → ME の通し確認（AE2 クラフト CPU 経由）。
- [x] 指令台から離れた完全リモート接続（0.19.0 個別預かりで実現）。
- [ ] 加工機械・手順・燃料・触媒レシピの隔離（Singulo 側の対応待ち）。
- [ ] 液体・化学物質レシピの共有。

## 7. 追加提案（ChatGPT 案以外、2026-11-14）

実装済み:
- [x] `/appliedsingulo snapshot <指令台>` — 0.18.0 の `snapshot` API で自分の外部注文と履歴の残り枠を一覧。
- [x] `/appliedsingulo bridge <座標> status|cancel` — ブリッジの状態確認と、実行中 Android 注文の一括取消。
- [x] Pattern Access Terminal 用の機械名・接続先ツールチップ。
- [x] 終わった注文の余り材料を、注文ごとに ME へ自動返却。
- [x] 指令台の受付履歴が消えた注文を `LOST` として扱い、自動再送しない。

次の候補:
- [ ] **AE2 ジョブ連動キャンセル**: Android 注文が FAILED/CANCELLED になったら AE2 側ジョブも止める（`ICraftingService` から該当 CPU を特定する方法を調査）。
- [x] **材料の厳密隔離**: Singulo 0.19.0 の `AndroidEscrows`（注文ごとの個別預かり）で実装（[採用状況](singulo-api-proposal.md)）。
- [ ] **ME → 資材口の材料供給ガイド**: ME Interface を資材口に接して在庫を見せる構成をドキュメント化（コード不要）。
- [ ] **Android 在庫の ME 表示**: 指令台の在庫を Storage Bus 互換の `MEStorage` で読み取り専用公開。
- [ ] **レッドストーン/コンパレータ出力**: 実行中・出力詰まり・失敗を信号で出す。
- [ ] **Jade/WTHIT 表示**: 見ただけで状態が分かるようにする（任意依存）。
- [ ] **GuideME ページ**: AE2 ガイドにブリッジの配置図を追加。
- [ ] **許可パターン一覧**: ブリッジが受ける出力アイテムを制限し、誤ったパターンで指令台を占有させない。

## 調査元（固定コミット、2026-11-13確認）

- [AE2 ICraftingMachine](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/da9bd7f09a94272a67f9825e4c0f4f6b61983b69/src/main/java/appeng/api/implementations/blockentities/ICraftingMachine.java)
- [AE2 PatternProviderLogic](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/da9bd7f09a94272a67f9825e4c0f4f6b61983b69/src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java)
- [AE2 ICraftingService](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/da9bd7f09a94272a67f9825e4c0f4f6b61983b69/src/main/java/appeng/api/networking/crafting/ICraftingService.java)
- [Singulo WorkConsoleBlockEntity 0.17.1](https://github.com/GenichiMaruo/Singulo/blob/2242440d050232de7359caa1abc81797effd2bd1/src/main/java/io/github/genichimaruo/singulo/android/work/WorkConsoleBlockEntity.java)
- [Singulo DeliveryPortBlockEntity 0.17.1](https://github.com/GenichiMaruo/Singulo/blob/2242440d050232de7359caa1abc81797effd2bd1/src/main/java/io/github/genichimaruo/singulo/android/work/DeliveryPortBlockEntity.java)

AE2の参照コミットは1.21.1ブランチの調査時点。依存を固定した19.2.19とのAPI差分は実装着手時に再照合する。ここにある提案メソッド名や状態は既存APIの存在を意味しない。


## 実装済みの第一段階（2026-11-13、ローカルのみ）

- [x] 本体 feature/0.18.0 に AE2 非依存の注文 API を追加し、コミット。
- [x] UUID による再送識別、所有者確認、状態取得、取消、終端履歴の確認・保存。
- [x] Applied 側で実 AE2 パターンの単一アイテム出力を読み、検証用コマンドから API を使用。
- [x] 本体 API 8件、Applied 8件の実ゲームテストと配布 JAR 検査。
- [ ] Pattern Provider からの自動受付と材料の預かり・返却。
- [ ] ME への納品とクラフト CPU 側の完了整合性。
- [ ] 本体 API コミットの push 後、Applied 開発ブランチを push して CI 検証。

詳細と制約は [検証記録](verification.md) と [開発コマンド](development-commands.md) を参照。
