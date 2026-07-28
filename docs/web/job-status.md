---
title: ジョブ状態
---

## 概要

ジョブ状態ページは、daemon が利用する queue テーブルやジョブによって作成された履歴テーブルの状態を Web から確認するための endpoint である。

## endpoint

| method | path | 概要 |
| --- | --- | --- |
| `GET` | `/job/status` | 対象ジョブ一覧を表示する。 |
| `GET` | `/job/status/<job-name>` | 指定したジョブの処理状態を表示する。 |

## クエリパラメーター

| query | 概要 |
| --- | --- |
| `status` | queue 詳細一覧に表示する queue status。`scheduled`、`processing`、`succeeded`、`failed`、`blocked`、`skipped` を複数指定できる。未指定の場合はすべての status を対象にする。 |
| `page` | 詳細一覧のページ番号。未指定の場合は `1`。1 ページあたり 100 件を表示する。 |

`status` のクエリ値には小文字のみを使用する。
定義されていない値や大文字の値は無視し、有効な値が残らない場合は詳細一覧を 0 件にする。
status filter と queue 詳細一覧の表示には DB 値を使用する。
DB 値と意味は `docs/job/README.md` のキュー状態に従う。

## 対象ジョブ

| job-name | 対象テーブル |
| --- | --- |
| `spotify-access-token-refresh` | `user_spotify_authorization_refresh_queue` |
| `followed-artists-sync` | `followed_artist_sync_queue` |
| `artist-releases-sync` | `artist_release_sync_queue` |
| `user-new-release-events-sync` | `user_new_release_event`, `user_new_release_notification_delivery_queue` |
| `user-new-release-notification-delivery-queue` | `user_new_release_notification_delivery_queue` |
