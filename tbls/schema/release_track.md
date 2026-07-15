# release_track

## Description

リリーストラック

<details>
<summary><strong>Table Definition</strong></summary>

```sql
CREATE TABLE `release_track` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `artist_release_id` bigint unsigned NOT NULL COMMENT 'アーティストリリースID',
  `spotify_track_code` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SpotifyトラックID',
  `track_name` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'トラック名',
  `spotify_track_uri` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'SpotifyトラックURI',
  `spotify_url` varchar(2048) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'Spotify URL',
  `href` varchar(2048) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'Spotify Web API URL',
  `disc_number` int NOT NULL DEFAULT '0' COMMENT 'ディスク番号',
  `track_number` int NOT NULL DEFAULT '0' COMMENT 'トラック番号',
  `duration_ms` int DEFAULT NULL COMMENT '再生時間ミリ秒',
  `explicit` bigint DEFAULT NULL COMMENT 'Explicitフラグ(0=無効、1=有効)',
  `is_playable` bigint DEFAULT NULL COMMENT '再生可能フラグ(0=無効、1=有効)',
  `is_local` bigint DEFAULT NULL COMMENT 'ローカルトラックフラグ(0=無効、1=有効)',
  `linked_from_spotify_track_code` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '置換元SpotifyトラックID',
  `linked_from_spotify_track_uri` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '置換元SpotifyトラックURI',
  `preview_url` varchar(2048) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SpotifyプレビューURL',
  `external_ids_json` json DEFAULT NULL COMMENT 'Spotify外部ID JSON',
  `isrc_code` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ISRCコード',
  `ean_code` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'EANコード',
  `upc_code` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'UPCコード',
  `available_markets_json` json DEFAULT NULL COMMENT 'Spotify提供国一覧JSON',
  `restrictions_json` json DEFAULT NULL COMMENT 'Spotify制限情報JSON',
  `popularity` int DEFAULT NULL COMMENT 'Spotify人気度',
  `synced_at` datetime DEFAULT NULL COMMENT '同期日時',
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
  UNIQUE KEY `uq_release_track_release_track` (`artist_release_id`,`spotify_track_code`),
  KEY `idx_release_track_track_code` (`spotify_track_code`),
  KEY `idx_release_track_order` (`artist_release_id`,`disc_number`,`track_number`),
  CONSTRAINT `fk_release_track_artist_release_id` FOREIGN KEY (`artist_release_id`) REFERENCES `artist_release` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='リリーストラック'
```

</details>

## Columns

| Name | Type | Default | Nullable | Extra Definition | Children | Parents | Comment |
| ---- | ---- | ------- | -------- | ---------------- | -------- | ------- | ------- |
| id | bigint unsigned |  | false | auto_increment |  |  |  |
| artist_release_id | bigint unsigned |  | false |  |  | [artist_release](artist_release.md) | アーティストリリースID |
| spotify_track_code | varchar(255) |  | false |  |  |  | SpotifyトラックID |
| track_name | varchar(1024) |  | false |  |  |  | トラック名 |
| spotify_track_uri | varchar(255) |  | false |  |  |  | SpotifyトラックURI |
| spotify_url | varchar(2048) |  | false |  |  |  | Spotify URL |
| href | varchar(2048) |  | false |  |  |  | Spotify Web API URL |
| disc_number | int | 0 | false |  |  |  | ディスク番号 |
| track_number | int | 0 | false |  |  |  | トラック番号 |
| duration_ms | int |  | true |  |  |  | 再生時間ミリ秒 |
| explicit | bigint |  | true |  |  |  | Explicitフラグ(0=無効、1=有効) |
| is_playable | bigint |  | true |  |  |  | 再生可能フラグ(0=無効、1=有効) |
| is_local | bigint |  | true |  |  |  | ローカルトラックフラグ(0=無効、1=有効) |
| linked_from_spotify_track_code | varchar(255) |  | true |  |  |  | 置換元SpotifyトラックID |
| linked_from_spotify_track_uri | varchar(255) |  | true |  |  |  | 置換元SpotifyトラックURI |
| preview_url | varchar(2048) |  | true |  |  |  | SpotifyプレビューURL |
| external_ids_json | json |  | true |  |  |  | Spotify外部ID JSON |
| isrc_code | varchar(255) |  | true |  |  |  | ISRCコード |
| ean_code | varchar(255) |  | true |  |  |  | EANコード |
| upc_code | varchar(255) |  | true |  |  |  | UPCコード |
| available_markets_json | json |  | true |  |  |  | Spotify提供国一覧JSON |
| restrictions_json | json |  | true |  |  |  | Spotify制限情報JSON |
| popularity | int |  | true |  |  |  | Spotify人気度 |
| synced_at | datetime |  | true |  |  |  | 同期日時 |
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
| fk_release_track_artist_release_id | FOREIGN KEY | FOREIGN KEY (artist_release_id) REFERENCES artist_release (id) |
| id | UNIQUE | UNIQUE KEY id (id) |
| PRIMARY | PRIMARY KEY | PRIMARY KEY (id) |
| uq_release_track_release_track | UNIQUE | UNIQUE KEY uq_release_track_release_track (artist_release_id, spotify_track_code) |

## Indexes

| Name | Definition |
| ---- | ---------- |
| idx_release_track_order | KEY idx_release_track_order (artist_release_id, disc_number, track_number) USING BTREE |
| idx_release_track_track_code | KEY idx_release_track_track_code (spotify_track_code) USING BTREE |
| PRIMARY | PRIMARY KEY (id) USING BTREE |
| id | UNIQUE KEY id (id) USING BTREE |
| uq_release_track_release_track | UNIQUE KEY uq_release_track_release_track (artist_release_id, spotify_track_code) USING BTREE |

## Relations

```mermaid
erDiagram

"release_track" }o--|| "artist_release" : ""
"user_new_release_event" }o--|| "artist_release" : ""

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
```

---

> Generated by [tbls](https://github.com/k1LoW/tbls)
