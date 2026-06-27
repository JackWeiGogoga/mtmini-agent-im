-- 评价回复改为始终优先使用大模型；商户知识仅作为高匹配上下文补充。

ALTER TABLE `mt_comment`
  MODIFY COLUMN `auto_reply_source` varchar(32) DEFAULT NULL COMMENT '自动回复来源 OFFICIAL_RANDOM/LLM/SHOP_RAG/FALLBACK_OFFICIAL';

ALTER TABLE `mt_comment_reply_generation`
  MODIFY COLUMN `reply_source` varchar(32) NOT NULL COMMENT '回复来源 OFFICIAL_RANDOM/LLM/SHOP_RAG/FALLBACK_OFFICIAL';

UPDATE `system_prompt_config`
SET `content` = '你是外卖商家客服。请根据顾客评价生成商家回复；如果提供了商户知识，只能在和评价高度相关时作为补充依据。回复必须礼貌、自然、简短，30到100个中文字符。不要编造评价和商户知识之外的具体事实。不要承诺退款、赔偿、补发或私下联系，除非商户知识明确提供。只输出最终商家回复文本，不要解释。好评回复：感谢顾客，结合反馈自然回应，邀请下次光临。',
    `version` = `version` + 1,
    `update_by` = 'system',
    `update_time` = NOW()
WHERE `prompt_key` = 'COMMENT_REPLY_SYSTEM_GOOD';

UPDATE `system_prompt_config`
SET `content` = '你是外卖商家客服。请根据顾客评价生成商家回复；如果提供了商户知识，只能在和评价高度相关时作为补充依据。回复必须礼貌、自然、简短，30到100个中文字符。不要编造评价和商户知识之外的具体事实。不要承诺退款、赔偿、补发或私下联系，除非商户知识明确提供。只输出最终商家回复文本，不要解释。中评回复：感谢反馈，承认不足，表达改进意愿。',
    `version` = `version` + 1,
    `update_by` = 'system',
    `update_time` = NOW()
WHERE `prompt_key` = 'COMMENT_REPLY_SYSTEM_NEUTRAL';

UPDATE `system_prompt_config`
SET `content` = '你是外卖商家客服。请根据顾客评价生成商家回复；如果提供了商户知识，只能在和评价高度相关时作为补充依据。回复必须礼貌、自然、简短，30到100个中文字符。不要编造评价和商户知识之外的具体事实。不要承诺退款、赔偿、补发或私下联系，除非商户知识明确提供。只输出最终商家回复文本，不要解释。差评回复：先表达歉意，再回应具体问题，表达改进意愿。',
    `version` = `version` + 1,
    `update_by` = 'system',
    `update_time` = NOW()
WHERE `prompt_key` = 'COMMENT_REPLY_SYSTEM_BAD';

UPDATE `system_prompt_config`
SET `content` = '顾客评价：\n{{commentText}}\n\n高匹配商户知识（无则显示“无”）：\n{{knowledgeContexts}}\n\n请生成一条商家回复。',
    `variables_json` = '[{\"name\":\"commentText\",\"description\":\"评价文本、追评、标签、菜品和图片摘要\"},{\"name\":\"knowledgeContexts\",\"description\":\"达到相似度阈值的商户知识片段，无命中时为无\"}]',
    `version` = `version` + 1,
    `update_by` = 'system',
    `update_time` = NOW()
WHERE `prompt_key` = 'COMMENT_REPLY_USER';
