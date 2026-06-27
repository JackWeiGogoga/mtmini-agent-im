-- 系统提示词配置与评论日志 AI 分析

CREATE TABLE IF NOT EXISTS `system_prompt_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `prompt_key` varchar(100) NOT NULL COMMENT '提示词唯一键',
  `prompt_name` varchar(100) NOT NULL COMMENT '提示词名称',
  `prompt_type` varchar(32) NOT NULL COMMENT '提示词类型 SYSTEM/USER/VISION/ANALYSIS',
  `content` longtext NOT NULL COMMENT '提示词内容',
  `default_resource_path` varchar(255) DEFAULT NULL COMMENT '默认资源路径',
  `variables_json` longtext COMMENT '变量说明JSON',
  `model_hint` varchar(128) DEFAULT NULL COMMENT '建议模型或用途说明',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_system_prompt_config_key` (`prompt_key`),
  KEY `idx_system_prompt_config_enabled` (`enabled`,`prompt_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统提示词配置表';

INSERT INTO `system_prompt_config` (
  `prompt_key`, `prompt_name`, `prompt_type`, `content`, `default_resource_path`,
  `variables_json`, `model_hint`, `enabled`, `version`, `remark`, `create_by`, `update_by`, `create_time`, `update_time`
) VALUES
  ('COMMENT_REPLY_SYSTEM_GOOD','好评回复系统提示词','SYSTEM','你是外卖商家客服。只能根据提供的知识和评价内容生成商家回复。回复必须礼貌、自然、简短，30到100个中文字符。不要编造知识库不存在的信息。不要承诺退款、赔偿、补发或私下联系，除非知识库明确提供。只输出最终商家回复文本，不要解释。好评回复：感谢顾客，结合反馈自然回应，邀请下次光临。','prompts/comment_reply_system_good.prompt','[]','deepseek-v4-flash',1,1,'评价 RAG 回复系统提示词','system','system',NOW(),NOW()),
  ('COMMENT_REPLY_SYSTEM_NEUTRAL','中评回复系统提示词','SYSTEM','你是外卖商家客服。只能根据提供的知识和评价内容生成商家回复。回复必须礼貌、自然、简短，30到100个中文字符。不要编造知识库不存在的信息。不要承诺退款、赔偿、补发或私下联系，除非知识库明确提供。只输出最终商家回复文本，不要解释。中评回复：感谢反馈，承认不足，表达改进意愿。','prompts/comment_reply_system_neutral.prompt','[]','deepseek-v4-flash',1,1,'评价 RAG 回复系统提示词','system','system',NOW(),NOW()),
  ('COMMENT_REPLY_SYSTEM_BAD','差评回复系统提示词','SYSTEM','你是外卖商家客服。只能根据提供的知识和评价内容生成商家回复。回复必须礼貌、自然、简短，30到100个中文字符。不要编造知识库不存在的信息。不要承诺退款、赔偿、补发或私下联系，除非知识库明确提供。只输出最终商家回复文本，不要解释。差评回复：先表达歉意，再回应具体问题，表达改进意愿。','prompts/comment_reply_system_bad.prompt','[]','deepseek-v4-flash',1,1,'评价 RAG 回复系统提示词','system','system',NOW(),NOW()),
  ('COMMENT_REPLY_USER','评价回复用户提示词','USER','顾客评价：\n{{commentText}}\n\n可用商户知识：\n{{knowledgeContexts}}\n\n请生成一条商家回复。','prompts/comment_reply_user.prompt','[{\"name\":\"commentText\",\"description\":\"评价文本、追评、标签、菜品和图片摘要\"},{\"name\":\"knowledgeContexts\",\"description\":\"检索命中的商户知识片段\"}]','deepseek-v4-flash',1,1,'评价 RAG 回复用户提示词','system','system',NOW(),NOW()),
  ('COMMENT_IMAGE_VISION','评价图片识别提示词','VISION','你是外卖评价图片质检助手。请识别这 {{imageCount}} 张顾客上传的外卖评价图片，只描述图片中清晰可见的事实。重点判断是否存在餐品异常、包装破损、汤汁撒漏、异物、票据或文字信息。不要推测图片之外的原因，不要生成商家回复。只输出 JSON 对象，字段固定为：{\"hasFoodIssue\":boolean,\"hasPackageIssue\":boolean,\"hasForeignObject\":boolean,\"hasLeakage\":boolean,\"hasReceiptOrText\":boolean,\"summary\":\"中文摘要，80字以内\",\"confidence\":\"HIGH|MEDIUM|LOW\"}','prompts/comment_image_vision.prompt','[{\"name\":\"imageCount\",\"description\":\"本次参与识别的图片数量\"}]','qwen3.5-flash',1,1,'评价图片识别提示词','system','system',NOW(),NOW()),
  ('COMMENT_LOG_ANALYSIS','评论日志分析提示词','ANALYSIS','你是一名外卖商家评价运营分析助手。请基于以下评价数据，输出给商家查看的分析日志文案。\n\n要求：\n1. 500 个中文字符以内。\n2. 先给整体判断，再指出主要原因和可执行改进建议。\n3. 语气专业、克制、易懂，可使用最多 2 个表情符号。\n4. 不要输出 JSON、Markdown 表格或标题编号。\n5. 不得承诺退款、补发、赔偿或私下联系顾客。\n\n评价类型：{{sentiment}}\n口味顾客评分：{{orderCommentScore}}\n配送顾客评分：{{deliveryCommentScore}}\n商品顾客评分：{{productScore}}\n包装顾客评分：{{packingScore}}\n评价标签：{{commentLabels}}\n配送时效：{{overDeliveryTimeDesc}}\n顾客首次评价内容：{{commentContent}}\n追评内容：{{addComment}}\n评价图片摘要：{{imageSummaryText}}','prompts/comment_log.prompt','[{\"name\":\"sentiment\",\"description\":\"评价类型 GOOD/NEUTRAL/BAD\"},{\"name\":\"orderCommentScore\",\"description\":\"口味顾客评分\"},{\"name\":\"deliveryCommentScore\",\"description\":\"配送顾客评分\"},{\"name\":\"productScore\",\"description\":\"商品顾客评分\"},{\"name\":\"packingScore\",\"description\":\"包装顾客评分\"},{\"name\":\"commentLabels\",\"description\":\"顾客评价标签\"},{\"name\":\"overDeliveryTimeDesc\",\"description\":\"配送时效或超时描述\"},{\"name\":\"commentContent\",\"description\":\"顾客首次评价内容\"},{\"name\":\"addComment\",\"description\":\"追评内容\"},{\"name\":\"imageSummaryText\",\"description\":\"评价图片摘要\"}]','deepseek-v4-flash',1,1,'H5 日志展示用评论分析提示词','system','system',NOW(),NOW())
ON DUPLICATE KEY UPDATE
  `prompt_name` = VALUES(`prompt_name`),
  `prompt_type` = VALUES(`prompt_type`),
  `default_resource_path` = VALUES(`default_resource_path`),
  `variables_json` = VALUES(`variables_json`),
  `model_hint` = VALUES(`model_hint`),
  `update_time` = NOW();

ALTER TABLE `mt_comment`
  ADD COLUMN `analysis_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'AI分析状态 PENDING/SUCCESS/FAILED' AFTER `auto_reply_power_amount`,
  ADD COLUMN `analysis_text` varchar(1000) DEFAULT NULL COMMENT 'AI分析日志文案' AFTER `analysis_status`,
  ADD COLUMN `analysis_model` varchar(128) DEFAULT NULL COMMENT 'AI分析模型' AFTER `analysis_text`,
  ADD COLUMN `analysis_prompt_key` varchar(100) DEFAULT NULL COMMENT 'AI分析提示词Key' AFTER `analysis_model`,
  ADD COLUMN `analysis_prompt_version` int DEFAULT NULL COMMENT 'AI分析提示词版本' AFTER `analysis_prompt_key`,
  ADD COLUMN `analysis_prompt_tokens` int DEFAULT NULL COMMENT 'AI分析输入token数' AFTER `analysis_prompt_version`,
  ADD COLUMN `analysis_completion_tokens` int DEFAULT NULL COMMENT 'AI分析输出token数' AFTER `analysis_prompt_tokens`,
  ADD COLUMN `analysis_power_amount` int DEFAULT NULL COMMENT 'AI分析消耗算力' AFTER `analysis_completion_tokens`,
  ADD COLUMN `analysis_error` varchar(1000) DEFAULT NULL COMMENT 'AI分析错误' AFTER `analysis_power_amount`,
  ADD COLUMN `analysis_at` datetime DEFAULT NULL COMMENT 'AI分析生成时间' AFTER `analysis_error`,
  ADD KEY `idx_mt_comment_shop_analysis_status` (`shop_id`,`analysis_status`,`update_time`);

INSERT IGNORE INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`,
  `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`
) VALUES
  ('MENU_SYSTEM_PROMPT','MENU_SYSTEM','系统提示词','/system/prompts','pages/system-prompt/SystemPromptManage','system:prompt:list','MessageOutlined','C',3,1,1,'2026-06-27 00:00:00','2026-06-27 00:00:00'),
  ('MENU_SYSTEM_PROMPT_QUERY','MENU_SYSTEM_PROMPT','查看提示词',NULL,NULL,'system:prompt:query',NULL,'F',0,1,1,'2026-06-27 00:00:00','2026-06-27 00:00:00'),
  ('MENU_SYSTEM_PROMPT_EDIT','MENU_SYSTEM_PROMPT','编辑提示词',NULL,NULL,'system:prompt:edit',NULL,'F',1,1,1,'2026-06-27 00:00:00','2026-06-27 00:00:00');

INSERT IGNORE INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`) VALUES
  ('ROLE_SUPER','MENU_SYSTEM_PROMPT','2026-06-27 00:00:00'),
  ('ROLE_SUPER','MENU_SYSTEM_PROMPT_QUERY','2026-06-27 00:00:00'),
  ('ROLE_SUPER','MENU_SYSTEM_PROMPT_EDIT','2026-06-27 00:00:00'),
  ('ROLE_ADMIN','MENU_SYSTEM_PROMPT','2026-06-27 00:00:00'),
  ('ROLE_ADMIN','MENU_SYSTEM_PROMPT_QUERY','2026-06-27 00:00:00'),
  ('ROLE_ADMIN','MENU_SYSTEM_PROMPT_EDIT','2026-06-27 00:00:00');
