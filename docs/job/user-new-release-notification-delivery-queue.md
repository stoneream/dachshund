---
title: user-new-release-notification-delivery-queue
---

## 概要

キューイングされたユーザー別新着リリース通知を配信する。

`user-new-release-events-sync` ジョブが作成した通知キューを取得し、通知種別ごとの方法で配信する。
現時点でこのジョブが扱う通知種別は `PLAYLIST` のみである。

## 通知種別

| 通知種別 | 作成元 | 配送処理 | 備考 |
| --- | --- | --- | --- |
| `PLAYLIST` | `user-new-release-events-sync` | 対象リリースの全トラックをユーザー設定の Spotify playlist に追加する | 現時点でこのジョブが扱う唯一の通知種別。今後、別の通知種別を追加する場合はこの表に行を追加する。 |

## Spotify API のリクエスト制御

このジョブが使用するSpotify Web APIのリクエスト間隔、rate limit、およびバッチ停止時のキュー更新は[Spotify Web APIのリクエスト制御](<../blog/Spotify Web API のリクエスト制御.md>)に従う。
