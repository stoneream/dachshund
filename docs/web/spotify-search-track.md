---
title: GET /spotify/search-track
---

## 概要

ログインユーザーが Spotify の楽曲を検索する画面を表示する。
検索欄に曲名やアーティスト名などを入力し、検索ボタンまたは Enter で検索する。
入力中の自動検索は行わない。

## リクエスト

| パラメーター | 内容 |
| --- | --- |
| `q` | 検索語。前後の空白を除く。未指定または空白のみの場合はフォームを表示し、Spotify API を呼び出さない。 |
| `offset` | 検索結果の開始位置。既定値は `0`。整数の `0` から `1000` までを受け付け、それ以外は `400 Bad Request` を返す。 |

検索には [Spotify Search API](https://developer.spotify.com/documentation/web-api/reference/search) を使用し、`type=track`、`limit=10` を指定する。
ログインユーザーの Spotify access token を使用し、`market` は指定せずアカウントの国を対象とする。
既存のトークン取得・更新基盤と Spotify リクエスト制御を利用する。

## 応答

ジャケット、曲名、アーティスト名、アルバム名を Spotify の検索順で表示する。
検索結果が空の場合は該当なしを表示し、ジャケットがない楽曲も一覧に表示する。
再生、プレイリスト追加、検索結果の保存は行わない。

「前へ」「次へ」は検索語を保持して 10 件ずつ移動する。
次ページは Spotify の応答に次ページがあり、移動先の offset が `1000` 以下の場合だけ表示する。
検索フォームの送信は常に先頭ページから検索する。

| 状態 | 応答 |
| --- | --- |
| 未ログイン | `/spotify/auth/login` へ redirect する。 |
| Spotify 認可情報不足・再認可要求 | `/spotify/auth/login` へ redirect する。 |
| Spotify が 401 を返す | トークンを強制更新して 1 回だけ再試行する。再試行も 401 の場合はログインへ redirect する。 |
| Spotify の rate limit | `429 Too Many Requests` と再試行案内を表示する。待機時間が得られた場合は `Retry-After` を付ける。 |
| 403、通信障害、一時的なトークン解決失敗など | `503 Service Unavailable` と検索失敗の案内を表示する。 |

429 と 503 の画面では検索語を入力欄に保持する。
rate limit と一般的な検索失敗は、それぞれ `SpotifySearchTrackUseCaseException.RateLimited` と `Unavailable` として扱い、Renderer の `failure` で HTTP 応答へ変換する。
正常 Output は初期表示と検索結果だけを表す。
Spotify のエラー本文やトークンは画面へ出力しない。
HTTP リクエストログの `q` はマスクし、検索語や楽曲情報をアプリケーションログへ出力しない。
