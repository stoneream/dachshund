# user_spotify_authorization_refresh_queue

## Description

ユーザーSpotify認可更新キュー

<details>
<summary><strong>Table Definition</strong></summary>

```sql
CREATE TABLE `user_spotify_authorization_refresh_queue` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `authorization_id` bigint unsigned NOT NULL COMMENT 'Spotify認可情報ID',
  `status` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SCHEDULED' COMMENT 'キュー状態(SCHEDULED, PROCESSING, SUCCEEDED, FAILED, BLOCKED, SKIPPED)',
  `next_attempt_at` datetime DEFAULT NULL COMMENT '次回試行日時',
  `attempt_count` int NOT NULL DEFAULT '0' COMMENT '試行回数',
  `last_failed_at` datetime DEFAULT NULL COMMENT '最終失敗日時',
  `last_error_type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '最終エラー種別',
  `lock_token` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '処理ロックトークン',
  `locked_until` datetime DEFAULT NULL COMMENT '処理ロック期限',
  `last_attempted_at` datetime DEFAULT NULL COMMENT '最終試行日時',
  `completed_at` datetime DEFAULT NULL COMMENT '処理完了日時',
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
  UNIQUE KEY `uq_user_spotify_authorization_refresh_queue_authorization_id` (`authorization_id`),
  KEY `idx_user_spotify_authorization_refresh_queue_target` (`deleted`,`status`,`next_attempt_at`,`locked_until`,`id`),
  CONSTRAINT `fk_user_spotify_authorization_refresh_queue_authorization_id` FOREIGN KEY (`authorization_id`) REFERENCES `user_spotify_authorization` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=[Redacted by tbls] DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ユーザーSpotify認可更新キュー'
```

</details>

## Columns

| Name | Type | Default | Nullable | Extra Definition | Children | Parents | Comment |
| ---- | ---- | ------- | -------- | ---------------- | -------- | ------- | ------- |
| id | bigint unsigned |  | false | auto_increment |  |  |  |
| authorization_id | bigint unsigned |  | false |  |  | [user_spotify_authorization](user_spotify_authorization.md) | Spotify認可情報ID |
| status | varchar(255) | SCHEDULED | false |  |  |  | キュー状態(SCHEDULED, PROCESSING, SUCCEEDED, FAILED, BLOCKED, SKIPPED) |
| next_attempt_at | datetime |  | true |  |  |  | 次回試行日時 |
| attempt_count | int | 0 | false |  |  |  | 試行回数 |
| last_failed_at | datetime |  | true |  |  |  | 最終失敗日時 |
| last_error_type | varchar(255) |  | false |  |  |  | 最終エラー種別 |
| lock_token | varchar(255) |  | false |  |  |  | 処理ロックトークン |
| locked_until | datetime |  | true |  |  |  | 処理ロック期限 |
| last_attempted_at | datetime |  | true |  |  |  | 最終試行日時 |
| completed_at | datetime |  | true |  |  |  | 処理完了日時 |
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
| fk_user_spotify_authorization_refresh_queue_authorization_id | FOREIGN KEY | FOREIGN KEY (authorization_id) REFERENCES user_spotify_authorization (id) |
| id | UNIQUE | UNIQUE KEY id (id) |
| PRIMARY | PRIMARY KEY | PRIMARY KEY (id) |
| uq_user_spotify_authorization_refresh_queue_authorization_id | UNIQUE | UNIQUE KEY uq_user_spotify_authorization_refresh_queue_authorization_id (authorization_id) |

## Indexes

| Name | Definition |
| ---- | ---------- |
| idx_user_spotify_authorization_refresh_queue_target | KEY idx_user_spotify_authorization_refresh_queue_target (deleted, status, next_attempt_at, locked_until, id) USING BTREE |
| PRIMARY | PRIMARY KEY (id) USING BTREE |
| id | UNIQUE KEY id (id) USING BTREE |
| uq_user_spotify_authorization_refresh_queue_authorization_id | UNIQUE KEY uq_user_spotify_authorization_refresh_queue_authorization_id (authorization_id) USING BTREE |

## Relations

```mermaid
erDiagram

"user_spotify_authorization_refresh_queue" |o--|| "user_spotify_authorization" : ""
"user_spotify_authorization" |o--|| "user" : ""

"user_spotify_authorization_refresh_queue" {
  bigint_unsigned id PK ""
  bigint_unsigned authorization_id FK "Spotify認可情報ID"
  varchar_255_ status "キュー状態(SCHEDULED, PROCESSING, SUCCEEDED, FAILED, BLOCKED, SKIPPED)"
  datetime next_attempt_at "次回試行日時"
  int attempt_count "試行回数"
  datetime last_failed_at "最終失敗日時"
  varchar_255_ last_error_type "最終エラー種別"
  varchar_255_ lock_token "処理ロックトークン"
  datetime locked_until "処理ロック期限"
  datetime last_attempted_at "最終試行日時"
  datetime completed_at "処理完了日時"
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
```

---

> Generated by [tbls](https://github.com/k1LoW/tbls)
