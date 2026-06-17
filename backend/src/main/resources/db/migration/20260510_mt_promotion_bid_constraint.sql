CREATE TABLE IF NOT EXISTS `mt_promotion_bid_constraint` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '店铺ID',
  `poi_id` varchar(64) DEFAULT NULL COMMENT '美团门店ID',
  `max_bid_amount` decimal(10,2) NOT NULL COMMENT '推广出价上限',
  `source` varchar(32) NOT NULL COMMENT '来源',
  `raw_message` varchar(1000) DEFAULT NULL COMMENT '来源原始信息',
  `observed_at` datetime NOT NULL COMMENT '观测时间',
  `expire_at` datetime NOT NULL COMMENT '过期时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_promotion_bid_constraint_shop` (`shop_id`),
  KEY `idx_mt_promotion_bid_constraint_expire` (`expire_at`),
  KEY `idx_mt_promotion_bid_constraint_poi` (`poi_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店推广出价约束表';
