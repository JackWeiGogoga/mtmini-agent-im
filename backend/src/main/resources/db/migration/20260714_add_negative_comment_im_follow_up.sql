-- 差评评价匿名群聊跟进与全量评价处理日志

CREATE TABLE IF NOT EXISTS `mt_comment_im_follow_up` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '门店ID',
  `mt_comment_id` bigint NOT NULL COMMENT '本地评价ID',
  `comment_id` bigint NOT NULL COMMENT '美团评价ID',
  `status` varchar(32) NOT NULL COMMENT 'PENDING/GENERATED/GROUP_CREATED/SUCCESS/FAILED/SKIPPED',
  `failed_stage` varchar(32) DEFAULT NULL COMMENT '失败阶段',
  `error_code` varchar(128) DEFAULT NULL COMMENT '安全错误码',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '安全错误摘要',
  `message_content` varchar(1000) DEFAULT NULL COMMENT '发送消息明文',
  `message_source` varchar(32) DEFAULT NULL COMMENT 'MODEL/FALLBACK_STATIC',
  `fallback_reason` varchar(500) DEFAULT NULL COMMENT '兜底原因',
  `model_provider` varchar(64) DEFAULT NULL COMMENT '模型提供方',
  `model` varchar(128) DEFAULT NULL COMMENT '模型名称',
  `prompt_key` varchar(100) DEFAULT NULL COMMENT '提示词Key',
  `prompt_version` int DEFAULT NULL COMMENT '提示词版本',
  `prompt_tokens` int DEFAULT NULL COMMENT '输入token数',
  `completion_tokens` int DEFAULT NULL COMMENT '输出token数',
  `total_tokens` int DEFAULT NULL COMMENT '总token数',
  `power_amount` int DEFAULT NULL COMMENT '消耗算力',
  `generated_at` datetime DEFAULT NULL COMMENT '消息生成时间',
  `create_group_attempted_at` datetime DEFAULT NULL COMMENT '唯一建群真实尝试时间',
  `group_id` bigint DEFAULT NULL COMMENT '美团群聊ID',
  `new_group` varchar(64) DEFAULT NULL COMMENT '是否新建群原始值',
  `group_ctime` bigint DEFAULT NULL COMMENT '美团群创建时间戳',
  `group_state` int DEFAULT NULL COMMENT '群状态',
  `group_close_type` varchar(64) DEFAULT NULL COMMENT '群关闭类型',
  `create_group_response_summary` varchar(1000) DEFAULT NULL COMMENT '建群脱敏响应摘要',
  `group_created_at` datetime DEFAULT NULL COMMENT '建群成功时间',
  `message_id` bigint DEFAULT NULL COMMENT '三方唯一消息ID',
  `message_cts` bigint DEFAULT NULL COMMENT '首次发送10位秒级时间戳',
  `send_attempted_at` datetime DEFAULT NULL COMMENT '唯一发送真实尝试时间',
  `message_content_hash` varchar(64) DEFAULT NULL COMMENT '消息正文SHA-256',
  `send_response_summary` varchar(1000) DEFAULT NULL COMMENT '发送脱敏响应摘要',
  `sent_at` datetime DEFAULT NULL COMMENT '发送成功时间',
  `generation_duration_ms` int DEFAULT NULL COMMENT '生成耗时毫秒',
  `create_group_duration_ms` int DEFAULT NULL COMMENT '建群耗时毫秒',
  `send_duration_ms` int DEFAULT NULL COMMENT '发送耗时毫秒',
  `duration_ms` int DEFAULT NULL COMMENT '总处理耗时毫秒',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_comment_im_shop_comment` (`shop_id`,`comment_id`),
  UNIQUE KEY `uk_mt_comment_im_message_id` (`message_id`),
  KEY `idx_mt_comment_im_mt_comment` (`mt_comment_id`),
  KEY `idx_mt_comment_im_comment` (`comment_id`),
  KEY `idx_mt_comment_im_shop_status` (`shop_id`,`status`,`update_time`),
  KEY `idx_mt_comment_im_status_time` (`status`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='差评评价匿名群聊跟进表';

ALTER TABLE `mt_comment`
  ADD COLUMN `im_follow_up_status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'IM跟进状态' AFTER `complaint_error`,
  ADD COLUMN `im_group_id` bigint DEFAULT NULL COMMENT '评价联系群ID' AFTER `im_follow_up_status`,
  ADD COLUMN `im_message_at` datetime DEFAULT NULL COMMENT '群消息发送成功时间' AFTER `im_group_id`,
  ADD COLUMN `im_follow_up_error` varchar(1000) DEFAULT NULL COMMENT 'IM跟进错误摘要' AFTER `im_message_at`,
  ADD KEY `idx_mt_comment_shop_im_status` (`shop_id`,`im_follow_up_status`,`update_time`),
  ADD KEY `idx_mt_comment_time_status` (`comment_time`,`auto_reply_status`,`complaint_status`,`im_follow_up_status`);

