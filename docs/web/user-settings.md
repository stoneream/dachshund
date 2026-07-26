---
title: GET /user-settings
---

## 概要

ユーザー設定画面を表示する。

新着リリース通知用 playlist 設定を表示する。
ユーザーは playlist URL / ID や playlist 名を指定しない。

## 応答

ログイン済みの場合は設定画面を表示する。
未ログインの場合は `/spotify/auth/login` へ redirect する。

## 保存

`POST /user-settings` はログイン済みの場合に `/user-settings` へ redirect し、flash で保存完了メッセージを表示する。

`newReleasePlaylistEnabled=true` の場合、未設定なら Spotify 上に private playlist を自動作成し、`user_playlist_setting` に保存する。
既定の playlist 名は `Dachshund Radar` とする。
Spotify 上に同名 playlist が存在する場合は、`Dachshund Radar_<UUID>` で作成する。

`newReleasePlaylistEnabled` が送信されない場合は、新着リリース playlist 設定を無効化する。

## 補足

既存の新着リリースイベントへの通知キュー backfill はこの画面では行わない。
