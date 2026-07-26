# user_new_release_event

## Description

ユーザー別新着リリース履歴

<details>
<summary><strong>Table Definition</strong></summary>

```sql
CREATE TABLE `user_new_release_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL COMMENT 'ユーザーID',
  `artist_release_id` bigint unsigned NOT NULL COMMENT 'アーティストリリースID',
  `spotify_release_code` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SpotifyリリースID',
  `source_spotify_artist_code` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '取得元SpotifyアーティストID',
  `detected_at` datetime NOT NULL COMMENT '新着検出日時',
  `detection_sync_code` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '検出同期コード',
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
  UNIQUE KEY `uq_user_new_release_event_user_release` (`user_id`,`spotify_release_code`),
  UNIQUE KEY `uq_user_new_release_event_user_artist_release` (`user_id`,`artist_release_id`),
  KEY `idx_user_new_release_event_release` (`artist_release_id`,`deleted`),
  KEY `idx_user_new_release_event_detected` (`user_id`,`deleted`,`detected_at`),
  CONSTRAINT `fk_user_new_release_event_artist_release_id` FOREIGN KEY (`artist_release_id`) REFERENCES `artist_release` (`id`),
  CONSTRAINT `fk_user_new_release_event_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ユーザー別新着リリース履歴'
```

</details>

## Columns

| Name | Type | Default | Nullable | Extra Definition | Children | Parents | Comment |
| ---- | ---- | ------- | -------- | ---------------- | -------- | ------- | ------- |
| id | bigint unsigned |  | false | auto_increment | [user_new_release_notification_queue](user_new_release_notification_queue.md) |  |  |
| user_id | bigint unsigned |  | false |  |  | [user](user.md) | ユーザーID |
| artist_release_id | bigint unsigned |  | false |  |  | [artist_release](artist_release.md) | アーティストリリースID |
| spotify_release_code | varchar(255) |  | false |  |  |  | SpotifyリリースID |
| source_spotify_artist_code | varchar(255) |  | false |  |  |  | 取得元SpotifyアーティストID |
| detected_at | datetime |  | false |  |  |  | 新着検出日時 |
| detection_sync_code | varchar(255) |  | false |  |  |  | 検出同期コード |
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
| fk_user_new_release_event_artist_release_id | FOREIGN KEY | FOREIGN KEY (artist_release_id) REFERENCES artist_release (id) |
| fk_user_new_release_event_user_id | FOREIGN KEY | FOREIGN KEY (user_id) REFERENCES user (id) |
| id | UNIQUE | UNIQUE KEY id (id) |
| PRIMARY | PRIMARY KEY | PRIMARY KEY (id) |
| uq_user_new_release_event_user_artist_release | UNIQUE | UNIQUE KEY uq_user_new_release_event_user_artist_release (user_id, artist_release_id) |
| uq_user_new_release_event_user_release | UNIQUE | UNIQUE KEY uq_user_new_release_event_user_release (user_id, spotify_release_code) |

## Indexes

| Name | Definition |
| ---- | ---------- |
| idx_user_new_release_event_detected | KEY idx_user_new_release_event_detected (user_id, deleted, detected_at) USING BTREE |
| idx_user_new_release_event_release | KEY idx_user_new_release_event_release (artist_release_id, deleted) USING BTREE |
| PRIMARY | PRIMARY KEY (id) USING BTREE |
| id | UNIQUE KEY id (id) USING BTREE |
| uq_user_new_release_event_user_artist_release | UNIQUE KEY uq_user_new_release_event_user_artist_release (user_id, artist_release_id) USING BTREE |
| uq_user_new_release_event_user_release | UNIQUE KEY uq_user_new_release_event_user_release (user_id, spotify_release_code) USING BTREE |

## Relations

