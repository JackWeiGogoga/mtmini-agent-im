ALTER TABLE `mt_promotion_bid_record`
    ADD COLUMN IF NOT EXISTS `markdown_message` longtext COMMENT '商家可读Markdown日志' AFTER `error_msg`;
