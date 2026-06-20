CREATE TABLE `recommendation_metrics` (
  `url` VARCHAR(255) NOT NULL,
  `pageview_count` INT NOT NULL DEFAULT 0,
  `comment_count` INT NOT NULL DEFAULT 0,
  `hot_score` DOUBLE NOT NULL DEFAULT 0,
  `updated_at` DATETIME(6) NOT NULL,
  `source_status` VARCHAR(32) NOT NULL DEFAULT 'ok',
  `last_error` VARCHAR(1000) NULL,
  PRIMARY KEY (`url`),
  KEY `idx_recommendation_metrics_updated_at` (`updated_at`),
  KEY `idx_recommendation_metrics_hot_score` (`hot_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `analytics_visit` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `visit_id` VARCHAR(64) NOT NULL,
  `path` VARCHAR(512) NOT NULL,
  `title` VARCHAR(255) NOT NULL DEFAULT '',
  `page_type` VARCHAR(32) NOT NULL DEFAULT 'core',
  `visitor_id` VARCHAR(96) NOT NULL DEFAULT 'anonymous',
  `ip` VARCHAR(64) NOT NULL DEFAULT '',
  `user_agent` VARCHAR(1000) NULL,
  `referrer` VARCHAR(1000) NULL,
  `started_at` DATETIME(6) NOT NULL,
  `ended_at` DATETIME(6) NULL,
  `duration_ms` BIGINT NOT NULL DEFAULT 0,
  `max_scroll_percent` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_analytics_visit_visit_id` (`visit_id`),
  KEY `idx_analytics_visit_started_at` (`started_at`),
  KEY `idx_analytics_visit_path` (`path`),
  KEY `idx_analytics_visit_visitor` (`visitor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `analytics_visitor_identity` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `visitor_id` VARCHAR(96) NOT NULL,
  `waline_user_id` VARCHAR(128) NULL,
  `nickname` VARCHAR(128) NOT NULL DEFAULT '',
  `anonymous_label` VARCHAR(32) NULL,
  `avatar` VARCHAR(1000) NULL,
  `provider` VARCHAR(32) NULL,
  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_visitor_identity_visitor` (`visitor_id`),
  UNIQUE KEY `idx_visitor_identity_label` (`anonymous_label`),
  KEY `idx_visitor_identity_waline_user` (`waline_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `music_listen_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `listen_session_id` VARCHAR(96) NOT NULL,
  `visitor_id` VARCHAR(96) NOT NULL DEFAULT 'anonymous',
  `ip` VARCHAR(64) NOT NULL DEFAULT '',
  `user_agent` VARCHAR(1000) NULL,
  `song_id` VARCHAR(64) NOT NULL,
  `song_name` VARCHAR(255) NOT NULL DEFAULT '',
  `artist` VARCHAR(255) NOT NULL DEFAULT '',
  `playback_source` VARCHAR(64) NOT NULL DEFAULT 'unknown',
  `url_source` VARCHAR(32) NOT NULL DEFAULT 'unknown',
  `page_path` VARCHAR(512) NOT NULL DEFAULT '/',
  `started_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `listened_ms` BIGINT NOT NULL DEFAULT 0,
  `duration_ms` BIGINT NOT NULL DEFAULT 0,
  `completed` BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_music_listen_session_key` (`listen_session_id`),
  KEY `idx_music_listen_started_at` (`started_at`),
  KEY `idx_music_listen_visitor` (`visitor_id`),
  KEY `idx_music_listen_song` (`song_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `encouragement_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `path` VARCHAR(512) NOT NULL DEFAULT '/',
  `title` VARCHAR(255) NOT NULL DEFAULT '首页催更',
  `visitor_id` VARCHAR(96) NOT NULL DEFAULT 'anonymous',
  `ip` VARCHAR(64) NOT NULL DEFAULT '',
  `user_agent` VARCHAR(1000) NULL,
  `delta` INT NOT NULL DEFAULT 1,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_encouragement_created_at` (`created_at`),
  KEY `idx_encouragement_visitor` (`visitor_id`),
  KEY `idx_encouragement_path` (`path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
