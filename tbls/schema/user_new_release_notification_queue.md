# user_new_release_notification_queue

## Description

ユーザー別新着リリース通知キュー

<details>
<summary><strong>Table Definition</strong></summary>

```sql
CREATE TABLE `user_new_release_notification_queue` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_new_release_event_id` bigint unsigned NOT NULL COMMENT 'ユーザー別新着リリース履歴ID',
  `release_notification_type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PLAYLIST' COMMENT 'リリース通知種別(PLAYLIST)',
  `playlist_setting_id` bigint unsigned NOT NULL COMMENT 'ユーザープレイリスト設定ID',
  `status` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SCHEDULED' COMMENT 'キュー状態(SCHEDULED, PROCESSING, SUCCEEDED, FAILED, BLOCKED, SKIPPED)',
  `next_attempt_at` datetime DEFAULT NULL COMMENT '次回試行日時',
  `attempt_count` int NOT NULL DEFAULT '0' COMMENT '試行回数',
  `last_failed_at` datetime DEFAULT NULL COMMENT '最終失敗日時',
  `last_error_type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '最終エラー種別',
  `lock_token` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '処理ロックトークン',
  `locked_until` datetime DEFAULT NULL COMMENT '処理ロック期限',
  `last_attempted_at` datetime DEFAULT NULL COMMENT '最終試行日時',
  `completed_at` datetime DEFAULT NULL COMMENT '処理完了日時',
  `spotify_snapshot_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'SpotifyプレイリストスナップショットID',
  `created_at` datetime NOT NULL COMMENT '作成日時',
  `updated_at` datetime NOT NULL COMMENT '更新日時',
  `deleted_at` datetime DEFAULT NULL COMMENT '削除日時',
  `created_user` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作成者',
  `updated_user` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '更新者',
  `deleted_user` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '削除者',
  `deleted` bigint NOT NULL DEFAULT '0' COMMENT '論理削除フラグ(0=有効、1=無効)',
  `lock_version` bigint NOT NULL DEFAULT '0' COMMENT '楽観ロックバージョン',
  PRIMARY KEY (`id`),
  UNIQUE KEY `id` (`id`),
  UNIQUE KEY `uq_user_new_release_notification_queue_event_type_playlist` (`user_new_release_event_id`,`release_notification_type`,`playlist_setting_id`),
  KEY `idx_user_new_release_notification_queue_playlist_setting` (`playlist_setting_id`,`deleted`,`status`),
  KEY `idx_user_new_release_notification_queue_target` (`deleted`,`status`,`next_attempt_at`,`locked_until`,`id`),
  CONSTRAINT `fk_user_new_release_notification_queue_event_id` FOREIGN KEY (`user_new_release_event_id`) REFERENCES `user_new_release_event` (`id`),
  CONSTRAINT `fk_user_new_release_notification_queue_playlist_setting_id` FOREIGN KEY (`playlist_setting_id`) REFERENCES `user_playlist_setting` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ユーザー別新着リリース通知キュー'
```

</details>

## Columns

| Name | Type | Default | Nullable | Extra Definition | Children | Parents | Comment |
| ---- | ---- | ------- | -------- | ---------------- | -------- | ------- | ------- |
| id | bigint unsigned |  | false | auto_increment |  |  |  |
| user_new_release_event_id | bigint unsigned |  | false |  |  | [user_new_release_event](user_new_release_event.md) | ユーザー別新着リリース履歴ID |
| release_notification_type | varchar(255) | PLAYLIST | false |  |  |  | リリース通知種別(PLAYLIST) |
| playlist_setting_id | bigint unsigned |  | false |  |  | [user_playlist_setting](user_playlist_setting.md) | ユーザープレイリスト設定ID |
| status | varchar(255) | SCHEDULED | false |  |  |  | キュー状態(SCHEDULED, PROCESSING, SUCCEEDED, FAILED, BLOCKED, SKIPPED) |
| next_attempt_at | datetime |  | true |  |  |  | 次回試行日時 |
| attempt_count | int | 0 | false |  |  |  | 試行回数 |
| last_failed_at | datetime |  | true |  |  |  | 最終失敗日時 |
| last_error_type | varchar(255) |  | false |  |  |  | 最終エラー種別 |
| lock_token | varchar(255) |  | false |  |  |  | 処理ロックトークン |
| locked_until | datetime |  | true |  |  |  | 処理ロック期限 |
| last_attempted_at | datetime |  | true |  |  |  | 最終試行日時 |
| completed_at | datetime |  | true |  |  |  | 処理完了日時 |
| spotify_snapshot_id | varchar(255) |  | false |  |  |  | SpotifyプレイリストスナップショットID |
| created_at | datetime |  | false |  |  |  | 作成日時 |
| updated_at | datetime |  | false |  |  |  | 更新日時 |
| deleted_at | datetime |  | true |  |  |  | 削除日時 |
| created_user | varchar(255) |  | false |  |  |  | 作成者 |
| updated_user | varchar(255) |  | false |  |  |  | 更新者 |
| deleted_user | varchar(255) |  | false |  |  |  | 削除者 |
| deleted | bigint | 0 | false |  |  |  | 論理削除フラグ(0=有効、1=無効) |
| lock_version | bigint | 0 | false |  |  |  | 楽観ロックバージョン |

