-- 评论图片多模态识别摘要

CREATE TABLE IF NOT EXISTS `mt_comment_image_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '门店ID',
  `mt_comment_id` bigint DEFAULT NULL COMMENT '本地评价ID',
  `comment_id` bigint NOT NULL COMMENT '美团评价ID',
  `image_count` int NOT NULL DEFAULT '0' COMMENT '参与识别图片数量',
  `image_urls_json` longtext COMMENT '参与识别图片URL JSON',
  `status` varchar(32) NOT NULL COMMENT '识别状态 SUCCESS/FAILED/SKIPPED',
  `summary_json` longtext COMMENT '结构化图片摘要JSON',
  `summary_text` varchar(1000) DEFAULT NULL COMMENT '图片摘要文本',
  `model_provider` varchar(64) DEFAULT NULL COMMENT '模型提供方',
  `model` varchar(128) DEFAULT NULL COMMENT '模型名称',
  `prompt_tokens` int DEFAULT NULL COMMENT '输入token数',
  `completion_tokens` int DEFAULT NULL COMMENT '输出token数',
  `total_tokens` int DEFAULT NULL COMMENT '总token数',
  `power_amount` int DEFAULT NULL COMMENT '消耗算力',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '失败原因',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_comment_image_summary_shop_comment` (`shop_id`,`comment_id`),
  KEY `idx_mt_comment_image_summary_comment` (`comment_id`),
  KEY `idx_mt_comment_image_summary_shop_status` (`shop_id`,`status`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评价图片识别摘要表';
