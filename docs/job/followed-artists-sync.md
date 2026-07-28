---
title: followed-artists-sync
---

## 概要

キューイングされた各ユーザーのフォロー中アーティスト同期を実行する。

`followed-artists-sync-queue` ジョブが作成したキューを取得し、Spotify API からフォロー中アーティストを取得する。
何らかの原因により取得に失敗した場合は、失敗内容に応じて再試行できるようにキューの状態を更新する。

## Spotify API のリクエスト制御

このジョブが使用するSpotify Web APIのリクエスト間隔、rate limit、およびバッチ停止時のキュー更新は[Spotify Web APIのリクエスト制御](<../blog/Spotify Web API のリクエスト制御.md>)に従う。

## 補足

認可切れなど、再試行しても解消できない失敗は再試行せず、ユーザー操作が必要な状態として扱う。
