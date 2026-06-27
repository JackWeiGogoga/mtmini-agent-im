-- 差评自动投诉

CREATE TABLE IF NOT EXISTS `mt_comment_complaint` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '门店ID',
  `mt_comment_id` bigint DEFAULT NULL COMMENT '本地评价ID',
  `comment_id` bigint NOT NULL COMMENT '美团评价ID',
  `status` varchar(32) NOT NULL COMMENT '申诉状态 PENDING/GENERATED/SUCCESS/FAILED/SKIPPED',
  `report_type_code` int DEFAULT NULL COMMENT '美团申诉类型编码',
  `report_type_name` varchar(100) DEFAULT NULL COMMENT '美团申诉类型名称',
  `report_content` varchar(1000) DEFAULT NULL COMMENT '申诉内容',
  `has_picture` tinyint(1) DEFAULT NULL COMMENT '评价是否包含图片',
  `type_defaulted` tinyint(1) DEFAULT NULL COMMENT '申诉类型是否使用默认47',
  `type_reason` varchar(500) DEFAULT NULL COMMENT '申诉类型判断理由',
  `model_provider` varchar(64) DEFAULT NULL COMMENT '模型提供方',
  `model` varchar(128) DEFAULT NULL COMMENT '模型名称',
  `type_prompt_key` varchar(100) DEFAULT NULL COMMENT '类型判断提示词Key',
  `type_prompt_version` int DEFAULT NULL COMMENT '类型判断提示词版本',
  `content_prompt_key` varchar(100) DEFAULT NULL COMMENT '内容生成提示词Key',
  `content_prompt_version` int DEFAULT NULL COMMENT '内容生成提示词版本',
  `prompt_tokens` int DEFAULT NULL COMMENT '输入token数',
  `completion_tokens` int DEFAULT NULL COMMENT '输出token数',
  `total_tokens` int DEFAULT NULL COMMENT '总token数',
  `power_amount` int DEFAULT NULL COMMENT '消耗算力',
  `request_payload_json` longtext COMMENT '美团申诉请求JSON',
  `response_payload_json` longtext COMMENT '美团申诉响应JSON',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '失败原因',
  `submitted_at` datetime DEFAULT NULL COMMENT '提交成功时间',
  `duration_ms` int DEFAULT NULL COMMENT '处理耗时毫秒',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_comment_complaint_shop_comment` (`shop_id`,`comment_id`),
  KEY `idx_mt_comment_complaint_comment` (`comment_id`),
  KEY `idx_mt_comment_complaint_shop_status` (`shop_id`,`status`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评价差评自动投诉表';

ALTER TABLE `mt_comment`
  ADD COLUMN `complaint_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '差评申诉状态 PENDING/GENERATED/SUCCESS/FAILED/SKIPPED' AFTER `analysis_at`,
  ADD COLUMN `complaint_report_type_code` int DEFAULT NULL COMMENT '差评申诉类型编码' AFTER `complaint_status`,
  ADD COLUMN `complaint_report_content` varchar(1000) DEFAULT NULL COMMENT '差评申诉内容' AFTER `complaint_report_type_code`,
  ADD COLUMN `complaint_at` datetime DEFAULT NULL COMMENT '差评申诉成功时间' AFTER `complaint_report_content`,
  ADD COLUMN `complaint_error` varchar(1000) DEFAULT NULL COMMENT '差评申诉错误' AFTER `complaint_at`,
  ADD KEY `idx_mt_comment_shop_complaint_status` (`shop_id`,`complaint_status`,`update_time`);

