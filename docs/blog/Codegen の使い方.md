---
title: Codegen の使い方
---

## 目的

この文書は、`codegen` の実行方法と生成対象を説明する。
このリポジトリでは、UseCase、step、server endpoint、daemon、daemon handler、DB テーブルメタデータ、DB reader/writer スケルトンを `codegen` で生成できる。

実装配置、DB アクセス、daemon の責務は `docs/blog/アプリケーション構成.md` を参照。
DB スキーマ変更時のマイグレーション、tbls スキーマ、DB メタデータの同期手順は `docs/blog/開発の流れ .md` を参照。

## 基本コマンド

生成は `codegen/runMain` から実行する。
手順書や再現用のコマンドでは、次のように 1 回の sbt 起動で生成まで実行する。

```bash
sbt "codegen/runMain io.github.stoneream.dachshund.codegen.DachshundCodeGenMain --usecase spotify/auth/signup --name SpotifyAuthSignup"
```

複数の生成を続けて試す場合は、対話型の sbt シェルを使う。
ルートプロジェクトから実行する場合は、シェル内でも `codegen/runMain` を指定する。

```bash
sbt
```

```bash
# sbt シェル
codegen/runMain io.github.stoneream.dachshund.codegen.DachshundCodeGenMain --usecase spotify/auth/signup --name SpotifyAuthSignup
```

`project codegen` に切り替えた場合は、`runMain` で実行する。

```bash
# sbt シェル
project codegen
runMain io.github.stoneream.dachshund.codegen.DachshundCodeGenMain --usecase spotify/auth/signup --name SpotifyAuthSignup
```

コマンドラインからプロジェクト切り替えまで含めて実行する場合は、次のように書ける。

```bash
sbt "project codegen" "runMain io.github.stoneream.dachshund.codegen.DachshundCodeGenMain --usecase spotify/auth/signup --name SpotifyAuthSignup"
```

## 生成前の確認

`--dry-run` を付けると、ファイルを書き込まずに生成予定ファイルと差し込み用コードだけを表示する。

```bash
sbt "codegen/runMain io.github.stoneream.dachshund.codegen.DachshundCodeGenMain --endpoint spotify/auth/signup --name SpotifyAuthSignup --dry-run"
```

既存ファイルを上書きする場合は、明示的に `--force` を付ける。

```bash
sbt "codegen/runMain io.github.stoneream.dachshund.codegen.DachshundCodeGenMain --endpoint spotify/auth/signup --name SpotifyAuthSignup --force"
```

## UseCase を生成する

UseCase は `application/src/main/scala/io/github/stoneream/dachshund/usecase/...` に生成される。
`--usecase` にはスラッシュ区切りのモジュールパス、`--name` には UpperCamelCase の名前を指定する。

```bash
sbt "codegen/runMain io.github.stoneream.dachshund.codegen.DachshundCodeGenMain --usecase spotify/auth/signup --name SpotifyAuthSignup"
```

この例では次のファイルが生成される。

- `SpotifyAuthSignupUseCase.scala`
- `SpotifyAuthSignupUseCaseInput.scala`
- `SpotifyAuthSignupUseCaseOutput.scala`
- `SpotifyAuthSignupUseCaseException.scala`

## step を生成する

step は UseCase 配下の `step` パッケージに生成される。
`--step` には対象 UseCase のモジュールパス、`--name` には step 名を指定する。

```bash
sbt "codegen/runMain io.github.stoneream.dachshund.codegen.DachshundCodeGenMain --step spotify/auth/signup --name BuildAuthorizationRequest"
```

生成されるクラスは UseCase パッケージ内に閉じた `private[...]` スコープを持つ。

## server endpoint を生成する

server endpoint は controller、handler、renderer の 3 ファイルを生成する。
既存の `Root` には自動追記せず、手動反映用の差し込み用コードを標準出力に出す。

```bash
sbt "codegen/runMain io.github.stoneream.dachshund.codegen.DachshundCodeGenMain --endpoint spotify/auth/signup --name SpotifyAuthSignup"
```

この例では次のファイルが生成される。

- `SpotifyAuthSignupController.scala`
- `SpotifyAuthSignupHandler.scala`
- `SpotifyAuthSignupRenderer.scala`

## daemon を生成する

daemon は handler、job、ジョブ設定を生成する。
手動反映用の差し込み用コードを標準出力に出す。

```bash
sbt "codegen/runMain io.github.stoneream.dachshund.codegen.DachshundCodeGenMain --daemon spotify/access-token-refresh --name SpotifyAccessTokenRefresh"
```

この例では次のファイルが生成される。

- `SpotifyAccessTokenRefreshHandler.scala`
- `SpotifyAccessTokenRefreshJob.scala`
- `SpotifyAccessTokenRefreshJobConfig.scala`

## daemon handler のみ生成する

既存の daemon job を維持したまま handler の雛形だけを作る場合は、`--daemon-handler` を使う。
出力先と handler の形は `--daemon` で生成される handler と同じで、job、ジョブ設定、差し込み用コードは生成しない。

```bash
sbt "codegen/runMain io.github.stoneream.dachshund.codegen.DachshundCodeGenMain --daemon-handler spotify/access-token-refresh --name SpotifyAccessTokenRefresh"
```

この例では次のファイルが生成される。

- `SpotifyAccessTokenRefreshHandler.scala`

## DB テーブルメタデータを生成する

DB テーブルメタデータは `tbls/schema/schema.json` を読み、`application/src/main/scala/io/github/stoneream/dachshund/infra/db/generated` に生成される。
`--db-metadata` と `--table` を指定する。

```bash
sbt "codegen/runMain io.github.stoneream.dachshund.codegen.DachshundCodeGenMain --db-metadata --table spotify_authorizations"
```

スキーマ JSON の場所を変える場合は `--schema-json` を指定する。

```bash
sbt "codegen/runMain io.github.stoneream.dachshund.codegen.DachshundCodeGenMain --db-metadata --table spotify_authorizations --schema-json tmp/schema.json"
```

DB テーブルメタデータが生成するものは、テーブル名、カラム名、DB row の `case class`、`WrappedResultSet` から DB row へのマッパー、監査カラム名である。

## DB reader/writer スケルトンを生成する

DB reader/writer スケルトンは `application/src/main/scala/io/github/stoneream/dachshund/infra/db/.../reader|writer` に生成される。
`--db-accessor`、`--reader`、`--writer` を指定する。

```bash
sbt "codegen/runMain io.github.stoneream.dachshund.codegen.DachshundCodeGenMain --db-accessor spotify/auth --reader SpotifyAuthorizationReader --writer SpotifyAuthorizationWriter"
```

この例では次のファイルが生成される。

- `reader/SpotifyAuthorizationReader.scala`
- `writer/SpotifyAuthorizationWriter.scala`

## モジュールパスと名前の指定

`--usecase`、`--step`、`--endpoint`、`--daemon`、`--daemon-handler`、`--db-accessor` のパスはスラッシュ区切りの小文字で指定する。
ハイフンはパッケージパスではアンダースコアに変換される。

```bash
--daemon spotify/access-token-refresh
```

この例ではパッケージパスは `spotify.access_token_refresh` になり、daemon 名は `spotify-access-token-refresh` になる。

`--name`、`--reader`、`--writer` は UpperCamelCase で指定する。
`UseCase`、`Step`、`Handler` などの接尾辞は、足りない場合は codegen が補う。

## スニペットコードの扱い

server endpoint と daemon は、既存ファイルへ自動追記しない。
生成された差し込み用コードは標準出力に表示される。
