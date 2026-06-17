ALTER TABLE `mt_promotion_bid_record`
    ADD COLUMN IF NOT EXISTS `decision_detail_json` longtext COMMENT '决策扩展详情JSON' AFTER `error_msg`;
