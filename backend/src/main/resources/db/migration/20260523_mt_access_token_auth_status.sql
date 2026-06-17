ALTER TABLE `mt_access_token`
  ADD COLUMN `auth_status` tinyint NOT NULL DEFAULT 1 COMMENT '授权状态 1有效 2需重新授权' AFTER `expires_at`,
  ADD COLUMN `auth_status_changed_at` datetime DEFAULT NULL COMMENT '授权状态最近变更时间' AFTER `auth_status`,
  ADD COLUMN `last_auth_failure_at` datetime DEFAULT NULL COMMENT '最近一次鉴权失败时间' AFTER `auth_status_changed_at`,
  ADD KEY `idx_mt_access_token_auth_status` (`auth_status`, `shop_id`, `expires_at`);
