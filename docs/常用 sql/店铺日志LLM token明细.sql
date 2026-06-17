-- 查看某个店铺每次调用大模型生成推广日志的 token 明细
-- 使用方法：
-- 1. 把下面的 123 替换成目标 shop_id
-- 2. 如需限定时间范围，可追加 biz_date / slot_time 条件

SELECT
    r.id,
    r.shop_id,
    r.biz_date,
    r.slot_time,
    r.execute_status,
    r.log_markdown_status,
    JSON_UNQUOTE(JSON_EXTRACT(r.decision_detail_json, '$.logMarkdownSource')) AS log_markdown_source,
    CAST(JSON_UNQUOTE(JSON_EXTRACT(r.decision_detail_json, '$.logLlmPromptTokens')) AS UNSIGNED) AS log_llm_prompt_tokens,
    CAST(JSON_UNQUOTE(JSON_EXTRACT(r.decision_detail_json, '$.logLlmCompletionTokens')) AS UNSIGNED) AS log_llm_completion_tokens,
    CAST(JSON_UNQUOTE(JSON_EXTRACT(r.decision_detail_json, '$.logLlmTotalTokens')) AS UNSIGNED) AS log_llm_total_tokens,
    JSON_UNQUOTE(JSON_EXTRACT(r.decision_detail_json, '$.logLlmFailureSummary')) AS log_llm_failure_summary,
    LEFT(r.markdown_message, 120) AS markdown_preview
FROM mt_promotion_bid_record r
WHERE r.shop_id = 123
  AND JSON_UNQUOTE(JSON_EXTRACT(r.decision_detail_json, '$.logMarkdownSource')) = 'llm'
ORDER BY r.slot_time DESC, r.id DESC;


-- 如果你还想看单店汇总，可以用下面这条

SELECT
    r.shop_id,
    COUNT(*) AS llm_call_count,
    COALESCE(SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(r.decision_detail_json, '$.logLlmPromptTokens')) AS UNSIGNED)), 0) AS total_prompt_tokens,
    COALESCE(SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(r.decision_detail_json, '$.logLlmCompletionTokens')) AS UNSIGNED)), 0) AS total_completion_tokens,
    COALESCE(SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(r.decision_detail_json, '$.logLlmTotalTokens')) AS UNSIGNED)), 0) AS total_tokens,
    ROUND(
        COALESCE(SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(r.decision_detail_json, '$.logLlmPromptTokens')) AS UNSIGNED)), 0) / NULLIF(COUNT(*), 0),
        2
    ) AS avg_prompt_tokens,
    ROUND(
        COALESCE(SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(r.decision_detail_json, '$.logLlmCompletionTokens')) AS UNSIGNED)), 0) / NULLIF(COUNT(*), 0),
        2
    ) AS avg_completion_tokens,
    ROUND(
        COALESCE(SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(r.decision_detail_json, '$.logLlmTotalTokens')) AS UNSIGNED)), 0) / NULLIF(COUNT(*), 0),
        2
    ) AS avg_total_tokens
FROM mt_promotion_bid_record r
WHERE r.shop_id = 123
  AND JSON_UNQUOTE(JSON_EXTRACT(r.decision_detail_json, '$.logMarkdownSource')) = 'llm'
GROUP BY r.shop_id;
