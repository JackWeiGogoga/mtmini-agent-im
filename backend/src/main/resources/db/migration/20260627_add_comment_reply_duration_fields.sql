ALTER TABLE `mt_comment`
  ADD COLUMN `auto_reply_duration_ms` int DEFAULT NULL COMMENT '自动回复处理耗时毫秒' AFTER `auto_reply_power_amount`,
  ADD COLUMN `analysis_duration_ms` int DEFAULT NULL COMMENT 'AI分析处理耗时毫秒' AFTER `analysis_power_amount`;

ALTER TABLE `mt_comment_reply_generation`
  ADD COLUMN `duration_ms` int DEFAULT NULL COMMENT '回复生成耗时毫秒' AFTER `power_amount`;
