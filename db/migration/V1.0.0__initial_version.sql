CREATE TABLE user (
  id SERIAL PRIMARY KEY,
  user_name VARCHAR(255) NOT NULL COMMENT 'ユーザー名',
  display_name VARCHAR(255) NOT NULL COMMENT '表示名',
  time_zone VARCHAR(255) NOT NULL DEFAULT 'Asia/Tokyo' COMMENT 'タイムゾーン',
  enabled BIGINT NOT NULL DEFAULT 1 COMMENT '有効フラグ(0=無効、1=有効)',
  created_at DATETIME NOT NULL COMMENT '作成日時',
  updated_at DATETIME NOT NULL COMMENT '更新日時',
  deleted_at DATETIME NULL COMMENT '削除日時',
  created_user VARCHAR(255) NOT NULL COMMENT '作成者',
  updated_user VARCHAR(255) NOT NULL COMMENT '更新者',
  deleted_user VARCHAR(255) NOT NULL DEFAULT '' COMMENT '削除者',
  deleted BIGINT NOT NULL DEFAULT 0 COMMENT '論理削除フラグ(0=有効、1=無効)',
  lock_version BIGINT NOT NULL DEFAULT 0 COMMENT '楽観ロックバージョン'
) COMMENT='アプリケーションユーザー'
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE external_auth_request (
  id SERIAL PRIMARY KEY,
  flow_type VARCHAR(255) NOT NULL COMMENT '認証フロー種別(SIGNUP)',
  provider_type VARCHAR(255) NOT NULL COMMENT '外部認証種別(SPOTIFY)',
  state VARCHAR(255) NOT NULL COMMENT 'OAuth state照合値',
  nonce VARCHAR(255) NOT NULL COMMENT 'OIDC nonce照合値',
  code_verifier VARCHAR(255) NULL COMMENT 'PKCE利用時のcode verifier',
  redirect_uri VARCHAR(2048) NOT NULL COMMENT 'OAuth認可要求時のredirect URI',
  scopes VARCHAR(1024) NOT NULL COMMENT '認可要求scope一覧(スペース区切り)',
  status VARCHAR(255) NOT NULL DEFAULT 'PENDING' COMMENT '外部認証要求状態(PENDING, PROCESSING, SUCCEEDED, FAILED)',
  expires_at DATETIME NOT NULL COMMENT '認可要求有効期限日時',
  completed_at DATETIME NULL COMMENT '認可要求完了日時',
  error_code VARCHAR(255) NULL COMMENT '認可要求エラーコード',
  error_description VARCHAR(1024) NULL COMMENT '認可要求エラー詳細',
  created_at DATETIME NOT NULL COMMENT '作成日時',
  updated_at DATETIME NOT NULL COMMENT '更新日時',
  deleted_at DATETIME NULL COMMENT '削除日時',
  created_user VARCHAR(255) NOT NULL COMMENT '作成者',
  updated_user VARCHAR(255) NOT NULL COMMENT '更新者',
  deleted_user VARCHAR(255) NOT NULL DEFAULT '' COMMENT '削除者',
  deleted BIGINT NOT NULL DEFAULT 0 COMMENT '論理削除フラグ(0=有効、1=無効)',
  lock_version BIGINT NOT NULL DEFAULT 0 COMMENT '楽観ロックバージョン'
) COMMENT='外部認証要求'
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_spotify_auth (
  id SERIAL PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL COMMENT 'ユーザーID',
  spotify_user_id VARCHAR(255) NOT NULL COMMENT 'SpotifyユーザーID',
  created_at DATETIME NOT NULL COMMENT '作成日時',
  updated_at DATETIME NOT NULL COMMENT '更新日時',
  deleted_at DATETIME NULL COMMENT '削除日時',
  created_user VARCHAR(255) NOT NULL COMMENT '作成者',
  updated_user VARCHAR(255) NOT NULL COMMENT '更新者',
  deleted_user VARCHAR(255) NOT NULL DEFAULT '' COMMENT '削除者',
  deleted BIGINT NOT NULL DEFAULT 0 COMMENT '論理削除フラグ(0=有効、1=無効)',
  lock_version BIGINT NOT NULL DEFAULT 0 COMMENT '楽観ロックバージョン'
) COMMENT='Spotify認証識別子'
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_session_token (
  id SERIAL PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL COMMENT 'ユーザーID',
  hashed_token VARCHAR(255) NOT NULL COMMENT 'セッショントークンのハッシュ値',
  issued_at DATETIME NOT NULL COMMENT 'トークン発行日時',
  last_accessed_at DATETIME NOT NULL COMMENT '最終アクセス日時',
  idle_expires_at DATETIME NOT NULL COMMENT 'アイドルタイムアウト日時',
  expires_at DATETIME NOT NULL COMMENT 'トークン有効期限日時',
  created_at DATETIME NOT NULL COMMENT '作成日時',
  updated_at DATETIME NOT NULL COMMENT '更新日時',
  deleted_at DATETIME NULL COMMENT '削除日時',
  created_user VARCHAR(255) NOT NULL COMMENT '作成者',
  updated_user VARCHAR(255) NOT NULL COMMENT '更新者',
  deleted_user VARCHAR(255) NOT NULL DEFAULT '' COMMENT '削除者',
  deleted BIGINT NOT NULL DEFAULT 0 COMMENT '論理削除フラグ(0=有効、1=無効)',
  lock_version BIGINT NOT NULL DEFAULT 0 COMMENT '楽観ロックバージョン'
) COMMENT='ユーザーセッション'
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_spotify_authorization (
  id SERIAL PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL COMMENT 'ユーザーID',
  scope_text TEXT NOT NULL COMMENT '認可スコープ',
  access_token_cipher VARBINARY(4096) NOT NULL COMMENT '暗号化アクセストークン',
  access_token_nonce VARBINARY(12) NOT NULL COMMENT 'アクセストークンNonce',
  access_token_tag VARBINARY(16) NOT NULL COMMENT 'アクセストークン認証タグ',
  refresh_token_cipher VARBINARY(4096) NOT NULL COMMENT '暗号化リフレッシュトークン',
  refresh_token_nonce VARBINARY(12) NOT NULL COMMENT 'リフレッシュトークンNonce',
  refresh_token_tag VARBINARY(16) NOT NULL COMMENT 'リフレッシュトークン認証タグ',
  encryption_algorithm VARCHAR(255) NOT NULL COMMENT '暗号化方式',
  encryption_key_version VARCHAR(255) NOT NULL COMMENT '暗号化キーバージョン',
  token_type VARCHAR(255) NOT NULL COMMENT 'トークン種別',
  access_token_expires_at DATETIME NOT NULL COMMENT 'アクセストークン失効日時',
  refresh_margin_seconds INT NOT NULL DEFAULT 300 COMMENT '期限前更新猶予秒',
  last_authorized_at DATETIME NULL COMMENT '最終認可日時',
  last_refreshed_at DATETIME NULL COMMENT '最終更新日時',
  created_at DATETIME NOT NULL COMMENT '作成日時',
  updated_at DATETIME NOT NULL COMMENT '更新日時',
  deleted_at DATETIME NULL COMMENT '削除日時',
  created_user VARCHAR(255) NOT NULL COMMENT '作成者',
  updated_user VARCHAR(255) NOT NULL COMMENT '更新者',
  deleted_user VARCHAR(255) NOT NULL DEFAULT '' COMMENT '削除者',
  deleted BIGINT NOT NULL DEFAULT 0 COMMENT '論理削除フラグ(0=有効、1=無効)',
  lock_version BIGINT NOT NULL DEFAULT 0 COMMENT '楽観ロックバージョン'
) COMMENT='ユーザーSpotify認可情報'
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_spotify_authorization_refresh_queue (
  id SERIAL PRIMARY KEY,
  authorization_id BIGINT UNSIGNED NOT NULL COMMENT 'Spotify認可情報ID',
  status VARCHAR(255) NOT NULL DEFAULT 'SCHEDULED' COMMENT 'キュー状態(SCHEDULED, PROCESSING, SUCCEEDED, FAILED, BLOCKED, SKIPPED)',
  next_attempt_at DATETIME NULL COMMENT '次回試行日時',
  attempt_count INT NOT NULL DEFAULT 0 COMMENT '試行回数',
  last_failed_at DATETIME NULL COMMENT '最終失敗日時',
  last_error_type VARCHAR(255) NOT NULL DEFAULT '' COMMENT '最終エラー種別',
  lock_token VARCHAR(255) NOT NULL DEFAULT '' COMMENT '処理ロックトークン',
  locked_until DATETIME NULL COMMENT '処理ロック期限',
  last_attempted_at DATETIME NULL COMMENT '最終試行日時',
  completed_at DATETIME NULL COMMENT '処理完了日時',
  created_at DATETIME NOT NULL COMMENT '作成日時',
  updated_at DATETIME NOT NULL COMMENT '更新日時',
  deleted_at DATETIME NULL COMMENT '削除日時',
  created_user VARCHAR(255) NOT NULL COMMENT '作成者',
  updated_user VARCHAR(255) NOT NULL COMMENT '更新者',
  deleted_user VARCHAR(255) NOT NULL DEFAULT '' COMMENT '削除者',
  deleted BIGINT NOT NULL DEFAULT 0 COMMENT '論理削除フラグ(0=有効、1=無効)',
  lock_version BIGINT NOT NULL DEFAULT 0 COMMENT '楽観ロックバージョン'
) COMMENT='ユーザーSpotify認可更新キュー'
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_followed_artist (
  id SERIAL PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL COMMENT 'ユーザーID',
  spotify_artist_code VARCHAR(255) NOT NULL COMMENT 'SpotifyアーティストID',
  artist_name VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'アーティスト名',
  spotify_artist_uri VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'SpotifyアーティストURI',
  spotify_url VARCHAR(2048) NOT NULL DEFAULT '' COMMENT 'Spotify URL',
  href VARCHAR(2048) NOT NULL DEFAULT '' COMMENT 'Spotify Web API URL',
  primary_image_url VARCHAR(2048) NOT NULL DEFAULT '' COMMENT '代表画像URL',
  primary_image_height INT NULL COMMENT '代表画像高さ',
  primary_image_width INT NULL COMMENT '代表画像幅',
  images_json JSON NULL COMMENT 'Spotify画像一覧JSON',
  genres_json JSON NULL COMMENT 'Spotifyジャンル一覧JSON',
  followers_total BIGINT NULL COMMENT 'Spotifyフォロワー数',
  popularity INT NULL COMMENT 'Spotify人気度',
  first_followed_at DATETIME NULL COMMENT '初回フォロー検出日時',
  last_seen_at DATETIME NULL COMMENT '最終検出日時',
  last_synced_at DATETIME NULL COMMENT '最終同期日時',
  created_at DATETIME NOT NULL COMMENT '作成日時',
  updated_at DATETIME NOT NULL COMMENT '更新日時',
  deleted_at DATETIME NULL COMMENT '削除日時',
  created_user VARCHAR(255) NOT NULL COMMENT '作成者',
  updated_user VARCHAR(255) NOT NULL COMMENT '更新者',
  deleted_user VARCHAR(255) NOT NULL DEFAULT '' COMMENT '削除者',
  deleted BIGINT NOT NULL DEFAULT 0 COMMENT '論理削除フラグ(0=有効、1=無効)',
  lock_version BIGINT NOT NULL DEFAULT 0 COMMENT '楽観ロックバージョン'
) COMMENT='ユーザー別フォロー中アーティスト'
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE followed_artist_sync_queue (
  id SERIAL PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL COMMENT 'ユーザーID',
  sync_date DATE NOT NULL COMMENT '同期対象日',
  status VARCHAR(255) NOT NULL DEFAULT 'SCHEDULED' COMMENT 'キュー状態(SCHEDULED, PROCESSING, SUCCEEDED, FAILED, BLOCKED, SKIPPED)',
  requested_limit INT NOT NULL COMMENT 'Spotify API取得件数',
  after_cursor VARCHAR(255) NULL COMMENT '次ページ取得用afterカーソル',
  next_attempt_at DATETIME NULL COMMENT '次回試行日時',
  last_attempted_at DATETIME NULL COMMENT '最終試行日時',
  completed_at DATETIME NULL COMMENT '処理完了日時',
  attempt_count INT NOT NULL DEFAULT 0 COMMENT '試行回数',
  last_failed_at DATETIME NULL COMMENT '最終失敗日時',
  last_error_type VARCHAR(255) NOT NULL DEFAULT '' COMMENT '最終エラー種別',
  lock_token VARCHAR(255) NOT NULL DEFAULT '' COMMENT '処理ロックトークン',
  locked_until DATETIME NULL COMMENT '処理ロック期限',
  created_at DATETIME NOT NULL COMMENT '作成日時',
  updated_at DATETIME NOT NULL COMMENT '更新日時',
  deleted_at DATETIME NULL COMMENT '削除日時',
  created_user VARCHAR(255) NOT NULL COMMENT '作成者',
  updated_user VARCHAR(255) NOT NULL COMMENT '更新者',
  deleted_user VARCHAR(255) NOT NULL DEFAULT '' COMMENT '削除者',
  deleted BIGINT NOT NULL DEFAULT 0 COMMENT '論理削除フラグ(0=有効、1=無効)',
  lock_version BIGINT NOT NULL DEFAULT 0 COMMENT '楽観ロックバージョン'
) COMMENT='フォロー中アーティスト同期キュー'
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE artist_release_sync_queue (
  id SERIAL PRIMARY KEY,
  spotify_artist_code VARCHAR(255) NOT NULL COMMENT 'SpotifyアーティストID',
  sync_scope VARCHAR(255) NOT NULL DEFAULT 'INCREMENTAL' COMMENT '同期範囲(INITIAL_IMPORT, INCREMENTAL)',
  status VARCHAR(255) NOT NULL DEFAULT 'SCHEDULED' COMMENT 'キュー状態(SCHEDULED, PROCESSING, SUCCEEDED, FAILED, BLOCKED, SKIPPED)',
  include_groups VARCHAR(255) NOT NULL DEFAULT 'album,single' COMMENT 'Spotify include_groups',
  market VARCHAR(2) NULL COMMENT 'Spotify market',
  requested_limit INT NOT NULL DEFAULT 10 COMMENT 'Spotify API取得件数',
  next_offset INT NOT NULL DEFAULT 0 COMMENT '次ページ取得用offset',
  next_attempt_at DATETIME NULL COMMENT '次回試行日時',
  last_attempted_at DATETIME NULL COMMENT '最終試行日時',
  completed_at DATETIME NULL COMMENT '処理完了日時',
  attempt_count INT NOT NULL DEFAULT 0 COMMENT '試行回数',
  last_failed_at DATETIME NULL COMMENT '最終失敗日時',
  last_error_type VARCHAR(255) NOT NULL DEFAULT '' COMMENT '最終エラー種別',
  lock_token VARCHAR(255) NOT NULL DEFAULT '' COMMENT '処理ロックトークン',
  locked_until DATETIME NULL COMMENT '処理ロック期限',
  created_at DATETIME NOT NULL COMMENT '作成日時',
  updated_at DATETIME NOT NULL COMMENT '更新日時',
  deleted_at DATETIME NULL COMMENT '削除日時',
  created_user VARCHAR(255) NOT NULL COMMENT '作成者',
  updated_user VARCHAR(255) NOT NULL COMMENT '更新者',
  deleted_user VARCHAR(255) NOT NULL DEFAULT '' COMMENT '削除者',
  deleted BIGINT NOT NULL DEFAULT 0 COMMENT '論理削除フラグ(0=有効、1=無効)',
  lock_version BIGINT NOT NULL DEFAULT 0 COMMENT '楽観ロックバージョン'
) COMMENT='アーティストリリース同期キュー'
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE blocked_label (
  id SERIAL PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL COMMENT 'ユーザーID',
  label_name VARCHAR(512) NOT NULL COMMENT 'レーベル名',
  normalized_label_name VARCHAR(255) NOT NULL COMMENT '正規化レーベル名',
  reason_text TEXT NOT NULL COMMENT '理由',
  enabled BIGINT NOT NULL DEFAULT 1 COMMENT '有効フラグ(0=無効、1=有効)',
  created_at DATETIME NOT NULL COMMENT '作成日時',
  updated_at DATETIME NOT NULL COMMENT '更新日時',
  deleted_at DATETIME NULL COMMENT '削除日時',
  created_user VARCHAR(255) NOT NULL COMMENT '作成者',
  updated_user VARCHAR(255) NOT NULL COMMENT '更新者',
  deleted_user VARCHAR(255) NOT NULL DEFAULT '' COMMENT '削除者',
  deleted BIGINT NOT NULL DEFAULT 0 COMMENT '論理削除フラグ(0=有効、1=無効)',
  lock_version BIGINT NOT NULL DEFAULT 0 COMMENT '楽観ロックバージョン'
) COMMENT='新着リリース除外レーベル'
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE artist_release (
  id SERIAL PRIMARY KEY,
  spotify_release_code VARCHAR(255) NOT NULL COMMENT 'SpotifyリリースID',
  source_spotify_artist_code VARCHAR(255) NOT NULL COMMENT '取得元SpotifyアーティストID',
  release_name VARCHAR(1024) NOT NULL DEFAULT '' COMMENT 'リリース名',
  release_type VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'アプリ内リリース種別(ALBUM, EP, SINGLE)',
  album_type VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'Spotify album_type',
  album_group VARCHAR(255) NULL COMMENT 'Spotify album_group',
  spotify_release_uri VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'SpotifyリリースURI',
  spotify_url VARCHAR(2048) NOT NULL DEFAULT '' COMMENT 'Spotify URL',
  href VARCHAR(2048) NOT NULL DEFAULT '' COMMENT 'Spotify Web API URL',
  primary_image_url VARCHAR(2048) NOT NULL DEFAULT '' COMMENT '代表画像URL',
  primary_image_height INT NULL COMMENT '代表画像高さ',
  primary_image_width INT NULL COMMENT '代表画像幅',
  images_json JSON NULL COMMENT 'Spotify画像一覧JSON',
  release_date_text VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'リリース日文字列',
  release_date_precision VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'リリース日精度',
  release_date_at DATETIME NULL COMMENT 'リリース日',
  total_tracks_count INT NULL COMMENT '総トラック数',
  label_name VARCHAR(512) NULL COMMENT 'レーベル名',
  normalized_label_name VARCHAR(255) NULL COMMENT '正規化レーベル名',
  external_ids_json JSON NULL COMMENT 'Spotify外部ID JSON',
  upc_code VARCHAR(255) NULL COMMENT 'UPCコード',
  ean_code VARCHAR(255) NULL COMMENT 'EANコード',
  isrc_code VARCHAR(255) NULL COMMENT 'ISRCコード',
  copyrights_json JSON NULL COMMENT 'Spotify著作権情報JSON',
  available_markets_json JSON NULL COMMENT 'Spotify提供国一覧JSON',
  genres_json JSON NULL COMMENT 'Spotifyジャンル一覧JSON',
  restrictions_json JSON NULL COMMENT 'Spotify制限情報JSON',
  popularity INT NULL COMMENT 'Spotify人気度',
  synced_at DATETIME NULL COMMENT '同期日時',
  created_at DATETIME NOT NULL COMMENT '作成日時',
  updated_at DATETIME NOT NULL COMMENT '更新日時',
  deleted_at DATETIME NULL COMMENT '削除日時',
  created_user VARCHAR(255) NOT NULL COMMENT '作成者',
  updated_user VARCHAR(255) NOT NULL COMMENT '更新者',
  deleted_user VARCHAR(255) NOT NULL DEFAULT '' COMMENT '削除者',
  deleted BIGINT NOT NULL DEFAULT 0 COMMENT '論理削除フラグ(0=有効、1=無効)',
  lock_version BIGINT NOT NULL DEFAULT 0 COMMENT '楽観ロックバージョン'
) COMMENT='アーティストリリース'
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE release_track (
  id SERIAL PRIMARY KEY,
  artist_release_id BIGINT UNSIGNED NOT NULL COMMENT 'アーティストリリースID',
  spotify_track_code VARCHAR(255) NOT NULL COMMENT 'SpotifyトラックID',
  track_name VARCHAR(1024) NOT NULL DEFAULT '' COMMENT 'トラック名',
  spotify_track_uri VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'SpotifyトラックURI',
  spotify_url VARCHAR(2048) NOT NULL DEFAULT '' COMMENT 'Spotify URL',
  href VARCHAR(2048) NOT NULL DEFAULT '' COMMENT 'Spotify Web API URL',
  disc_number INT NOT NULL DEFAULT 0 COMMENT 'ディスク番号',
  track_number INT NOT NULL DEFAULT 0 COMMENT 'トラック番号',
  duration_ms INT NULL COMMENT '再生時間ミリ秒',
  explicit BIGINT NULL COMMENT 'Explicitフラグ(0=無効、1=有効)',
  is_playable BIGINT NULL COMMENT '再生可能フラグ(0=無効、1=有効)',
  is_local BIGINT NULL COMMENT 'ローカルトラックフラグ(0=無効、1=有効)',
  linked_from_spotify_track_code VARCHAR(255) NULL COMMENT '置換元SpotifyトラックID',
  linked_from_spotify_track_uri VARCHAR(255) NULL COMMENT '置換元SpotifyトラックURI',
  preview_url VARCHAR(2048) NULL COMMENT 'SpotifyプレビューURL',
  external_ids_json JSON NULL COMMENT 'Spotify外部ID JSON',
  isrc_code VARCHAR(255) NULL COMMENT 'ISRCコード',
  ean_code VARCHAR(255) NULL COMMENT 'EANコード',
  upc_code VARCHAR(255) NULL COMMENT 'UPCコード',
  available_markets_json JSON NULL COMMENT 'Spotify提供国一覧JSON',
  restrictions_json JSON NULL COMMENT 'Spotify制限情報JSON',
  popularity INT NULL COMMENT 'Spotify人気度',
  synced_at DATETIME NULL COMMENT '同期日時',
  created_at DATETIME NOT NULL COMMENT '作成日時',
  updated_at DATETIME NOT NULL COMMENT '更新日時',
  deleted_at DATETIME NULL COMMENT '削除日時',
  created_user VARCHAR(255) NOT NULL COMMENT '作成者',
  updated_user VARCHAR(255) NOT NULL COMMENT '更新者',
  deleted_user VARCHAR(255) NOT NULL DEFAULT '' COMMENT '削除者',
  deleted BIGINT NOT NULL DEFAULT 0 COMMENT '論理削除フラグ(0=有効、1=無効)',
  lock_version BIGINT NOT NULL DEFAULT 0 COMMENT '楽観ロックバージョン'
) COMMENT='リリーストラック'
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_new_release_event (
  id SERIAL PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL COMMENT 'ユーザーID',
  artist_release_id BIGINT UNSIGNED NOT NULL COMMENT 'アーティストリリースID',
  spotify_release_code VARCHAR(255) NOT NULL COMMENT 'SpotifyリリースID',
  source_spotify_artist_code VARCHAR(255) NOT NULL COMMENT '取得元SpotifyアーティストID',
  detected_at DATETIME NOT NULL COMMENT '新着検出日時',
  detection_sync_code VARCHAR(255) NOT NULL DEFAULT '' COMMENT '検出同期コード',
  created_at DATETIME NOT NULL COMMENT '作成日時',
  updated_at DATETIME NOT NULL COMMENT '更新日時',
  deleted_at DATETIME NULL COMMENT '削除日時',
  created_user VARCHAR(255) NOT NULL COMMENT '作成者',
  updated_user VARCHAR(255) NOT NULL COMMENT '更新者',
  deleted_user VARCHAR(255) NOT NULL DEFAULT '' COMMENT '削除者',
  deleted BIGINT NOT NULL DEFAULT 0 COMMENT '論理削除フラグ(0=有効、1=無効)',
  lock_version BIGINT NOT NULL DEFAULT 0 COMMENT '楽観ロックバージョン'
) COMMENT='ユーザー別新着リリース履歴'
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE user_spotify_auth
  ADD CONSTRAINT fk_user_spotify_auth_user_id
  FOREIGN KEY (user_id) REFERENCES user (id);

