ALTER TABLE user_new_release_notification_queue
  DROP FOREIGN KEY fk_user_new_release_notification_queue_event_id;

ALTER TABLE user_new_release_notification_queue
  DROP FOREIGN KEY fk_user_new_release_notification_queue_playlist_setting_id;

DROP INDEX uq_user_new_release_notification_queue_event_type_playlist
  ON user_new_release_notification_queue;

DROP INDEX idx_user_new_release_notification_queue_target
  ON user_new_release_notification_queue;

DROP INDEX idx_user_new_release_notification_queue_playlist_setting
  ON user_new_release_notification_queue;

RENAME TABLE user_new_release_notification_queue
  TO user_new_release_notification_delivery_queue;

ALTER TABLE user_new_release_notification_delivery_queue
  ADD CONSTRAINT fk_unr_delivery_queue_event_id
  FOREIGN KEY (user_new_release_event_id) REFERENCES user_new_release_event (id);

ALTER TABLE user_new_release_notification_delivery_queue
  ADD CONSTRAINT fk_unr_delivery_queue_playlist_setting_id
  FOREIGN KEY (playlist_setting_id) REFERENCES user_playlist_setting (id);

CREATE UNIQUE INDEX uq_unr_delivery_queue_event_type_playlist
  ON user_new_release_notification_delivery_queue (
    user_new_release_event_id,
    release_notification_type,
    playlist_setting_id
  );

CREATE INDEX idx_unr_delivery_queue_target
  ON user_new_release_notification_delivery_queue (deleted, status, next_attempt_at, locked_until, id);

CREATE INDEX idx_unr_delivery_queue_playlist_setting
  ON user_new_release_notification_delivery_queue (playlist_setting_id, deleted, status);
