# 最初の実装: AE2パターンからAndroid注文へ

これは開発用の手動経路です。MEネットワークのクラフト依頼や材料を受け取るブリッジは未実装です。

## 必要な構成

- Minecraft 1.21.1 / NeoForge 21.1.256 / Java 21
- **AndroidOrders API が追加された未公開 Singulo 0.18.0 開発版**。一般公開済み0.17.1では動きません。
- AE2 19.2.19 / GuideME 21.1.1
- 自分が所有する作業指令台、権限のある作業者、登録済みレシピ、既存の資材口に材料。

メタデータは [0.18.0,0.19.0) ですが、0.18.0 という版番号だけでAPIの存在は保証できません。必ずAPI追加コミット以降の本体を使用してください。

## 操作

1. `/appliedsingulo newid` で受付UUIDを1つ生成・控える。
2. AE2の符号化済みパターンをメインハンドに持つ。アイテム入力・単一アイテム出力のみ。
3. `/appliedsingulo submit <指令台x y z> <受付UUID> <バッチ数>` で注文する。
4. `/appliedsingulo status <指令台x y z> <受付UUID>` で明示状態と納品数を見る。
5. 必要なら `/appliedsingulo cancel <指令台x y z> <受付UUID>`。
6. 終了結果を確認後、`/appliedsingulo acknowledge <指令台x y z> <受付UUID>` で履歴枠を解放する。**解放したUUIDは再利用しない。**

コマンドはプレイヤーのみ・64ブロック以内・読み込み済み指令台に限定します。APIには実際のログインプレイヤーのUUIDを渡し、権限を迂回しません。作業者は本体の権限条件に従って自動選択します。

**パターンから使うのは希望する出力と数量だけです。** AE2パターンに記録された加工手順がそのまま実行されるわけではなく、指令台に登録済みの本体レシピで新規製作します。パターン本体やME材料は消費しません。資材口の材料を使い、納品は指令台の通常出力へ行います。

## 安全上の境界

- 同じ受付UUIDの再送は本体APIが処理する。内容を変えて同じIDを使わない。
- 注文不在を完了とはみなさない。COMPLETED/CANCELLED/FAILED 等の明示結果を見る。
- 本体APIは信頼するサーバー側アドオン向けで、UUID文字列だけを認証情報としてネットワークから受け付けるものではない。
- 再起動後も本体の受付履歴が照会できる。履歴が上限に達したら新規受付は拒否される。
- 全自動化は材料隔離/予約・出力の帰属・停止/破壊時の回収を完成させてから有効化する。

## テスト

ビルドとGameTestはいずれも3依存JARを指定します。

```sh
./gradlew --no-daemon build -PsinguloJar=/path/Singulo-0.18.0-1.21.1N.jar -Pae2Jar=/path/appliedenergistics2-19.2.19.jar -PguideMeJar=/path/guideme-21.1.1.jar
./gradlew --no-daemon runGameTestServer -PsinguloJar=/path/Singulo-0.18.0-1.21.1N.jar -Pae2Jar=/path/appliedenergistics2-19.2.19.jar -PguideMeJar=/path/guideme-21.1.1.jar
python3 tools/check_distribution.py
```

GameTestは実行件数を確認すること。起動だけ・0件は成功扱いしない。配布JARにはテストクラス・テスト構造を含めない。