ALTER TABLE user_session_token
  ADD CONSTRAINT fk_user_session_token_user_id
  FOREIGN KEY (user_id) REFERENCES user (id);

ALTER TABLE user_spotify_authorization
  ADD CONSTRAINT fk_user_spotify_authorization_user_id
  FOREIGN KEY (user_id) REFERENCES user (id);

ALTER TABLE user_spotify_authorization_refresh_queue
  ADD CONSTRAINT fk_user_spotify_authorization_refresh_queue_authorization_id
  FOREIGN KEY (authorization_id) REFERENCES user_spotify_authorization (id);

ALTER TABLE user_followed_artist
  ADD CONSTRAINT fk_user_followed_artist_user_id
  FOREIGN KEY (user_id) REFERENCES user (id);

ALTER TABLE followed_artist_sync_queue
  ADD CONSTRAINT fk_followed_artist_sync_queue_user_id
  FOREIGN KEY (user_id) REFERENCES user (id);

ALTER TABLE blocked_label
  ADD CONSTRAINT fk_blocked_label_user_id
  FOREIGN KEY (user_id) REFERENCES user (id);

ALTER TABLE release_track
  ADD CONSTRAINT fk_release_track_artist_release_id
  FOREIGN KEY (artist_release_id) REFERENCES artist_release (id);

