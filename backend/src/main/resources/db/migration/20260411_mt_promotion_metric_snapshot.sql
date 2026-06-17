CREATE TABLE IF NOT EXISTS `mt_promotion_metric_snapshot` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `shop_id` bigint NOT NULL COMMENT '店铺ID',
    `biz_date` date NOT NULL COMMENT '业务日期',
    `slot_time` datetime NOT NULL COMMENT '时段时间',
    `primary_balance` decimal(10,2) DEFAULT NULL COMMENT '主推广余额',
    `common_balance` decimal(10,2) DEFAULT NULL COMMENT '通用推广余额',
    `promotion_balance` decimal(10,2) DEFAULT NULL COMMENT '推广总余额',
    `cost_counter_value` decimal(10,2) DEFAULT NULL COMMENT '广告累计花费计数器',
    `metrics_trusted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '指标是否可信',
    `untrusted_reason` varchar(255) DEFAULT NULL COMMENT '指标不可信原因',
    `payload_json` longtext COMMENT '指标原始内容',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_mt_promotion_metric_snapshot_shop_slot` (`shop_id`, `biz_date`, `slot_time`),
    KEY `idx_mt_promotion_metric_snapshot_shop_time` (`shop_id`, `slot_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店推广指标快照表';
