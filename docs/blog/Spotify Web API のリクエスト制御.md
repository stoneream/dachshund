---
title: Spotify Web API のリクエスト制御
---

## 目的

この文書は、Spotify Web API クライアントのリクエスト開始間隔、rate limit による停止、キューを処理する UseCase の再試行方針を記す。

## クライアント構成

`service.spotify.client.SpotifyClient` は UseCase へ公開する Facade とし、`SpotifyClientImpl` は次の機能別 API へ処理を委譲する。

- `client.api.spotify_followed_artist.SpotifyFollowedArtistsApi`: フォロー中アーティストの取得と cursor 解決
- `client.api.spotify_artist_release.SpotifyArtistReleasesApi`: アーティストリリースの一覧、詳細、track page の取得
- `client.api.spotify_playlist.SpotifyPlaylistApi`: playlist の一覧、作成、unfollow、track 追加

API ごとの application model は各 API package の `model` に置く。
Spotify SDK と sttp の違い、リクエスト開始前の制御、実行時間のログ、HTTP status と SDK 例外の分類は `client.lib.SpotifyRequestExecutor` に閉じる。
Artist Release の SDK model から application model への純粋な変換は `client.api.spotify_artist_release.SpotifyArtistReleaseMapper` が担当する。

機能別 API と共通実行基盤は `service.spotify.client` package 内だけで利用し、HTTP client、SDK、レスポンス decode の詳細を UseCase へ公開しない。

## 制限エラーと停止期間

Spotify Web APIが429を返した場合はrate limitとして扱う。

有効な`Retry-After`があればその期間、なければ`rate-limit-fallback-delay`を停止期間として使う。
`Retry-After` がない、または解釈できない場合だけ `rate-limit-fallback-delay` を使う。

キューへ保存する失敗種別は`rate_limited`とする。

## 処理中バッチの停止

アーティストリリース同期、フォロー中アーティスト同期、ユーザー別新着リリース通知配信などのジョブは、claim 済み target を順次処理する。
最初の`rate_limited`で処理中バッチを停止し、そのtargetに決定した失敗種別と再試行時刻を、未処理のclaim済みtargetにも適用する。
未処理 target は同じ失敗種別、同じ `next_attempt_at` で `SCHEDULED` に戻し、このバッチでは Spotify Web API を呼び出さない。

## 設定

以下を参照。

- `application/src/main/resources/application.conf`
- `daemon/src/main/resources/application.conf`
