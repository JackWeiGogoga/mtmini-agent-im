CREATE TABLE IF NOT EXISTS `mt_promotion_ad_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '店铺ID',
  `biz_date` date NOT NULL COMMENT '业务日期',
  `channel` int NOT NULL COMMENT '推广渠道，1=点金推广',
  `show_count` int NOT NULL DEFAULT 0 COMMENT '曝光量',
  `click_count` int NOT NULL DEFAULT 0 COMMENT '点击量',
  `debit_cents` bigint NOT NULL DEFAULT 0 COMMENT '推广消耗，单位分',
  `payload_json` longtext COMMENT '原始响应及解密结果',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_promotion_ad_daily_shop_date_channel` (`shop_id`, `biz_date`, `channel`),
  KEY `idx_mt_promotion_ad_daily_shop_date` (`shop_id`, `biz_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店推广广告日汇总数据';

CREATE TABLE IF NOT EXISTS `mt_promotion_ad_hourly_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '店铺ID',
  `biz_date` date NOT NULL COMMENT '业务日期',
  `channel` int NOT NULL COMMENT '推广渠道，1=点金推广',
  `time_range` varchar(32) NOT NULL COMMENT '时间段，例如 10:00-11:00',
  `show_count` int NOT NULL DEFAULT 0 COMMENT '曝光量',
  `click_count` int NOT NULL DEFAULT 0 COMMENT '点击量',
  `debit_cents` bigint NOT NULL DEFAULT 0 COMMENT '推广消耗，单位分',
  `payload_json` longtext COMMENT '原始响应及解密结果',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_promotion_ad_hourly_shop_date_channel_time` (`shop_id`, `biz_date`, `channel`, `time_range`),
  KEY `idx_mt_promotion_ad_hourly_shop_date` (`shop_id`, `biz_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店推广广告分时明细数据';

CREATE TABLE IF NOT EXISTS `mt_promotion_ad_realtime_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '店铺ID',
  `biz_date` date NOT NULL COMMENT '业务日期',
  `slot_time` datetime NOT NULL COMMENT '10分钟槽位时间',
  `channel` int NOT NULL COMMENT '推广渠道，1=点金推广',
  `current_hour_range` varchar(32) DEFAULT NULL COMMENT '当前小时区间，例如 10:00-11:00',
  `hour_show_count` int NOT NULL DEFAULT 0 COMMENT '当前小时累计曝光量',
  `hour_click_count` int NOT NULL DEFAULT 0 COMMENT '当前小时累计点击量',
  `hour_debit_cents` bigint NOT NULL DEFAULT 0 COMMENT '当前小时累计消耗，单位分',
  `day_show_count` int NOT NULL DEFAULT 0 COMMENT '当日累计曝光量',
  `day_click_count` int NOT NULL DEFAULT 0 COMMENT '当日累计点击量',
  `day_debit_cents` bigint NOT NULL DEFAULT 0 COMMENT '当日累计消耗，单位分',
  `payload_json` longtext COMMENT '原始响应及解密结果',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_promotion_ad_realtime_shop_slot` (`shop_id`, `biz_date`, `slot_time`),
  KEY `idx_mt_promotion_ad_realtime_shop_time` (`shop_id`, `slot_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店推广广告实时快照';
