# 0.1.0 の 1.20.1 移植記録

状態: **移植中。ビルド・実ゲーム検証はまだ完了していません。公開不可。**

## 対象

- Minecraft 1.20.1、Java 17
- Forge 47.4.26 / NeoForge 47.1.106（同じ net.minecraftforge API の世代）
- AE2 Forge 15.4.12。公式タグ `forge/v15.4.12`、ソース `5a544ce4aa4d51a1e92a21d316bb89c4a5f4a75a`
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
- [ ] 親側レビュー修正を同期
- [ ] draft PR作成（マージ・公開はこの依頼の対象外）

参考:
- https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/forge/v15.4.12
- https://github.com/neoforged/ModDevGradle/blob/main/LEGACY.md
