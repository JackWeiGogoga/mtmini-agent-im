CREATE TABLE IF NOT EXISTS `mt_promotion_period_metric_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '店铺ID',
  `biz_date` date NOT NULL COMMENT '业务日期',
  `show_count` int NOT NULL DEFAULT 0 COMMENT '推广期开启区间曝光量',
  `debit_cents` bigint NOT NULL DEFAULT 0 COMMENT '推广期开启区间推广消耗，单位分',
  `income` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '推广期开启区间订单实收',
  `order_count` int NOT NULL DEFAULT 0 COMMENT '推广期开启区间订单量',
  `source_hash` varchar(64) DEFAULT NULL COMMENT '源数据版本摘要',
  `calculated_at` datetime NOT NULL COMMENT '计算时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_promotion_period_metric_shop_date` (`shop_id`, `biz_date`),
  KEY `idx_mt_promotion_period_metric_date_shop` (`biz_date`, `shop_id`),
  KEY `idx_mt_promotion_period_metric_calculated_at` (`calculated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店推广期日统计预聚合表';

ALTER TABLE `mt_promotion_setting_history`
  ADD KEY `idx_mt_promotion_setting_history_shop_time_enabled` (`shop_id`, `create_time`, `promotion_enabled`);

ALTER TABLE `mt_promotion_ad_hourly_detail`
  ADD KEY `idx_mt_promotion_ad_hourly_shop_date_channel` (`shop_id`, `biz_date`, `channel`);
