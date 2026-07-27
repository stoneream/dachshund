---
title: ジョブ状態
---

## 概要

ジョブ状態ページは、daemon が利用する queue テーブルの状態を Web から確認するための endpoint である。
このページは既存 DB に保存されている queue の状態を表示する。
daemon プロセスの稼働状態、最終実行時刻、実行履歴は表示しない。

## endpoint

| method | path | 概要 |
| --- | --- | --- |
| `GET` | `/job/status` | 対象ジョブ一覧を表示する。 |
| `GET` | `/job/status/<job-name>` | 指定したジョブの queue 状態を表示する。 |

`/job/status` と `/job/status/<job-name>` はログイン必須である。
未ログインの場合は Spotify ログインへリダイレクトする。

## query

| query | 概要 |
| --- | --- |
| `status` | 詳細一覧に表示する queue status。複数指定できる。未指定の場合はすべての status を対象にする。 |
| `page` | 詳細一覧のページ番号。未指定の場合は `1`。1 ページあたり 100 件を表示する。 |

query は `/job/status/<job-name>` の詳細ページでのみ使用する。
status 別の集計は `status` filter に関係なく対象テーブル全体を表示する。
`status` と `page` は詳細一覧にだけ適用する。

## 対象ジョブ

| job-name | 対象テーブル |
| --- | --- |
| `spotify-access-token-refresh` | `user_spotify_authorization_refresh_queue` |
| `followed-artists-sync` | `followed_artist_sync_queue` |
| `artist-releases-sync` | `artist_release_sync_queue` |
| `user-new-release-notification-delivery` | `user_new_release_notification_queue` |
