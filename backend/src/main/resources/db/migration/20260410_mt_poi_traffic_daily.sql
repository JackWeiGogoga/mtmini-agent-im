CREATE TABLE IF NOT EXISTS `mt_poi_traffic_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '店铺ID',
  `biz_date` date NOT NULL COMMENT '业务日期',
  `flow_prop` int NOT NULL DEFAULT 3 COMMENT '流量类型 1自然流量 2付费流量 3全部流量',
  `exposure_count` int NOT NULL DEFAULT 0 COMMENT '曝光量',
  `visit_count` int NOT NULL DEFAULT 0 COMMENT '入店量',
  `payload_json` longtext COMMENT '原始返回与解析结果JSON',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_poi_traffic_daily_shop_date_flow` (`shop_id`, `biz_date`, `flow_prop`),
  KEY `idx_mt_poi_traffic_daily_shop_date` (`shop_id`, `biz_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店营业分析日流量数据表';
