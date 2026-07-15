# AGENTS

## 開発の流れ

作業の流れは `docs/blog/開発の流れ .md` を参照し、従うこと。

## docsの構成

各ディレクトリの責務は以下の通り。

### blog

現況の仕様および開発における意思決定を格納する。

### discussion

新規機能開発や改修に関する方針、検討中の論点、意思決定前の議論を格納する。

### job

daemon で実行されるジョブの概要や実行方式を格納する。

### web

server で公開される Web endpoint の概要を格納する。

## MCPについて

### Metals MCP について

Metals MCP が利用可能な場合は、`compile_file`、`compile_module`、`compile_full` などによる diagnostics 確認を `sbt` コマンドよりも優先する。
