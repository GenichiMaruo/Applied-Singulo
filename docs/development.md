# 開発・配布ルール

- 既定・維持ブランチ `1.21.1`、開発は `feature/<version>`、修正は `fix/<topic>`。
- JAR は `Applied-Singulo-<version>-<Minecraft><loader>.jar`。NeoForge=N、将来Forgeを作る場合はF。
- 0.1.0 の配布区分は Beta。公開承認後のタグは `v0.1.0`。今回はタグ・Release・配布サイト公開をしません。
- `./gradlew --no-daemon build` と `python3 tools/check_distribution.py` を通す。
- 実装後は実依存でクライアント・専用サーバー・GameTest を追加し、0件実行は失敗扱いにする。
- CI は contents:read のみ。秘密情報を使わず、pull_request_target は使わない。
- 公開時は成功CIの同一成果物を昇格し、依存・ローダー・既存公開物・ハッシュを確認。自動再送やタグ移動で検証を迂回しない。
- AE2 は appeng.api を優先。内部クラスの mixin/reflection を前提にしない。Singulo には安定した注文受付APIがあると仮定せず、薄いアダプタと明示的な注文状態を設計する。
- メタデータの依存範囲は動作保証ではない。広げる際は実ゲームで再検証する。