```mermaid
erDiagram

"user_new_release_notification_queue" }o--|| "user_new_release_event" : ""
"user_new_release_notification_queue" }o--|| "user_playlist_setting" : ""
"user_new_release_event" }o--|| "user" : ""
"blocked_label" }o--|| "user" : ""
"followed_artist_sync_queue" }o--|| "user" : ""
"user_followed_artist" }o--|| "user" : ""
"user_playlist_setting" }o--|| "user" : ""
"user_session_token" }o--|| "user" : ""
"user_spotify_auth" |o--|| "user" : ""
"user_spotify_authorization" |o--|| "user" : ""
"user_new_release_event" }o--|| "artist_release" : ""
"release_track" }o--|| "artist_release" : ""

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
"blocked_label" {
  bigint_unsigned id PK ""
  bigint_unsigned user_id FK "ユーザーID"
  varchar_512_ label_name "レーベル名"
  varchar_255_ normalized_label_name "正規化レーベル名"
  text reason_text "理由"
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
"followed_artist_sync_queue" {
  bigint_unsigned id PK ""
  bigint_unsigned user_id FK "ユーザーID"
  date sync_date "同期対象日"
  varchar_255_ status "キュー状態(SCHEDULED, PROCESSING, SUCCEEDED, FAILED, BLOCKED, SKIPPED)"
  int requested_limit "Spotify API取得件数"
  varchar_255_ after_cursor "次ページ取得用afterカーソル"
  datetime next_attempt_at "次回試行日時"
  datetime last_attempted_at "最終試行日時"
  datetime completed_at "処理完了日時"
  int attempt_count "試行回数"
  datetime last_failed_at "最終失敗日時"
  varchar_255_ last_error_type "最終エラー種別"
  varchar_255_ lock_token "処理ロックトークン"
  datetime locked_until "処理ロック期限"
  datetime created_at "作成日時"
  datetime updated_at "更新日時"
  datetime deleted_at "削除日時"
  varchar_255_ created_user "作成者"
  varchar_255_ updated_user "更新者"
  varchar_255_ deleted_user "削除者"
  bigint deleted "論理削除フラグ(0=有効、1=無効)"
  bigint lock_version "楽観ロックバージョン"
}
"user_followed_artist" {
  bigint_unsigned id PK ""
  bigint_unsigned user_id FK "ユーザーID"
  varchar_255_ spotify_artist_code "SpotifyアーティストID"
  varchar_512_ artist_name "アーティスト名"
  varchar_255_ spotify_artist_uri "SpotifyアーティストURI"
  varchar_2048_ spotify_url "Spotify URL"
  varchar_2048_ href "Spotify Web API URL"
  varchar_2048_ primary_image_url "代表画像URL"
  int primary_image_height "代表画像高さ"
  int primary_image_width "代表画像幅"
  json images_json "Spotify画像一覧JSON"
  json genres_json "Spotifyジャンル一覧JSON"
  bigint followers_total "Spotifyフォロワー数"
  int popularity "Spotify人気度"
  datetime first_followed_at "初回フォロー検出日時"
  datetime last_seen_at "最終検出日時"
  datetime last_synced_at "最終同期日時"
  datetime created_at "作成日時"
  datetime updated_at "更新日時"
  datetime deleted_at "削除日時"
  varchar_255_ created_user "作成者"
  varchar_255_ updated_user "更新者"
  varchar_255_ deleted_user "削除者"
  bigint deleted "論理削除フラグ(0=有効、1=無効)"
  bigint lock_version "楽観ロックバージョン"
}
"user_session_token" {
  bigint_unsigned id PK ""
  bigint_unsigned user_id FK "ユーザーID"
  varchar_255_ hashed_token "セッショントークンのハッシュ値"
  datetime issued_at "トークン発行日時"
  datetime last_accessed_at "最終アクセス日時"
  datetime idle_expires_at "アイドルタイムアウト日時"
  datetime expires_at "トークン有効期限日時"
  datetime created_at "作成日時"
  datetime updated_at "更新日時"
  datetime deleted_at "削除日時"
  varchar_255_ created_user "作成者"
  varchar_255_ updated_user "更新者"
  varchar_255_ deleted_user "削除者"
  bigint deleted "論理削除フラグ(0=有効、1=無効)"
  bigint lock_version "楽観ロックバージョン"
}
"user_spotify_auth" {
  bigint_unsigned id PK ""
  bigint_unsigned user_id FK "ユーザーID"
  varchar_255_ spotify_user_id "SpotifyユーザーID"
  datetime created_at "作成日時"
  datetime updated_at "更新日時"
  datetime deleted_at "削除日時"
  varchar_255_ created_user "作成者"
  varchar_255_ updated_user "更新者"
  varchar_255_ deleted_user "削除者"
  bigint deleted "論理削除フラグ(0=有効、1=無効)"
  bigint lock_version "楽観ロックバージョン"
}
"user_spotify_authorization" {
  bigint_unsigned id PK ""
  bigint_unsigned user_id FK "ユーザーID"
  text scope_text "認可スコープ"
  varbinary_4096_ access_token_cipher "暗号化アクセストークン"
  varbinary_12_ access_token_nonce "アクセストークンNonce"
  varbinary_16_ access_token_tag "アクセストークン認証タグ"
  varbinary_4096_ refresh_token_cipher "暗号化リフレッシュトークン"
  varbinary_12_ refresh_token_nonce "リフレッシュトークンNonce"
  varbinary_16_ refresh_token_tag "リフレッシュトークン認証タグ"
  varchar_255_ encryption_algorithm "暗号化方式"
  varchar_255_ encryption_key_version "暗号化キーバージョン"
  varchar_255_ token_type "トークン種別"
  datetime access_token_expires_at "アクセストークン失効日時"
  int refresh_margin_seconds "期限前更新猶予秒"
  datetime last_authorized_at "最終認可日時"
  datetime last_refreshed_at "最終更新日時"
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
"release_track" {
  bigint_unsigned id PK ""
  bigint_unsigned artist_release_id FK "アーティストリリースID"
  varchar_255_ spotify_track_code "SpotifyトラックID"
  varchar_1024_ track_name "トラック名"
  varchar_255_ spotify_track_uri "SpotifyトラックURI"
  varchar_2048_ spotify_url "Spotify URL"
  varchar_2048_ href "Spotify Web API URL"
  int disc_number "ディスク番号"
  int track_number "トラック番号"
  int duration_ms "再生時間ミリ秒"
  bigint explicit "Explicitフラグ(0=無効、1=有効)"
  bigint is_playable "再生可能フラグ(0=無効、1=有効)"
  bigint is_local "ローカルトラックフラグ(0=無効、1=有効)"
  varchar_255_ linked_from_spotify_track_code "置換元SpotifyトラックID"
  varchar_255_ linked_from_spotify_track_uri "置換元SpotifyトラックURI"
  varchar_2048_ preview_url "SpotifyプレビューURL"
  json external_ids_json "Spotify外部ID JSON"
  varchar_255_ isrc_code "ISRCコード"
  varchar_255_ ean_code "EANコード"
  varchar_255_ upc_code "UPCコード"
  json available_markets_json "Spotify提供国一覧JSON"
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
```

---

> Generated by [tbls](https://github.com/k1LoW/tbls)
