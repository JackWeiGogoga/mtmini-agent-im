ALTER TABLE `mt_promotion_bid_record`
  DROP INDEX `idx_mt_promotion_bid_record_shop_window_status`,
  ADD KEY `idx_mt_promotion_bid_record_shop_window_status_slot` (`shop_id`, `window_key`, `execute_status`, `slot_time`),
  ADD KEY `idx_mt_promotion_bid_record_shop_biz_reason_slot` (`shop_id`, `biz_date`, `decision_reason`, `slot_time`);

ALTER TABLE `mt_order`
  ADD KEY `idx_mt_order_shop_send_status` (`shop_id`, `order_send_time`, `status`);

ALTER TABLE `mt_promotion_setting`
  ADD KEY `idx_mt_promotion_setting_enabled_configured_shop` (`promotion_enabled`, `strategy_configured`, `shop_id`);