ALTER TABLE user_new_release_event
  ADD CONSTRAINT fk_user_new_release_event_user_id
  FOREIGN KEY (user_id) REFERENCES user (id);

ALTER TABLE user_new_release_event
  ADD CONSTRAINT fk_user_new_release_event_artist_release_id
  FOREIGN KEY (artist_release_id) REFERENCES artist_release (id);

CREATE UNIQUE INDEX uq_user_user_name
  ON user (user_name);

CREATE INDEX idx_user_enabled
  ON user (deleted, enabled);

CREATE UNIQUE INDEX uq_external_auth_request_state
  ON external_auth_request (state);

CREATE INDEX idx_external_auth_request_flow_provider
  ON external_auth_request (flow_type, provider_type);

CREATE INDEX idx_external_auth_request_status
  ON external_auth_request (status);

CREATE INDEX idx_external_auth_request_expires_at
  ON external_auth_request (expires_at);

CREATE INDEX idx_external_auth_request_completed_at
  ON external_auth_request (completed_at);

CREATE UNIQUE INDEX uq_user_spotify_auth_user_id
  ON user_spotify_auth (user_id);

CREATE UNIQUE INDEX uq_user_spotify_auth_spotify_user_id
  ON user_spotify_auth (spotify_user_id);

CREATE UNIQUE INDEX uq_user_session_token_hashed_token
  ON user_session_token (hashed_token);