INSERT INTO `system_prompt_config` (
  `prompt_key`, `prompt_name`, `prompt_type`, `content`, `default_resource_path`,
  `variables_json`, `model_hint`, `enabled`, `version`, `remark`, `create_by`, `update_by`, `create_time`, `update_time`
) VALUES (
  'COMMENT_IM_FOLLOW_UP_MESSAGE',
  '差评匿名群聊首条消息提示词',
  'USER',
  '你是美团外卖商家的资深售后客服，正在评价匿名群聊中首次联系顾客。\n\n顾客评价：{{commentContent}}\n顾客追评：{{addComment}}\n\n请只输出一条要发送给顾客的中文消息，并严格满足：\n1. 先简要、准确概括顾客反馈，再真诚致歉并表达核实和解决意愿；不得编造评价之外的事实。\n2. 礼貌请顾客提供对应订单信息和方便联系的手机号码，不需要补充手机号用途说明。\n3. 可以使用“核实后给您补偿处理”等明确以核实为前提的条件式补偿表述；不得直接或无条件承诺补偿，也不得承诺红包、优惠券、退款、赔付、补发或其他具体利益，不得引导删评、改评、好评。\n4. 不得引导添加微信、QQ、扫码、点击外链或前往其他平台，不得索取验证码、身份证号、银行卡号、支付密码等高风险信息。\n5. 不超过 150 个 Unicode 字符，单段纯文本，使用 1 至 2 个自然、克制的表情符号；不输出标题、Markdown、JSON、解释或变量占位符。',
  'prompts/comment_im_follow_up_message.prompt',
  '[{"name":"commentContent","description":"顾客首次评价内容"},{"name":"addComment","description":"顾客追评内容"}]',
  'deepseek-v4-flash',1,1,'差评评价匿名群聊首条消息生成','system','system',NOW(),NOW()
) ON DUPLICATE KEY UPDATE
  `prompt_name` = VALUES(`prompt_name`),
  `prompt_type` = VALUES(`prompt_type`),
  `default_resource_path` = VALUES(`default_resource_path`),
  `variables_json` = VALUES(`variables_json`),
  `model_hint` = VALUES(`model_hint`),
  `update_time` = NOW();

INSERT IGNORE INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`,
  `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`
) VALUES
  ('MENU_COMMENT_PROCESSING_LOG','0','评价处理日志','/comment-processing-logs','pages/comment-processing-log/CommentProcessingLog','comment:processing-log:list','FileSearchOutlined','C',14,1,1,NOW(),NOW()),
  ('MENU_COMMENT_PROCESSING_LOG_QUERY','MENU_COMMENT_PROCESSING_LOG','查看评价处理详情',NULL,NULL,'comment:processing-log:query',NULL,'F',0,1,1,NOW(),NOW());

INSERT IGNORE INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`) VALUES
  ('ROLE_SUPER','MENU_COMMENT_PROCESSING_LOG',NOW()),
  ('ROLE_SUPER','MENU_COMMENT_PROCESSING_LOG_QUERY',NOW()),
  ('ROLE_ADMIN','MENU_COMMENT_PROCESSING_LOG',NOW()),
  ('ROLE_ADMIN','MENU_COMMENT_PROCESSING_LOG_QUERY',NOW());
