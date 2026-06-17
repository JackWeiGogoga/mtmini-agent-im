ALTER TABLE `mt_promotion_bid_record`
  ADD COLUMN `record_source` varchar(32) NOT NULL DEFAULT 'AUTO' COMMENT '记录来源 AUTO/MANUAL' AFTER `slot_time`,
  ADD COLUMN `attributed_slot_time` datetime DEFAULT NULL COMMENT '归属标准10分钟槽位' AFTER `record_source`,
  ADD COLUMN `operated_at` datetime DEFAULT NULL COMMENT '实际操作时间' AFTER `attributed_slot_time`,
  ADD KEY `idx_mt_promotion_bid_record_shop_visible_id` (`shop_id`, `log_markdown_status`, `id`);

CREATE TABLE IF NOT EXISTS `mt_promotion_log_read_state` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '店铺ID',
  `last_read_record_id` bigint DEFAULT NULL COMMENT '最近已读推广日志记录ID',
  `last_read_at` datetime DEFAULT NULL COMMENT '最近已读时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_promotion_log_read_state_shop` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店推广日志已读状态表';
