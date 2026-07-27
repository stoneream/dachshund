---
title: Docker image の公開と取得
---

## 目的

この文書は、Dachshund の server / daemon を Dockerイメージ としてビルドする手順を記す。
Dockerイメージは sbt-native-packager の Docker 設定と GitHub Actions の `Release` ワークフローとする。

## イメージについて

server と daemon は別イメージとして扱う。

| module | image | port |
| --- | --- | --- |
| `server` | `ghcr.io/stoneream/dachshund-server:<tag>` | `9000` |
| `daemon` | `ghcr.io/stoneream/dachshund-daemon:<tag>` | なし |

runtime の環境変数は [環境変数](環境変数.md) を参照する。

## リリース

GHCR への publish は GitHub Actions の `Release` workflow を実行する。

手動実行時は `tag` を指定する。
`tag` は `vMAJOR.MINOR.PATCH` または `vMAJOR.MINOR.PATCH-prerelease` の形式とする。

release ワークフローでは、同じ `tag` を Git tag、Docker image tag、GitHub release draft の tag として使う。
これは、あとから「どのコミットから、どの Dockerイメージとリリースが作られたか」を同じ名前で追跡できるようにするためである。

また、server image では同じ `tag` が CSS / JavaScript の参照 URL に付く version query にも使われる。  
静的アセットの URL が変わり、ブラウザが前回リリースの CSS / JavaScript を使い続けることを防ぐ。

同名のGitタグが既に存在する場合は、同じリビジョンを指していてもジョブが止まるようになっている。

失敗したリリースを再試行する場合は、新しい tag を使う。
