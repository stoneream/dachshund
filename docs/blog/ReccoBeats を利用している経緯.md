---
title: ReccoBeats を利用している経緯
---

## 背景

このアプリケーションは本番向けの利用形態にあたる Extended Quota Mode の申請要件を満たせないため、Spotify Web API を Development Mode で運用している。
この制約により、楽曲の音響指標を取得する次の API を利用できないという制約がある。

| API | 検討した用途 |
| --- | --- |
| [Get Track's Audio Features](https://developer.spotify.com/documentation/web-api/reference/get-audio-features) | 楽曲全体の key、mode、tempo と energy などの音響指標を取得する。 |
| [Get Track's Audio Analysis](https://developer.spotify.com/documentation/web-api/reference/get-audio-analysis) | 楽曲全体や区間ごとの key、mode、tempo などを取得する代替候補。 |

Spotify の [Quota modes](https://developer.spotify.com/documentation/web-api/concepts/quota-modes) によると、2025年5月15日以降、Extended Quota Mode の新規申請は組織からのみ受け付けており、個人利用のアプリケーションは対象外となっている。
（法的に登録された事業体・組織であること、公開済みで稼働中のサービスを運営していること、月間アクティブユーザーが25万人以上いることなどが含まれる。）
