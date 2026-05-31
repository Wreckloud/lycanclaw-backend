-- LycanClaw 本地测试数据库初始化脚本（MySQL 8+）
-- 用途：为后续“索引入库、推荐管理、评论治理、阅读统计”预留表结构。

CREATE DATABASE IF NOT EXISTS `lycanclaw_local_test`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `lycanclaw_local_test`;

-- 文章索引表：后续可替代 posts.json 的主要数据源。
CREATE TABLE IF NOT EXISTS `lc_post_index` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `url` VARCHAR(255) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` VARCHAR(1000) NOT NULL DEFAULT '',
  `published_at` DATETIME NULL,
  `tags_json` JSON NULL,
  `word_count` INT NOT NULL DEFAULT 0,
  `is_published` TINYINT(1) NOT NULL DEFAULT 1,
  `source` VARCHAR(32) NOT NULL DEFAULT 'vitepress',
  `last_synced_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lc_post_index_url` (`url`),
  KEY `idx_lc_post_index_published_at` (`published_at`),
  KEY `idx_lc_post_index_is_published` (`is_published`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 手动推荐表：顺序即展示优先级。
CREATE TABLE IF NOT EXISTS `lc_recommendation_manual` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `post_url` VARCHAR(255) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lc_recommendation_manual_post_url` (`post_url`),
  KEY `idx_lc_recommendation_manual_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 评论快照表：后续从 Waline 拉取并沉淀到本地。
CREATE TABLE IF NOT EXISTS `lc_comment_snapshot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `comment_id` VARCHAR(128) NOT NULL,
  `nick` VARCHAR(128) NOT NULL DEFAULT '',
  `content` TEXT NOT NULL,
  `url` VARCHAR(255) NOT NULL DEFAULT '',
  `path` VARCHAR(255) NOT NULL DEFAULT '',
  `created_at_raw` VARCHAR(64) NOT NULL DEFAULT '',
  `synced_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lc_comment_snapshot_comment_id` (`comment_id`),
  KEY `idx_lc_comment_snapshot_path` (`path`),
  KEY `idx_lc_comment_snapshot_synced_at` (`synced_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 阅读统计日聚合表：用于后续本地统计与推荐加权。
CREATE TABLE IF NOT EXISTS `lc_pageview_daily` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `path` VARCHAR(255) NOT NULL,
  `stats_date` DATE NOT NULL,
  `view_count` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lc_pageview_daily_path_date` (`path`, `stats_date`),
  KEY `idx_lc_pageview_daily_stats_date` (`stats_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 简单运维状态键值：记录最后同步时间、最近任务状态等。
CREATE TABLE IF NOT EXISTS `lc_system_state` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `state_key` VARCHAR(128) NOT NULL,
  `state_value` TEXT NOT NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lc_system_state_key` (`state_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 推荐聚合快照表：定时任务写入，推荐接口直接读取。
CREATE TABLE IF NOT EXISTS `recommendation_metrics` (
  `url` VARCHAR(255) NOT NULL,
  `pageview_count` INT NOT NULL DEFAULT 0,
  `comment_count` INT NOT NULL DEFAULT 0,
  `hot_score` DOUBLE NOT NULL DEFAULT 0,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `source_status` VARCHAR(32) NOT NULL DEFAULT 'ok',
  `last_error` VARCHAR(1000) NULL,
  PRIMARY KEY (`url`),
  KEY `idx_recommendation_metrics_updated_at` (`updated_at`),
  KEY `idx_recommendation_metrics_hot_score` (`hot_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
