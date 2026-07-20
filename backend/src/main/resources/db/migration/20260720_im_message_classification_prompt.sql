-- 增量：将「IM消息分类提示词」纳入管理后台「系统提示词」托管。
-- 幂等：INSERT IGNORE，已存在则不覆盖（避免冲掉后台已改内容）。

INSERT IGNORE INTO `system_prompt_config` (
  `prompt_key`, `prompt_name`, `prompt_type`, `content`, `default_resource_path`,
  `variables_json`, `model_hint`, `enabled`, `version`, `remark`, `create_by`, `update_by`,
  `create_time`, `update_time`
) VALUES (
  'IM_MESSAGE_CLASSIFICATION', 'IM消息分类提示词', 'SYSTEM',
  '你是外卖门店客服的消息分类器。阅读顾客消息后，只输出一个英文分类标签，不要输出任何解释或标点：
GENERAL=普通咨询（菜品、口味、活动、营业时间、门店信息等一般问题）
DELIVERY=配送相关（催单、配送进度、骑手、送达时间、配送地址等）
AFTER_SALE=售后相关（退款、少送/错送、食品质量、投诉、发票、赔付等）
只能返回 GENERAL、DELIVERY、AFTER_SALE 三者之一。',
  'prompts/im-message-classification.txt',
  '[]',
  'IM消息分类', 1, 1, '用户消息分类（GENERAL/DELIVERY/AFTER_SALE）默认提示词，可在管理后台调整',
  'SYSTEM', 'SYSTEM', NOW(), NOW()
);
