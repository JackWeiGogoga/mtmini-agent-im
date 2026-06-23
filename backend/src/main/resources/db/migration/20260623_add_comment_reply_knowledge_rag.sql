-- 评论知识库与 RAG 自动回复

ALTER TABLE `mt_comment`
  MODIFY COLUMN `auto_reply_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '自动回复状态 PENDING/SUCCESS/FAILED/SKIPPED',
  ADD COLUMN `auto_reply_source` varchar(32) DEFAULT NULL COMMENT '自动回复来源 OFFICIAL_RANDOM/SHOP_RAG/FALLBACK_OFFICIAL',
  ADD COLUMN `auto_reply_sentiment` varchar(32) DEFAULT NULL COMMENT '自动回复评论类型 GOOD/NEUTRAL/BAD',
  ADD COLUMN `auto_reply_generation_id` bigint DEFAULT NULL COMMENT '自动回复生成记录ID',
  ADD COLUMN `auto_reply_knowledge_hit_json` longtext COMMENT '自动回复命中知识JSON',
  ADD COLUMN `auto_reply_model` varchar(128) DEFAULT NULL COMMENT '自动回复模型',
  ADD COLUMN `auto_reply_power_amount` int DEFAULT NULL COMMENT '自动回复消耗算力';

CREATE TABLE IF NOT EXISTS `mt_reply_knowledge_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint DEFAULT NULL COMMENT '门店ID，官方知识为空',
  `scope` varchar(32) NOT NULL COMMENT '知识范围 OFFICIAL/SHOP',
  `source_type` varchar(32) NOT NULL COMMENT '知识来源类型 TEMPLATE/QA',
  `sentiment` varchar(32) NOT NULL COMMENT '评论类型 GOOD/NEUTRAL/BAD/UNKNOWN',
  `title` varchar(255) NOT NULL COMMENT '标题',
  `question` longtext COMMENT '问题/评论场景',
  `answer` longtext NOT NULL COMMENT '回答/回复文案',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_mt_reply_knowledge_item_shop` (`shop_id`,`source_type`,`deleted`,`enabled`),
  KEY `idx_mt_reply_knowledge_item_official` (`scope`,`source_type`,`sentiment`,`deleted`,`enabled`),
  KEY `idx_mt_reply_knowledge_item_sentiment` (`sentiment`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论回复知识条目表';

CREATE TABLE IF NOT EXISTS `mt_reply_knowledge_document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '门店ID',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_type` varchar(16) NOT NULL COMMENT '文件类型 TXT/DOCX/XLSX',
  `file_size` bigint NOT NULL COMMENT '文件大小字节',
  `storage_provider` varchar(32) NOT NULL COMMENT '存储提供方 TENCENT_COS',
  `object_key` varchar(500) NOT NULL COMMENT '对象存储Key',
  `parse_status` varchar(32) NOT NULL COMMENT '解析状态 PENDING/PARSING/SUCCESS/FAILED',
  `parse_error` varchar(1000) DEFAULT NULL COMMENT '解析失败原因',
  `chunk_count` int NOT NULL DEFAULT '0' COMMENT '解析片段数',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_mt_reply_knowledge_document_shop` (`shop_id`,`deleted`,`enabled`),
  KEY `idx_mt_reply_knowledge_document_status` (`parse_status`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论回复知识文档表';

CREATE TABLE IF NOT EXISTS `mt_reply_knowledge_chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '门店ID',
  `source_type` varchar(32) NOT NULL COMMENT '片段来源 QA/DOCUMENT',
  `source_id` bigint NOT NULL COMMENT '来源ID',
  `item_id` bigint DEFAULT NULL COMMENT '手工QA知识ID',
  `document_id` bigint DEFAULT NULL COMMENT '文档ID',
  `sentiment` varchar(32) NOT NULL COMMENT '评论类型 GOOD/NEUTRAL/BAD/UNKNOWN',
  `chunk_text` longtext NOT NULL COMMENT '片段文本',
  `embedding_provider` varchar(64) DEFAULT NULL COMMENT '向量提供方',
  `embedding_model` varchar(128) DEFAULT NULL COMMENT '向量模型',
  `embedding_dim` int DEFAULT NULL COMMENT '向量维度',
  `embedding_blob` mediumblob COMMENT 'float32向量二进制',
  `token_count` int DEFAULT NULL COMMENT '估算token数',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_mt_reply_knowledge_chunk_shop` (`shop_id`,`deleted`,`enabled`,`sentiment`),
  KEY `idx_mt_reply_knowledge_chunk_source` (`source_type`,`source_id`),
  KEY `idx_mt_reply_knowledge_chunk_item` (`item_id`),
  KEY `idx_mt_reply_knowledge_chunk_document` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论回复知识片段表';

CREATE TABLE IF NOT EXISTS `mt_comment_reply_generation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '门店ID',
  `mt_comment_id` bigint DEFAULT NULL COMMENT '本地评价ID',
  `comment_id` bigint NOT NULL COMMENT '美团评价ID',
  `reply_source` varchar(32) NOT NULL COMMENT '回复来源 OFFICIAL_RANDOM/SHOP_RAG/FALLBACK_OFFICIAL',
  `sentiment` varchar(32) DEFAULT NULL COMMENT '评论类型 GOOD/NEUTRAL/BAD',
  `reply_content` varchar(1000) DEFAULT NULL COMMENT '生成回复内容',
  `official_item_id` bigint DEFAULT NULL COMMENT '官方文案ID',
  `knowledge_hit_json` longtext COMMENT '命中知识JSON',
  `llm_provider` varchar(64) DEFAULT NULL COMMENT '大模型提供方',
  `llm_model` varchar(128) DEFAULT NULL COMMENT '大模型',
  `embedding_provider` varchar(64) DEFAULT NULL COMMENT '向量提供方',
  `embedding_model` varchar(128) DEFAULT NULL COMMENT '向量模型',
  `prompt_tokens` int DEFAULT NULL COMMENT '输入token数',
  `completion_tokens` int DEFAULT NULL COMMENT '输出token数',
  `power_amount` int DEFAULT NULL COMMENT '消耗算力',
  `fallback_reason` varchar(255) DEFAULT NULL COMMENT '兜底原因',
  `error_stage` varchar(64) DEFAULT NULL COMMENT '失败阶段',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '失败原因',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_mt_comment_reply_generation_comment` (`comment_id`),
  KEY `idx_mt_comment_reply_generation_shop_time` (`shop_id`,`create_time`),
  KEY `idx_mt_comment_reply_generation_source` (`reply_source`,`sentiment`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评价自动回复生成记录表';

-- 管理后台官方回复文案菜单与权限。
-- auth_init_data.sql 只用于新库初始化；已部署环境必须通过 migration 补齐。
INSERT IGNORE INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`,
  `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`
) VALUES
  ('MENU_COMMENT_REPLY_KNOWLEDGE','0','回复文案','/comment-reply-knowledge','pages/comment-reply-knowledge/CommentReplyKnowledgeManage','comment:reply-knowledge:list','MessageOutlined','C',12,1,1,'2026-06-23 01:00:00','2026-06-23 01:00:00'),
  ('MENU_COMMENT_REPLY_KNOWLEDGE_ADD','MENU_COMMENT_REPLY_KNOWLEDGE','新增回复文案',NULL,NULL,'comment:reply-knowledge:add',NULL,'F',0,1,1,'2026-06-23 01:00:00','2026-06-23 01:00:00'),
  ('MENU_COMMENT_REPLY_KNOWLEDGE_EDIT','MENU_COMMENT_REPLY_KNOWLEDGE','编辑回复文案',NULL,NULL,'comment:reply-knowledge:edit',NULL,'F',1,1,1,'2026-06-23 01:00:00','2026-06-23 01:00:00'),
  ('MENU_COMMENT_REPLY_KNOWLEDGE_DELETE','MENU_COMMENT_REPLY_KNOWLEDGE','删除回复文案',NULL,NULL,'comment:reply-knowledge:delete',NULL,'F',2,1,1,'2026-06-23 01:00:00','2026-06-23 01:00:00');

INSERT IGNORE INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`) VALUES
  ('ROLE_SUPER','MENU_COMMENT_REPLY_KNOWLEDGE','2026-06-23 01:00:00'),
  ('ROLE_SUPER','MENU_COMMENT_REPLY_KNOWLEDGE_ADD','2026-06-23 01:00:00'),
  ('ROLE_SUPER','MENU_COMMENT_REPLY_KNOWLEDGE_EDIT','2026-06-23 01:00:00'),
  ('ROLE_SUPER','MENU_COMMENT_REPLY_KNOWLEDGE_DELETE','2026-06-23 01:00:00');
