---
name: software-supply-chain-analysis
description: ソース管理、CI、依存関係、ビルド、成果物、配布経路に起因する脆弱性を分析する。アプリケーション内の成立判定は software-vulnerability-analysis、基盤構成は infrastructure-vulnerability-analysis の対象。
---

# ソフトウェアサプライチェーン分析

ソースへの入力から稼働中の成果物までを追跡し、改ざん、権限の悪用、秘密情報の露出、未検証の成果物が配布される経路を根拠とともに示す。

## 境界

- 供給経路を変更または修正する場合は `software-security-implementation` を使用する。
- 一つの依存関係がアプリケーション内で悪用可能か調べる場合は `software-vulnerability-analysis` を使用する。
- ランナー基盤、クラウドIAM、ネットワーク、成果物保管基盤そのものは `infrastructure-vulnerability-analysis` の対象とする。
- スキャナーの警告や望ましい構成との差だけで、悪用可能な問題と断定しない。

## 参照

対象リポジトリ、ワークフロー、成果物、環境を把握した後、分析前に[サプライチェーンの成立判定チェックリスト](references/analysis-checklist.md)を読む。

期待する防御を実際の供給経路と照合する場合は、`software-security-implementation` の[ソース管理・ビルド・配布](../software-security-implementation/references/source-build-and-delivery.md)を読む。依存関係の実行時の到達性も扱う場合は[依存関係と実行時](../software-security-implementation/references/dependencies-and-runtime.md)を併用する。

## 進め方

1. ソース、依存関係、ワークフロー、ランナー、成果物、配布先と所有者を特定する。
2. 起動条件、信頼できない入力、処理を実行するアクター、クレデンシャル、承認、外部サービスを追跡する。
3. 攻撃者が変更できる入力から、特権処理、秘密情報、成果物、本番環境までの経路を確認する。
4. リポジトリとCIの実効設定、生成記録、署名、成果物のダイジェストを証拠として確認する。
5. 影響するリビジョン、リリース、成果物、環境と、悪用に必要な条件を特定する。
6. 経路を閉じる修復方法、成果物とクレデンシャルの撤回、再ビルドと再配布の検証方法を整理する。

## 出力

各指摘には、対象、信頼できない入力、処理を実行するアクター、必要な権限、供給経路、影響する成果物と環境、根拠、既存対策、優先度、修復方法、撤回または復旧手順、再検証方法を含める。確認できない外部設定と、アプリケーション内の到達性は分離する。
