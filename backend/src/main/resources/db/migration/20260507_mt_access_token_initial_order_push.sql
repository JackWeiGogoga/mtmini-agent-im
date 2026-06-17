ALTER TABLE `mt_access_token`
  ADD COLUMN `order_push_blocked` tinyint(1) DEFAULT NULL COMMENT '订单推送是否已停止 1已停止 0未停止 NULL待同步' AFTER `expires_at`,
  ADD COLUMN `order_push_last_sync_at` datetime DEFAULT NULL COMMENT '订单推送最近一次同步时间' AFTER `order_push_blocked`,
  ADD COLUMN `order_push_last_sync_error` varchar(1000) DEFAULT NULL COMMENT '订单推送最近一次同步错误' AFTER `order_push_last_sync_at`;

UPDATE `mt_access_token`
SET `order_push_blocked` = 1
WHERE `order_push_blocked` IS NULL;

ALTER TABLE `mt_access_token`
  ADD KEY `idx_mt_access_token_order_push` (`order_push_blocked`, `shop_id`, `expires_at`);
