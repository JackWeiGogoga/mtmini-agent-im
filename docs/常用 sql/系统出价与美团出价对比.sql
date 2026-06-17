SELECT
    id,
    shop_id,
    biz_date,
    slot_time,
    execute_status,
    decision_action,
    decision_reason,
    previous_bid_amount AS local_previous_bid_yuan,
    next_bid_amount AS local_next_bid_yuan,

    CAST(JSON_UNQUOTE(JSON_EXTRACT(update_payload_json, '$.statusCheck.bid')) AS UNSIGNED) AS mt_bid_before_cents,
    CAST(JSON_UNQUOTE(JSON_EXTRACT(update_payload_json, '$.bidUpdate.bid')) AS UNSIGNED) AS mt_bid_after_cents,

    CAST(JSON_UNQUOTE(JSON_EXTRACT(update_payload_json, '$.statusCheck.bid')) AS DECIMAL(10,2)) / 100 AS mt_bid_before_yuan,
    CAST(JSON_UNQUOTE(JSON_EXTRACT(update_payload_json, '$.bidUpdate.bid')) AS DECIMAL(10,2)) / 100 AS mt_bid_after_yuan,

    (
        CAST(JSON_UNQUOTE(JSON_EXTRACT(update_payload_json, '$.bidUpdate.bid')) AS SIGNED)
        - CAST(JSON_UNQUOTE(JSON_EXTRACT(update_payload_json, '$.statusCheck.bid')) AS SIGNED)
    ) AS mt_bid_diff_cents,

    JSON_UNQUOTE(JSON_EXTRACT(update_payload_json, '$.bidUpdate.trigger')) AS bid_update_trigger,
    JSON_UNQUOTE(JSON_EXTRACT(update_payload_json, '$.bidUpdate.localError')) AS bid_update_local_error,

    update_payload_json
FROM mt_promotion_bid_record
WHERE shop_id = 5
  AND biz_date = '2026-04-17'
  AND JSON_EXTRACT(update_payload_json, '$.bidUpdate') IS NOT NULL
ORDER BY slot_time DESC, id DESC;


SELECT
    slot_time,
    CAST(JSON_UNQUOTE(JSON_EXTRACT(update_payload_json, '$.statusCheck.bid')) AS DECIMAL(10,2)) / 100 AS before_bid_yuan,
    CAST(JSON_UNQUOTE(JSON_EXTRACT(update_payload_json, '$.bidUpdate.bid')) AS DECIMAL(10,2)) / 100 AS after_bid_yuan,
    next_bid_amount AS local_next_bid_yuan,
    execute_status,
    decision_reason
FROM mt_promotion_bid_record
WHERE shop_id = 5
  AND biz_date = '2026-04-17'
  AND JSON_EXTRACT(update_payload_json, '$.bidUpdate') IS NOT NULL
ORDER BY slot_time ASC, id ASC;