INSERT INTO `system_prompt_config` (
  `prompt_key`, `prompt_name`, `prompt_type`, `content`, `default_resource_path`,
  `variables_json`, `model_hint`, `enabled`, `version`, `remark`, `create_by`, `update_by`, `create_time`, `update_time`
) VALUES
  ('COMMENT_REPORT_TYPE','差评申诉类型判断提示词','ANALYSIS','你是美团外卖商家差评申诉分类助手。请根据评价信息判断最适合的申诉类型，只输出 JSON 对象，不要解释。\n\n可选类型：\n31 骑手配送超时；32 骑手服务问题；34 用户选错评分；35 用户选择环保单；36 评价与订单不符；37 用户未收餐；39 用户未备注产生差评；40 用户提出不合理要求；41 用户以差评威胁商家；43 同行或特殊身份给差评；44 多次差评；46 不文明用语、泄露隐私、广告信息；47 内容无意义评价。\n\n输出格式固定为：{\"reportTypeCode\":47,\"reason\":\"20字以内理由\"}\n如果证据不足或无法判断，使用 47。\n\n评价类型：{{sentiment}}\n商品评分：{{productScore}}\n包装评分：{{packingScore}}\n配送评分：{{deliveryCommentScore}}\n评价标签：{{commentLabels}}\n配送时效：{{overDeliveryTimeDesc}}\n顾客首次评价：{{commentContent}}\n追评：{{addComment}}\n图片上下文：{{pictureContext}}','prompts/comment_report_type.prompt','[{\"name\":\"sentiment\",\"description\":\"评价类型 GOOD/NEUTRAL/BAD\"},{\"name\":\"productScore\",\"description\":\"商品满意度\"},{\"name\":\"packingScore\",\"description\":\"包装评价分\"},{\"name\":\"deliveryCommentScore\",\"description\":\"配送评价分\"},{\"name\":\"commentLabels\",\"description\":\"评价标签\"},{\"name\":\"overDeliveryTimeDesc\",\"description\":\"配送时效或超时描述\"},{\"name\":\"commentContent\",\"description\":\"顾客首次评价内容\"},{\"name\":\"addComment\",\"description\":\"追评内容\"},{\"name\":\"pictureContext\",\"description\":\"图片摘要或无图说明\"}]','deepseek-v4-flash',1,1,'差评自动投诉申诉类型判断提示词','system','system',NOW(),NOW()),
  ('COMMENT_REPORT_CONTENT','差评申诉内容生成提示词','ANALYSIS','你是一名资深外卖商家客服。请根据顾客评价和已选择的申诉类型，为商家生成提交给美团平台的申诉内容。\n\n要求：\n1. 300 个中文字符以内。\n2. 语气客观、克制、基于事实，不攻击顾客。\n3. 不承诺退款、赔偿、补发、私下联系或站外处理。\n4. 不使用辱骂、威胁、歧视、低俗色情、违法犯罪、政治宗教敏感、平台攻击类表达。\n5. 不输出 JSON、Markdown 或解释，只输出最终申诉文本。\n\n配送时效：{{overDeliveryTimeDesc}}\n顾客首次评价：{{commentContent}}\n追评：{{addComment}}\n图片上下文：{{pictureContext}}\n申诉类型：{{reportTypeCode}} {{reportTypeName}}\n申诉类型判断理由：{{typeReason}}','prompts/comment_report_content.prompt','[{\"name\":\"overDeliveryTimeDesc\",\"description\":\"配送时效或超时描述\"},{\"name\":\"commentContent\",\"description\":\"顾客首次评价内容\"},{\"name\":\"addComment\",\"description\":\"追评内容\"},{\"name\":\"pictureContext\",\"description\":\"图片摘要或无图说明\"},{\"name\":\"reportTypeCode\",\"description\":\"申诉类型编码\"},{\"name\":\"reportTypeName\",\"description\":\"申诉类型名称\"},{\"name\":\"typeReason\",\"description\":\"类型判断理由\"}]','deepseek-v4-flash',1,1,'差评自动投诉申诉内容生成提示词','system','system',NOW(),NOW())
ON DUPLICATE KEY UPDATE
  `prompt_name` = VALUES(`prompt_name`),
  `prompt_type` = VALUES(`prompt_type`),
  `default_resource_path` = VALUES(`default_resource_path`),
  `variables_json` = VALUES(`variables_json`),
  `model_hint` = VALUES(`model_hint`),
  `update_time` = NOW();