CREATE INDEX idx_user_session_token_user_id
  ON user_session_token (user_id);

CREATE INDEX idx_user_session_token_idle_expires_at
  ON user_session_token (idle_expires_at);

CREATE INDEX idx_user_session_token_expires_at
  ON user_session_token (expires_at);

CREATE UNIQUE INDEX uq_user_spotify_authorization_user_id
  ON user_spotify_authorization (user_id);

CREATE UNIQUE INDEX uq_user_spotify_authorization_refresh_queue_authorization_id
  ON user_spotify_authorization_refresh_queue (authorization_id);

CREATE INDEX idx_user_spotify_authorization_refresh_queue_target
  ON user_spotify_authorization_refresh_queue (
    deleted,
    status,
    next_attempt_at,
    locked_until,
    id
  );

CREATE UNIQUE INDEX uq_user_followed_artist_user_artist
  ON user_followed_artist (user_id, spotify_artist_code);

CREATE INDEX idx_user_followed_artist_sync_target
  ON user_followed_artist (deleted, user_id, last_synced_at);

CREATE INDEX idx_user_followed_artist_artist_code
  ON user_followed_artist (spotify_artist_code);

CREATE UNIQUE INDEX uq_followed_artist_sync_queue_user_date
  ON followed_artist_sync_queue (user_id, sync_date);

