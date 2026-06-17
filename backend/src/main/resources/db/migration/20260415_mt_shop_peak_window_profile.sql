CREATE TABLE `mt_shop_peak_window_profile`
(
    `id`                 bigint      NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    `shop_id`            bigint      NOT NULL COMMENT '门店 ID',
    `day_type`           varchar(16) NOT NULL COMMENT '日型: WEEKDAY/WEEKEND/HOLIDAY',
    `profile_status`     varchar(32) NOT NULL COMMENT '画像状态: USABLE/INSUFFICIENT',
    `lookback_days`      int         NOT NULL COMMENT '回看天数',
    `sample_day_count`   int         NOT NULL DEFAULT 0 COMMENT '样本天数',
    `sample_order_count` int         NOT NULL DEFAULT 0 COMMENT '样本订单数',
    `peak_windows_json`  text COMMENT '高峰窗口 JSON',
    `unavailable_reason` varchar(64)          DEFAULT NULL COMMENT '画像不可用原因',
    `generated_at`       datetime    NOT NULL COMMENT '画像生成时间',
    `create_time`        datetime    NOT NULL COMMENT '创建时间',
    `update_time`        datetime    NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_shop_day_type` (`shop_id`, `day_type`),
    KEY `idx_generated_at` (`generated_at`)
) COMMENT ='门店工作日/周末高峰画像';
