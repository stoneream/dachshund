SET @table_schema = DATABASE();

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE user_new_release_notification_queue ADD COLUMN next_attempt_at DATETIME NULL COMMENT ''次回試行日時'' AFTER status',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'user_new_release_notification_queue'
    AND column_name = 'next_attempt_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE user_new_release_notification_queue ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 COMMENT ''試行回数'' AFTER next_attempt_at',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'user_new_release_notification_queue'
    AND column_name = 'attempt_count'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE user_new_release_notification_queue ADD COLUMN last_failed_at DATETIME NULL COMMENT ''最終失敗日時'' AFTER attempt_count',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'user_new_release_notification_queue'
    AND column_name = 'last_failed_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE user_new_release_notification_queue ADD COLUMN last_error_type VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''最終エラー種別'' AFTER last_failed_at',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'user_new_release_notification_queue'
    AND column_name = 'last_error_type'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE user_new_release_notification_queue ADD COLUMN lock_token VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''処理ロックトークン'' AFTER last_error_type',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'user_new_release_notification_queue'
    AND column_name = 'lock_token'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE user_new_release_notification_queue ADD COLUMN locked_until DATETIME NULL COMMENT ''処理ロック期限'' AFTER lock_token',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'user_new_release_notification_queue'
    AND column_name = 'locked_until'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE user_new_release_notification_queue ADD COLUMN last_attempted_at DATETIME NULL COMMENT ''最終試行日時'' AFTER locked_until',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'user_new_release_notification_queue'
    AND column_name = 'last_attempted_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE user_new_release_notification_queue ADD COLUMN completed_at DATETIME NULL COMMENT ''処理完了日時'' AFTER last_attempted_at',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @table_schema
    AND table_name = 'user_new_release_notification_queue'
    AND column_name = 'completed_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(DISTINCT index_name) > 0,
    'DROP INDEX idx_user_new_release_notification_queue_target ON user_new_release_notification_queue',
    'SELECT 1'
  )
  FROM information_schema.statistics
  WHERE table_schema = @table_schema
    AND table_name = 'user_new_release_notification_queue'
    AND index_name = 'idx_user_new_release_notification_queue_target'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE INDEX idx_user_new_release_notification_queue_target
  ON user_new_release_notification_queue (deleted, status, next_attempt_at, locked_until, id);
