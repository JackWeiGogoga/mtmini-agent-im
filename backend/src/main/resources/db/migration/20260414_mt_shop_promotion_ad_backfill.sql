ALTER TABLE `mt_shop`
    ADD COLUMN `promotion_ad_backfill_status` varchar(32) NOT NULL DEFAULT 'INIT' COMMENT '推广广告明细历史补偿状态' AFTER `shipping_time`,
    ADD COLUMN `promotion_ad_backfill_at` datetime DEFAULT NULL COMMENT '推广广告明细历史补偿最近一次处理时间' AFTER `promotion_ad_backfill_status`;
