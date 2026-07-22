-- 增量：将「群发文案AI优化提示词」纳入管理后台「系统提示词」托管。
-- 幂等：INSERT IGNORE，已存在则不覆盖（避免冲掉后台已改内容）。

INSERT IGNORE INTO `system_prompt_config` (
  `prompt_key`, `prompt_name`, `prompt_type`, `content`, `default_resource_path`,
  `variables_json`, `model_hint`, `enabled`, `version`, `remark`, `create_by`, `update_by`,
  `create_time`, `update_time`
) VALUES (
  'IM_BROADCAST_CONTENT_OPTIMIZE', '群发文案AI优化提示词', 'SYSTEM',
  '你是一名资深的外卖文案策划。优化用户给出的外卖群发文案，限制在{{max_length}}字以内，语气亲切、带表情符号，只输出优化后的文案正文，不要解释、不要加引号、不要输出多余内容。',
  'prompts/im-broadcast-content-optimize.txt',
  '[{"name":"max_length","description":"群发文案最大字数"}]',
  '群发文案AI优化', 1, 1, '群发文案AI优化默认提示词，可在管理后台调整',
  'SYSTEM', 'SYSTEM', NOW(), NOW()
);

INSERT IGNORE INTO `system_prompt_config` (
  `prompt_key`, `prompt_name`, `prompt_type`, `content`, `default_resource_path`,
  `variables_json`, `model_hint`, `enabled`, `version`, `remark`, `create_by`, `update_by`,
  `create_time`, `update_time`
) VALUES (
  'IM_BROADCAST_CONTENT_OPTIMIZE_REWRITE', '群发文案AI优化-敏感词改写提示词', 'SYSTEM',
  '上一版文案包含敏感信息，请在保持原意的前提下改写为合规文案，同样限制在{{max_length}}字以内、带表情符号，只输出文案正文。',
  'prompts/im-broadcast-content-optimize-rewrite.txt',
  '[{"name":"max_length","description":"群发文案最大字数"}]',
  '群发文案AI优化', 1, 1, '群发文案命中敏感词后自动改写的默认提示词，可在管理后台调整',
  'SYSTEM', 'SYSTEM', NOW(), NOW()
);