## Constraints

| Name | Type | Definition |
| ---- | ---- | ---------- |
| fk_user_new_release_notification_queue_event_id | FOREIGN KEY | FOREIGN KEY (user_new_release_event_id) REFERENCES user_new_release_event (id) |
| fk_user_new_release_notification_queue_playlist_setting_id | FOREIGN KEY | FOREIGN KEY (playlist_setting_id) REFERENCES user_playlist_setting (id) |
| id | UNIQUE | UNIQUE KEY id (id) |
| PRIMARY | PRIMARY KEY | PRIMARY KEY (id) |
| uq_user_new_release_notification_queue_event_type_playlist | UNIQUE | UNIQUE KEY uq_user_new_release_notification_queue_event_type_playlist (user_new_release_event_id, release_notification_type, playlist_setting_id) |

## Indexes

| Name | Definition |
| ---- | ---------- |
| idx_user_new_release_notification_queue_playlist_setting | KEY idx_user_new_release_notification_queue_playlist_setting (playlist_setting_id, deleted, status) USING BTREE |
| idx_user_new_release_notification_queue_target | KEY idx_user_new_release_notification_queue_target (deleted, status, next_attempt_at, locked_until, id) USING BTREE |
| PRIMARY | PRIMARY KEY (id) USING BTREE |
| id | UNIQUE KEY id (id) USING BTREE |
| uq_user_new_release_notification_queue_event_type_playlist | UNIQUE KEY uq_user_new_release_notification_queue_event_type_playlist (user_new_release_event_id, release_notification_type, playlist_setting_id) USING BTREE |

## Relations

