-- 增量：知识库「测试聊天」审计与限流表（含按会话分组的 session_id）
-- 幂等：可在「无表 / 有旧表缺 session_id / 已最新」任意状态下安全执行一次。

-- 1) 无表则新建（含 session_id 与两个索引）
CREATE TABLE IF NOT EXISTS `mt_im_test_chat_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '本地门店ID',
  `session_id` varchar(64) DEFAULT NULL COMMENT '测试会话ID（前端每轮对话生成，用于按会话分组）',
  `operator_user_id` varchar(64) DEFAULT NULL COMMENT '发起测试的管理员用户ID',
  `operator_name` varchar(128) DEFAULT NULL COMMENT '发起测试的管理员姓名',
  `user_message` text COMMENT '测试用户输入文本',
  `ai_reply` text COMMENT 'AI回复文本',
  `user_message_category` varchar(32) DEFAULT NULL COMMENT '用户消息分类 GENERAL/DELIVERY/AFTER_SALE',
  `rag_used` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否使用RAG上下文',
  `rag_chunk_count` int NOT NULL DEFAULT '0' COMMENT '采用知识片段数',
  `rag_max_score` decimal(8,6) DEFAULT NULL COMMENT '最高知识相似度',
  `rewrite_count` int NOT NULL DEFAULT '0' COMMENT '敏感词改写次数',
  `blocked` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否被敏感词兜底拦截',
  `model_provider` varchar(64) DEFAULT NULL COMMENT 'Chat模型供应商',
  `model_name` varchar(128) DEFAULT NULL COMMENT 'Chat模型名称',
  `prompt_tokens` int DEFAULT NULL COMMENT '输入token数',
  `completion_tokens` int DEFAULT NULL COMMENT '输出token数',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_mt_im_test_chat_log_shop_time` (`shop_id`,`create_time`),
  KEY `idx_mt_im_test_chat_log_shop_session` (`shop_id`,`session_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM知识库测试聊天审计与限流记录';

-- 2) 旧表缺 session_id 列则补列（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 守卫）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mt_im_test_chat_log' AND COLUMN_NAME = 'session_id');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `mt_im_test_chat_log` ADD COLUMN `session_id` varchar(64) DEFAULT NULL COMMENT ''测试会话ID（前端每轮对话生成，用于按会话分组）'' AFTER `shop_id`',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 旧表缺会话分组索引则补索引
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mt_im_test_chat_log' AND INDEX_NAME = 'idx_mt_im_test_chat_log_shop_session');
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE `mt_im_test_chat_log` ADD KEY `idx_mt_im_test_chat_log_shop_session` (`shop_id`,`session_id`,`id`)',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
