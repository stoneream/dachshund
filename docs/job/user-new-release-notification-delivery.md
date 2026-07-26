---
title: user-new-release-notification-delivery
---

## 概要

キューイングされたユーザー別新着リリース通知を配信する。

`user-new-release-events-sync` ジョブが作成した通知キューを取得し、通知種別ごとの方法で配信する。
現時点でこのジョブが扱う通知種別は `PLAYLIST` のみである。

## 通知種別

| 通知種別 | 作成元 | 配送処理 | 備考 |
| --- | --- | --- | --- |
| `PLAYLIST` | `user-new-release-events-sync` | 対象リリースの全トラックをユーザー設定の Spotify playlist に追加する | 現時点でこのジョブが扱う唯一の通知種別。今後、別の通知種別を追加する場合はこの表に行を追加する。 |

### `PLAYLIST`

追加前に playlist items は取得しない。
既に playlist に存在する track URI であっても重複を許容し、追加リクエストの対象にする。

楽曲の追加位置は Spotify API の標準挙動に従い、playlist の末尾とする。

配信対象の track URI は `release_track` から `disc_number`、`track_number`、`id` の順で取得する。
対象 track が存在しない場合、または playlist API が再試行しても解消しない 4xx を返した場合は queue を `BLOCKED` にする。
rate limit、network、server error などの一時的な失敗は retry 時刻を計算して `SCHEDULED` に戻す。
