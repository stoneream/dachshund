---
title: GET /spotify/auth/login
---

## 概要

Spotify 認可を開始する。

Spotify の認可画面へ redirect するための認可リクエストを作成する。

## 応答

Spotify の認可画面へ redirect する。

## 補足

この処理は、Spotify callback で state を検証し、認可リクエストの再利用を防ぐための準備を行う。
