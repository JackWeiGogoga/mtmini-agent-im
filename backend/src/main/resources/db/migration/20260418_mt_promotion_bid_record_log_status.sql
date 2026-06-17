ALTER TABLE `mt_promotion_bid_record`
    ADD COLUMN `log_markdown_status` varchar(32) DEFAULT NULL COMMENT '日志Markdown状态 PENDING/READY/FALLBACK' AFTER `markdown_message`,
    ADD COLUMN `log_task_id` bigint DEFAULT NULL COMMENT '日志异步任务ID' AFTER `log_markdown_status`;