```mermaid
erDiagram

"user_new_release_notification_queue" }o--|| "user_new_release_event" : ""
"user_new_release_event" }o--|| "user" : ""
"user_new_release_event" }o--|| "artist_release" : ""
"user_new_release_notification_queue" }o--|| "user_playlist_setting" : ""
"user_playlist_setting" }o--|| "user" : ""

"user_new_release_notification_queue" {
  bigint_unsigned id PK ""
  bigint_unsigned user_new_release_event_id FK "ユーザー別新着リリース履歴ID"
  varchar_255_ release_notification_type "リリース通知種別(PLAYLIST)"
  bigint_unsigned playlist_setting_id FK "ユーザープレイリスト設定ID"
  varchar_255_ status "キュー状態(SCHEDULED, PROCESSING, SUCCEEDED, FAILED, BLOCKED, SKIPPED)"
  datetime next_attempt_at "次回試行日時"
  int attempt_count "試行回数"
  datetime last_failed_at "最終失敗日時"
  varchar_255_ last_error_type "最終エラー種別"
  varchar_255_ lock_token "処理ロックトークン"
  datetime locked_until "処理ロック期限"
  datetime last_attempted_at "最終試行日時"
  datetime completed_at "処理完了日時"
  varchar_255_ spotify_snapshot_id "SpotifyプレイリストスナップショットID"
  datetime created_at "作成日時"
  datetime updated_at "更新日時"
  datetime deleted_at "削除日時"
  varchar_255_ created_user "作成者"
  varchar_255_ updated_user "更新者"
  varchar_255_ deleted_user "削除者"
  bigint deleted "論理削除フラグ(0=有効、1=無効)"
  bigint lock_version "楽観ロックバージョン"
}
"user_new_release_event" {
  bigint_unsigned id PK ""
  bigint_unsigned user_id FK "ユーザーID"
  bigint_unsigned artist_release_id FK "アーティストリリースID"
  varchar_255_ spotify_release_code "SpotifyリリースID"
  varchar_255_ source_spotify_artist_code "取得元SpotifyアーティストID"
  datetime detected_at "新着検出日時"
  varchar_255_ detection_sync_code "検出同期コード"
  datetime created_at "作成日時"
  datetime updated_at "更新日時"
  datetime deleted_at "削除日時"
  varchar_255_ created_user "作成者"
  varchar_255_ updated_user "更新者"
  varchar_255_ deleted_user "削除者"
  bigint deleted "論理削除フラグ(0=有効、1=無効)"
  bigint lock_version "楽観ロックバージョン"
}
"user" {
  bigint_unsigned id PK ""
  varchar_255_ user_name "ユーザー名"
  varchar_255_ display_name "表示名"
  varchar_255_ time_zone "タイムゾーン"
  bigint enabled "有効フラグ(0=無効、1=有効)"
  datetime created_at "作成日時"
  datetime updated_at "更新日時"
  datetime deleted_at "削除日時"
  varchar_255_ created_user "作成者"
  varchar_255_ updated_user "更新者"
  varchar_255_ deleted_user "削除者"
  bigint deleted "論理削除フラグ(0=有効、1=無効)"
  bigint lock_version "楽観ロックバージョン"
}
"artist_release" {
  bigint_unsigned id PK ""
  varchar_255_ spotify_release_code "SpotifyリリースID"
  varchar_255_ source_spotify_artist_code "取得元SpotifyアーティストID"
  varchar_1024_ release_name "リリース名"
  varchar_255_ release_type "アプリ内リリース種別(ALBUM, EP, SINGLE)"
  varchar_255_ album_type "Spotify album_type"
  varchar_255_ album_group "Spotify album_group"
  varchar_255_ spotify_release_uri "SpotifyリリースURI"
  varchar_2048_ spotify_url "Spotify URL"
  varchar_2048_ href "Spotify Web API URL"
  varchar_2048_ primary_image_url "代表画像URL"
  int primary_image_height "代表画像高さ"
  int primary_image_width "代表画像幅"
  json images_json "Spotify画像一覧JSON"
  varchar_255_ release_date_text "リリース日文字列"
  varchar_255_ release_date_precision "リリース日精度"
  datetime release_date_at "リリース日"
  int total_tracks_count "総トラック数"
  varchar_512_ label_name "レーベル名"
  varchar_255_ normalized_label_name "正規化レーベル名"
  json external_ids_json "Spotify外部ID JSON"
  varchar_255_ upc_code "UPCコード"
  varchar_255_ ean_code "EANコード"
  varchar_255_ isrc_code "ISRCコード"
  json copyrights_json "Spotify著作権情報JSON"
  json available_markets_json "Spotify提供国一覧JSON"
  json genres_json "Spotifyジャンル一覧JSON"
  json restrictions_json "Spotify制限情報JSON"
  int popularity "Spotify人気度"
  datetime synced_at "同期日時"
  datetime created_at "作成日時"
  datetime updated_at "更新日時"
  datetime deleted_at "削除日時"
  varchar_255_ created_user "作成者"
  varchar_255_ updated_user "更新者"
  varchar_255_ deleted_user "削除者"
  bigint deleted "論理削除フラグ(0=有効、1=無効)"
  bigint lock_version "楽観ロックバージョン"
}
"user_playlist_setting" {
  bigint_unsigned id PK ""
  bigint_unsigned user_id FK "ユーザーID"
  varchar_255_ playlist_usage_type "プレイリスト用途種別(NEW_RELEASE_NOTIFICATION)"
  varchar_255_ spotify_playlist_code "SpotifyプレイリストID"
  varchar_255_ spotify_playlist_uri "SpotifyプレイリストURI"
  varchar_512_ playlist_name "プレイリスト名"
  bigint enabled "有効フラグ(0=無効、1=有効)"
  datetime created_at "作成日時"
  datetime updated_at "更新日時"
  datetime deleted_at "削除日時"
  varchar_255_ created_user "作成者"
  varchar_255_ updated_user "更新者"
  varchar_255_ deleted_user "削除者"
  bigint deleted "論理削除フラグ(0=有効、1=無効)"
  bigint lock_version "楽観ロックバージョン"
}
```

---

> Generated by [tbls](https://github.com/k1LoW/tbls)