CREATE INDEX idx_followed_artist_sync_queue_target
  ON followed_artist_sync_queue (deleted, status, next_attempt_at, locked_until, id);

CREATE INDEX idx_followed_artist_sync_queue_user_status
  ON followed_artist_sync_queue (user_id, status, created_at);

CREATE UNIQUE INDEX uq_artist_release_sync_queue_artist_scope
  ON artist_release_sync_queue (spotify_artist_code, sync_scope);

CREATE INDEX idx_artist_release_sync_queue_target
  ON artist_release_sync_queue (deleted, status, next_attempt_at, locked_until, id);

CREATE UNIQUE INDEX uq_blocked_label_user_label
  ON blocked_label (user_id, normalized_label_name);

CREATE INDEX idx_blocked_label_match
  ON blocked_label (user_id, deleted, enabled, normalized_label_name);

CREATE UNIQUE INDEX uq_artist_release_release
  ON artist_release (spotify_release_code);

CREATE INDEX idx_artist_release_source_artist
  ON artist_release (source_spotify_artist_code, deleted);

CREATE INDEX idx_artist_release_release_date
  ON artist_release (deleted, release_date_at);

CREATE INDEX idx_artist_release_label
  ON artist_release (normalized_label_name, deleted);

CREATE UNIQUE INDEX uq_release_track_release_track
  ON release_track (artist_release_id, spotify_track_code);

CREATE INDEX idx_release_track_track_code
  ON release_track (spotify_track_code);

CREATE INDEX idx_release_track_order
  ON release_track (artist_release_id, disc_number, track_number);

CREATE UNIQUE INDEX uq_user_new_release_event_user_release
  ON user_new_release_event (user_id, spotify_release_code);

CREATE UNIQUE INDEX uq_user_new_release_event_user_artist_release
  ON user_new_release_event (user_id, artist_release_id);

CREATE INDEX idx_user_new_release_event_release
  ON user_new_release_event (artist_release_id, deleted);

CREATE INDEX idx_user_new_release_event_detected
  ON user_new_release_event (user_id, deleted, detected_at);
