---
title: GET /spotify/track/detail/<track_id>
---

## 概要

指定した楽曲の基本情報と音響指標を表示する。

ジャケット、曲名、アーティスト名、アルバム名に加え、Key、Camelot、BPM などを確認できる。

## 応答

未ログイン、または Spotify の再認可が必要な場合は `/spotify/auth/login` へ redirect する。
楽曲が存在しない場合は `404 Not Found` とする。
音響指標が未登録、またはアクセスが許可されていない場合は、基本情報を表示したまま案内を表示する。
取得できない指標は `—` とする。

取得に失敗した場合は再試行の案内を表示する。
rate limit で待機時間が得られた場合は、再試行までの秒数を案内する。

## 補足

基本情報は Spotify、音響指標は ReccoBeats から取得する。
採用の背景は [ReccoBeats を利用している経緯](<../blog/ReccoBeats を利用している経緯.md>) を参照。
