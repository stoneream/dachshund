CREATE TABLE user_playlist_setting (
  id SERIAL PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL COMMENT 'ユーザーID',
  playlist_usage_type VARCHAR(255) NOT NULL DEFAULT 'NEW_RELEASE_NOTIFICATION' COMMENT 'プレイリスト用途種別(NEW_RELEASE_NOTIFICATION)',
  spotify_playlist_code VARCHAR(255) NOT NULL COMMENT 'SpotifyプレイリストID',
  spotify_playlist_uri VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'SpotifyプレイリストURI',
  playlist_name VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'プレイリスト名',
  enabled BIGINT NOT NULL DEFAULT 1 COMMENT '有効フラグ(0=無効、1=有効)',
  created_at DATETIME NOT NULL COMMENT '作成日時',
  updated_at DATETIME NOT NULL COMMENT '更新日時',
  deleted_at DATETIME NULL COMMENT '削除日時',
  created_user VARCHAR(255) NOT NULL COMMENT '作成者',
  updated_user VARCHAR(255) NOT NULL COMMENT '更新者',
  deleted_user VARCHAR(255) NOT NULL DEFAULT '' COMMENT '削除者',
  deleted BIGINT NOT NULL DEFAULT 0 COMMENT '論理削除フラグ(0=有効、1=無効)',
  lock_version BIGINT NOT NULL DEFAULT 0 COMMENT '楽観ロックバージョン'
) COMMENT='ユーザープレイリスト設定'
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_new_release_notification_queue (
  id SERIAL PRIMARY KEY,
  user_new_release_event_id BIGINT UNSIGNED NOT NULL COMMENT 'ユーザー別新着リリース履歴ID',
  release_notification_type VARCHAR(255) NOT NULL DEFAULT 'PLAYLIST' COMMENT 'リリース通知種別(PLAYLIST)',
  playlist_setting_id BIGINT UNSIGNED NOT NULL COMMENT 'ユーザープレイリスト設定ID',
  status VARCHAR(255) NOT NULL DEFAULT 'SCHEDULED' COMMENT 'キュー状態(SCHEDULED, PROCESSING, SUCCEEDED, FAILED, BLOCKED, SKIPPED)',
  next_attempt_at DATETIME NULL COMMENT '次回試行日時',
  attempt_count INT NOT NULL DEFAULT 0 COMMENT '試行回数',
  last_failed_at DATETIME NULL COMMENT '最終失敗日時',
  last_error_type VARCHAR(255) NOT NULL DEFAULT '' COMMENT '最終エラー種別',
  lock_token VARCHAR(255) NOT NULL DEFAULT '' COMMENT '処理ロックトークン',
  locked_until DATETIME NULL COMMENT '処理ロック期限',
  last_attempted_at DATETIME NULL COMMENT '最終試行日時',
  completed_at DATETIME NULL COMMENT '処理完了日時',
  spotify_snapshot_id VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'SpotifyプレイリストスナップショットID',
  created_at DATETIME NOT NULL COMMENT '作成日時',
  updated_at DATETIME NOT NULL COMMENT '更新日時',
  deleted_at DATETIME NULL COMMENT '削除日時',
  created_user VARCHAR(255) NOT NULL COMMENT '作成者',
  updated_user VARCHAR(255) NOT NULL COMMENT '更新者',
  deleted_user VARCHAR(255) NOT NULL DEFAULT '' COMMENT '削除者',
  deleted BIGINT NOT NULL DEFAULT 0 COMMENT '論理削除フラグ(0=有効、1=無効)',
  lock_version BIGINT NOT NULL DEFAULT 0 COMMENT '楽観ロックバージョン'
) COMMENT='ユーザー別新着リリース通知キュー'
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE user_playlist_setting
  ADD CONSTRAINT fk_user_playlist_setting_user_id
  FOREIGN KEY (user_id) REFERENCES user (id);

ALTER TABLE user_new_release_notification_queue
  ADD CONSTRAINT fk_user_new_release_notification_queue_event_id
  FOREIGN KEY (user_new_release_event_id) REFERENCES user_new_release_event (id);

ALTER TABLE user_new_release_notification_queue
  ADD CONSTRAINT fk_user_new_release_notification_queue_playlist_setting_id
  FOREIGN KEY (playlist_setting_id) REFERENCES user_playlist_setting (id);

CREATE UNIQUE INDEX uq_user_playlist_setting_user_usage
  ON user_playlist_setting (user_id, playlist_usage_type);

CREATE INDEX idx_user_playlist_setting_user_enabled
  ON user_playlist_setting (user_id, deleted, enabled);

CREATE UNIQUE INDEX uq_user_new_release_notification_queue_event_type_playlist
  ON user_new_release_notification_queue (
    user_new_release_event_id,
    release_notification_type,
    playlist_setting_id
  );

CREATE INDEX idx_user_new_release_notification_queue_target
  ON user_new_release_notification_queue (deleted, status, next_attempt_at, locked_until, id);

CREATE INDEX idx_user_new_release_notification_queue_playlist_setting
  ON user_new_release_notification_queue (playlist_setting_id, deleted, status);
