ALTER TABLE `mt_comment_reply_generation`
  ADD COLUMN `sensitive_hit_json` longtext COMMENT '敏感词命中信息JSON' AFTER `duration_ms`,
  ADD COLUMN `sensitive_rewrite_count` int DEFAULT NULL COMMENT '敏感词命中后改写次数' AFTER `sensitive_hit_json`,
  ADD COLUMN `sensitive_fallback_used` tinyint(1) DEFAULT NULL COMMENT '是否因敏感词使用兜底回复' AFTER `sensitive_rewrite_count`;
