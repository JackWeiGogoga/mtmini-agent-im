ALTER TABLE `mt_shop`
    ADD COLUMN `historical_order_backfill_status` varchar(32) NOT NULL DEFAULT 'INIT' COMMENT '历史订单补数状态' AFTER `shipping_time`,
    ADD COLUMN `historical_order_backfill_at` datetime DEFAULT NULL COMMENT '历史订单补数最近一次处理时间' AFTER `historical_order_backfill_status`;
