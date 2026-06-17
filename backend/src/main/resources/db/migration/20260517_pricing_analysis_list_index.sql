ALTER TABLE `mt_promotion_bid_record`
  ADD KEY `idx_mt_promotion_bid_record_slot_id` (`slot_time` DESC, `id` DESC);
