-- IM 自动回复本地联调测试数据（MySQL 8）
--
-- 用途：
-- 1. H5 直登：http://localhost:5173/?wmPoiId=TEST_EPOI_REPLY_001&platform=local-test
-- 2. 管理后台按门店关键字“本地测试”或 TEST_EPOI_REPLY_001 搜索回复明细和会话
-- 3. 会话详情首屏 30 条只展示最新 Session，继续向上滚动可加载历史 Session
--
-- 安全边界：
-- 1. 脚本只重建 epoi_id=TEST_EPOI_REPLY_001 对应门店的测试数据，可重复执行
-- 2. token、订单号、用户号和消息正文均为本地虚构数据
-- 3. 美团 API dry-run 默认开启；测试真实外呼前必须显式设置 MT_API_DRY_RUN=false

SET NAMES utf8mb4;
START TRANSACTION;

SET @epoi_id := 'TEST_EPOI_REPLY_001';
SET @poi_id := 'TEST_POI_REPLY_001';
SET @op_biz_code := 'LOCAL_TEST_OP_BIZ_REPLY_001';
SET @now := NOW();

-- 保留固定 epoi_id 对应的门店主键，避免重复执行后生成新的 H5 用户。
INSERT INTO mt_shop (
    name, contact_name, contact_phone, address, latitude, longitude,
    epoi_id, poi_id, poi_name, promotion_info, logo, phone, shipping_time,
    create_time, update_time
) VALUES (
    '本地测试·IM 自动回复店', '本地测试店长', '13800000001',
    '上海市浦东新区本地测试路 100 号', '31.230370', '121.473701',
    @epoi_id, @poi_id, '本地测试·IM 自动回复店',
    '本门店仅用于本地 IM 自动回复联调', NULL, '13800000001', '09:00-22:00',
    DATE_SUB(@now, INTERVAL 30 DAY), @now
)
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id),
    name = VALUES(name),
    contact_name = VALUES(contact_name),
    contact_phone = VALUES(contact_phone),
    address = VALUES(address),
    latitude = VALUES(latitude),
    longitude = VALUES(longitude),
    poi_id = VALUES(poi_id),
    poi_name = VALUES(poi_name),
    promotion_info = VALUES(promotion_info),
    phone = VALUES(phone),
    shipping_time = VALUES(shipping_time),
    update_time = VALUES(update_time);

SET @shop_id := LAST_INSERT_ID();

-- 清理该测试门店之前产生的异步任务和业务数据，不影响其他门店。
DELETE FROM mt_async_task
WHERE (task_type = 'IM_AUTO_REPLY' AND biz_key IN (
    SELECT CAST(id AS CHAR) FROM mt_im_auto_reply WHERE shop_id = @shop_id
)) OR scope_key = CAST(@shop_id AS CHAR);

DELETE FROM mt_power_consume_detail WHERE shop_id = @shop_id;
DELETE FROM mt_power_consume WHERE shop_id = @shop_id;
DELETE FROM mt_power_recharge WHERE shop_id = @shop_id;
DELETE FROM mt_power_grant WHERE shop_id = @shop_id;
DELETE FROM mt_power_account WHERE shop_id = @shop_id;
DELETE FROM mt_knowledge_chunk WHERE shop_id = @shop_id;
DELETE FROM mt_knowledge_document WHERE shop_id = @shop_id;
DELETE FROM mt_knowledge_item WHERE shop_id = @shop_id;
DELETE FROM mt_im_auto_reply WHERE shop_id = @shop_id;
DELETE FROM mt_im_message WHERE shop_id = @shop_id;
DELETE FROM mt_im_conversation_session
WHERE conversation_id IN (SELECT id FROM mt_im_conversation WHERE shop_id = @shop_id);
DELETE FROM mt_im_conversation WHERE shop_id = @shop_id;
DELETE FROM mt_im_auto_reply_setting_history WHERE shop_id = @shop_id;
DELETE FROM mt_im_auto_reply_setting WHERE shop_id = @shop_id;
DELETE FROM mt_h5_guide_state WHERE shop_id = @shop_id;
DELETE FROM mt_shop_right WHERE shop_id = @shop_id;
DELETE FROM mt_access_token_history WHERE shop_id = @shop_id OR op_biz_code = @op_biz_code;
DELETE FROM mt_access_token WHERE shop_id = @shop_id OR op_biz_code = @op_biz_code;

-- 模拟有效授权。dry-run 会阻止这些虚构 token 被发送到美团。
INSERT INTO mt_access_token (
    access_token, shop_id, create_time, update_time, op_biz_code, refresh_token,
    expires_at, auth_status, auth_status_changed_at, last_auth_failure_at,
    order_push_blocked, order_push_last_sync_at, order_push_last_sync_error
) VALUES (
    'LOCAL_TEST_ACCESS_TOKEN_DO_NOT_USE', @shop_id, @now, @now,
    @op_biz_code, 'LOCAL_TEST_REFRESH_TOKEN_DO_NOT_USE',
    DATE_ADD(@now, INTERVAL 10 YEAR), 1, @now, NULL, 1, @now, NULL
);

-- H5/知识库/自动回复所需权益。
INSERT INTO mt_shop_right (
    shop_id, service_code, type, expire_at, remaining_times,
    source_version, last_order_id, create_time, update_time
) VALUES
    (@shop_id, 'VIP_CODE', 2, DATE_ADD(@now, INTERVAL 1 YEAR), NULL,
     'LOCAL_TEST', 'LOCAL_TEST_RIGHT_VIP', @now, @now),
    (@shop_id, 'H5_PROMOTION', 2, DATE_ADD(@now, INTERVAL 1 YEAR), NULL,
     'LOCAL_TEST', 'LOCAL_TEST_RIGHT_H5', @now, @now);

INSERT INTO mt_im_auto_reply_setting (
    shop_id, auto_reply_enabled, create_time, update_time
) VALUES (@shop_id, 1, DATE_SUB(@now, INTERVAL 20 DAY), @now);
SET @setting_id := LAST_INSERT_ID();

INSERT INTO mt_im_auto_reply_setting_history (
    setting_id, shop_id, version_no, auto_reply_enabled, change_source,
    operator_user_id, operator_name, change_reason,
    before_snapshot_json, after_snapshot_json, create_time
) VALUES (
    @setting_id, @shop_id, 1, 1, 'SYSTEM',
    'LOCAL_SEED', '本地测试脚本', 'LOCAL_TEST_SEED',
    '{"autoReplyEnabled":false}', '{"autoReplyEnabled":true}',
    DATE_SUB(@now, INTERVAL 20 DAY)
);

-- 算力账户和少量流水，便于首页/算力页联调。
INSERT INTO mt_power_account (
    shop_id, balance, recharge_balance, grant_balance,
    total_recharge, total_grant, total_consume, version, create_time, update_time
) VALUES (
    @shop_id, 1998200, 1498200, 500000,
    1500000, 500000, 1800, 1, DATE_SUB(@now, INTERVAL 30 DAY), @now
);

INSERT INTO mt_power_recharge (
    recharge_no, shop_id, power_amount, unit_power, unit_price_fen,
    original_amount_fen, discount_rate_basis_points, total_amount_fen,
    pay_channel, pay_status, payment_order_no, transaction_id,
    paid_at, create_time, update_time
) VALUES (
    CONCAT('LOCAL_RECHARGE_', @shop_id), @shop_id, 1500000, 100000, 100,
    1500, 10000, 1500, 'LOCAL_TEST', 2,
    CONCAT('LOCAL_PAYMENT_', @shop_id), CONCAT('LOCAL_TX_', @shop_id),
    DATE_SUB(@now, INTERVAL 25 DAY), DATE_SUB(@now, INTERVAL 25 DAY), DATE_SUB(@now, INTERVAL 25 DAY)
);

INSERT INTO mt_power_grant (
    grant_no, shop_id, power_amount, grant_reason, remark,
    operator_user_id, operator_username, status, create_time, update_time
) VALUES (
    CONCAT('LOCAL_GRANT_', @shop_id), @shop_id, 500000,
    '本地 IM 自动回复联调', '虚构测试流水',
    'LOCAL_SEED', '本地测试脚本', 'SUCCESS',
    DATE_SUB(@now, INTERVAL 25 DAY), DATE_SUB(@now, INTERVAL 25 DAY)
);

INSERT INTO mt_power_consume (
    consume_no, shop_id, source_type, source_id, request_no, power_amount,
    balance_before, balance_after, remark, create_time, update_time
) VALUES
    (CONCAT('LOCAL_CONSUME_1_', @shop_id), @shop_id, 'IM_AUTO_REPLY', 'LOCAL_REPLY_1',
     CONCAT('LOCAL_REQUEST_1_', @shop_id), 600, 2000000, 1999400,
     '本地自动回复测试', DATE_SUB(@now, INTERVAL 3 DAY), DATE_SUB(@now, INTERVAL 3 DAY)),
    (CONCAT('LOCAL_CONSUME_2_', @shop_id), @shop_id, 'IM_AUTO_REPLY', 'LOCAL_REPLY_2',
     CONCAT('LOCAL_REQUEST_2_', @shop_id), 600, 1999400, 1998800,
     '本地自动回复测试', DATE_SUB(@now, INTERVAL 1 DAY), DATE_SUB(@now, INTERVAL 1 DAY)),
    (CONCAT('LOCAL_CONSUME_3_', @shop_id), @shop_id, 'IM_AUTO_REPLY', 'LOCAL_REPLY_3',
     CONCAT('LOCAL_REQUEST_3_', @shop_id), 600, 1998800, 1998200,
     '本地自动回复测试', @now, @now);

-- 可用知识库：两个 QA 和一个已解析文档切片；向量为合法的 1024 维 little-endian float32。
SET @test_embedding := UNHEX(CONCAT('0000803F', REPEAT('00000000', 1023)));

INSERT INTO mt_knowledge_item (
    shop_id, title, question, answer, enabled, deleted, create_time, update_time
) VALUES (
    @shop_id, '营业时间', '你们几点营业？晚上几点关门？',
    '门店每天 09:00 至 22:00 营业，节假日如有调整会在店铺公告说明。',
    1, 0, DATE_SUB(@now, INTERVAL 10 DAY), @now
);
SET @knowledge_item_1 := LAST_INSERT_ID();

INSERT INTO mt_knowledge_chunk (
    shop_id, source_type, source_id, item_id, document_id, chunk_text,
    embedding_provider, embedding_model, embedding_dim, embedding_blob,
    token_count, enabled, deleted, create_time, update_time
) VALUES (
    @shop_id, 'QA', @knowledge_item_1, @knowledge_item_1, NULL,
    '问题：你们几点营业？晚上几点关门？\n回答：门店每天 09:00 至 22:00 营业。',
    'DASHSCOPE', 'text-embedding-v4', 1024, @test_embedding,
    36, 1, 0, DATE_SUB(@now, INTERVAL 10 DAY), @now
);

INSERT INTO mt_knowledge_item (
    shop_id, title, question, answer, enabled, deleted, create_time, update_time
) VALUES (
    @shop_id, '餐具和备注', '可以多放餐具和少辣吗？',
    '可以，请在订单备注中写明餐具数量和辣度；门店会尽量按备注准备。',
    1, 0, DATE_SUB(@now, INTERVAL 9 DAY), @now
);
SET @knowledge_item_2 := LAST_INSERT_ID();

INSERT INTO mt_knowledge_chunk (
    shop_id, source_type, source_id, item_id, document_id, chunk_text,
    embedding_provider, embedding_model, embedding_dim, embedding_blob,
    token_count, enabled, deleted, create_time, update_time
) VALUES (
    @shop_id, 'QA', @knowledge_item_2, @knowledge_item_2, NULL,
    '问题：可以多放餐具和少辣吗？\n回答：请在订单备注中写明餐具数量和辣度。',
    'DASHSCOPE', 'text-embedding-v4', 1024, @test_embedding,
    32, 1, 0, DATE_SUB(@now, INTERVAL 9 DAY), @now
);

INSERT INTO mt_knowledge_document (
    shop_id, file_name, file_type, file_size, storage_provider, object_key,
    parse_status, parse_error, chunk_count, enabled, deleted, create_time, update_time
) VALUES (
    @shop_id, '本地测试门店说明.txt', 'TXT', 256, 'LOCAL_TEST',
    CONCAT('local-test/', @epoi_id, '/shop-guide.txt'),
    'SUCCESS', NULL, 1, 1, 0, DATE_SUB(@now, INTERVAL 8 DAY), @now
);
SET @knowledge_document := LAST_INSERT_ID();

INSERT INTO mt_knowledge_chunk (
    shop_id, source_type, source_id, item_id, document_id, chunk_text,
    embedding_provider, embedding_model, embedding_dim, embedding_blob,
    token_count, enabled, deleted, create_time, update_time
) VALUES (
    @shop_id, 'DOCUMENT', @knowledge_document, NULL, @knowledge_document,
    '本地测试门店配送范围约三公里；高峰期预计配送时间可能延长十至二十分钟。',
    'DASHSCOPE', 'text-embedding-v4', 1024, @test_embedding,
    30, 1, 0, DATE_SUB(@now, INTERVAL 8 DAY), @now
);

-- 主 Conversation：三个 Session，共 34 条统一消息，首屏 30 条后仍可加载历史 Session。
SET @open_user_id := 880000001;
SET @order_id := 990000001;
SET @session_1_start := TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 14 DAY), '11:00:00');
SET @session_2_start := TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '18:00:00');
SET @session_3_start := DATE_SUB(@now, INTERVAL 60 MINUTE);

INSERT INTO mt_im_conversation (
    shop_id, open_user_id, message_count, user_message_count, ai_message_count,
    merchant_message_count, session_count, version, create_time, update_time
) VALUES (@shop_id, @open_user_id, 0, 0, 0, 0, 0, 0, @session_1_start, @now);
SET @conversation_id := LAST_INSERT_ID();

INSERT INTO mt_im_conversation_session (
    conversation_id, session_no, status, boundary_reason, started_at,
    last_active_at, closed_at, message_count, user_message_count,
    ai_message_count, merchant_message_count, create_time, update_time
) VALUES (
    @conversation_id, 1, 'CLOSED', 'INITIAL', @session_1_start,
    DATE_ADD(@session_1_start, INTERVAL 11 MINUTE), DATE_ADD(@session_1_start, INTERVAL 41 MINUTE),
    0, 0, 0, 0, @session_1_start, DATE_ADD(@session_1_start, INTERVAL 41 MINUTE)
);
SET @session_1_id := LAST_INSERT_ID();

INSERT INTO mt_im_conversation_session (
    conversation_id, session_no, status, boundary_reason, started_at,
    last_active_at, closed_at, message_count, user_message_count,
    ai_message_count, merchant_message_count, create_time, update_time
) VALUES (
    @conversation_id, 2, 'CLOSED', 'IDLE_TIMEOUT', @session_2_start,
    DATE_ADD(@session_2_start, INTERVAL 11 MINUTE), DATE_ADD(@session_2_start, INTERVAL 41 MINUTE),
    0, 0, 0, 0, @session_2_start, DATE_ADD(@session_2_start, INTERVAL 41 MINUTE)
);
SET @session_2_id := LAST_INSERT_ID();

INSERT INTO mt_im_conversation_session (
    conversation_id, session_no, status, boundary_reason, started_at,
    last_active_at, closed_at, message_count, user_message_count,
    ai_message_count, merchant_message_count, create_time, update_time
) VALUES (
    @conversation_id, 3, 'ACTIVE', 'IDLE_TIMEOUT', @session_3_start,
    DATE_ADD(@session_3_start, INTERVAL 11 MINUTE), NULL,
    0, 0, 0, 0, @session_3_start, @now
);
SET @session_3_id := LAST_INSERT_ID();

CREATE TEMPORARY TABLE tmp_local_im_pairs (
    pair_no INT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    event_time DATETIME NOT NULL,
    user_content VARCHAR(500) NOT NULL,
    ai_content VARCHAR(500),
    reply_status VARCHAR(32) NOT NULL,
    ai_delivery_status VARCHAR(32),
    rag_used TINYINT(1) NOT NULL DEFAULT 0
);

INSERT INTO tmp_local_im_pairs (
    pair_no, session_id, event_time, user_content, ai_content,
    reply_status, ai_delivery_status, rag_used
) VALUES
    (1,  @session_1_id, @session_1_start, '你好，请问今天营业吗？', '您好，今天正常营业，营业时间是 09:00 至 22:00。', 'SENT', 'SENT', 1),
    (2,  @session_1_id, DATE_ADD(@session_1_start, INTERVAL 2 MINUTE), '晚上九点还能下单吗？', '可以的，晚上九点仍在营业时间内，欢迎下单。', 'SENT', 'SENT', 1),
    (3,  @session_1_id, DATE_ADD(@session_1_start, INTERVAL 4 MINUTE), '可以少辣吗？', '可以，请在订单备注里写明“少辣”，门店会尽量按备注制作。', 'SENT', 'SENT', 1),
    (4,  @session_1_id, DATE_ADD(@session_1_start, INTERVAL 6 MINUTE), '需要两套餐具。', '好的，请在订单备注中填写需要两套餐具。', 'SENT', 'SENT', 1),
    (5,  @session_1_id, DATE_ADD(@session_1_start, INTERVAL 8 MINUTE), '配送大概多久？', '通常以页面预计时间为准，高峰期可能延长十至二十分钟。', 'SENT', 'SENT', 1),
    (6,  @session_1_id, DATE_ADD(@session_1_start, INTERVAL 10 MINUTE), '谢谢。', '不客气，祝您用餐愉快！', 'SENT', 'SENT', 0),
    (7,  @session_2_id, @session_2_start, '我上次点的菜很好吃。', '感谢您的认可，我们会继续认真做好每一份餐。', 'SENT', 'SENT', 0),
    (8,  @session_2_id, DATE_ADD(@session_2_start, INTERVAL 2 MINUTE), '今天有活动吗？', '具体优惠请以当前店铺页面展示为准。', 'SENT', 'SENT', 0),
    (9,  @session_2_id, DATE_ADD(@session_2_start, INTERVAL 4 MINUTE), '能多放一份米饭吗？', '是否可加米饭请以商品选项为准，也可以在订单备注中说明。', 'SENT', 'SENT', 0),
    (10, @session_2_id, DATE_ADD(@session_2_start, INTERVAL 6 MINUTE), '我对花生过敏。', '请务必在订单备注中注明过敏信息，并在下单前联系门店人工确认。', 'SENT', 'SENT', 0),
    (11, @session_2_id, DATE_ADD(@session_2_start, INTERVAL 8 MINUTE), '能保证完全没有花生吗？', '这条回复用于展示发送失败记录。', 'DEAD', 'FAILED', 0),
    (12, @session_2_id, DATE_ADD(@session_2_start, INTERVAL 10 MINUTE), '算了，我再看看。', NULL, 'SUPERSEDED', NULL, 0),
    (13, @session_3_id, @session_3_start, '你好，我想确认一下营业时间。', '您好，门店今天营业至 22:00。', 'SENT', 'SENT', 1),
    (14, @session_3_id, DATE_ADD(@session_3_start, INTERVAL 2 MINUTE), '现在下单来得及吗？', '来得及，您可以按页面展示的预计送达时间下单。', 'SENT', 'SENT', 0),
    (15, @session_3_id, DATE_ADD(@session_3_start, INTERVAL 4 MINUTE), '我要少辣并放三套餐具。', '好的，请在订单备注中写明少辣和三套餐具。', 'SENT', 'SENT', 1),
    (16, @session_3_id, DATE_ADD(@session_3_start, INTERVAL 6 MINUTE), '如果下雨会慢吗？', '这条回复用于展示发送中状态。', 'SENDING', 'SENDING', 0),
    (17, @session_3_id, DATE_ADD(@session_3_start, INTERVAL 8 MINUTE), '我已经联系人工了。', NULL, 'SKIPPED', NULL, 0),
    (18, @session_3_id, DATE_ADD(@session_3_start, INTERVAL 10 MINUTE), '好的，谢谢你。', '不客气，如有其他问题随时告诉我。', 'SENT', 'SENT', 0);

-- 扩充最新 Session，使首屏 30 条只落在最新 Session，第二次加载才进入历史 Session。
INSERT INTO tmp_local_im_pairs (
    pair_no, session_id, event_time, user_content, ai_content,
    reply_status, ai_delivery_status, rag_used
)
SELECT
    seq.pair_no,
    @session_3_id,
    TIMESTAMPADD(MINUTE, (seq.pair_no - 13) * 2, @session_3_start),
    CONCAT('本地滚动测试问题 ', seq.pair_no - 18, '：还能继续查看历史消息吗？'),
    CONCAT('可以，这是最新 Session 的第 ', seq.pair_no - 18, ' 条滚动测试回复。'),
    'SENT', 'SENT', IF(MOD(seq.pair_no, 5) = 0, 1, 0)
FROM (
    SELECT 19 AS pair_no UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22
    UNION ALL SELECT 23 UNION ALL SELECT 24 UNION ALL SELECT 25 UNION ALL SELECT 26
    UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30
    UNION ALL SELECT 31 UNION ALL SELECT 32 UNION ALL SELECT 33 UNION ALL SELECT 34
) seq;

INSERT INTO mt_im_message (
    shop_id, conversation_id, session_id, record_source, sender_role,
    reply_to_message_id, auto_reply_id, delivery_status, event_time,
    callback_msg_id, callback_msg_type, developer_id, business_id, op_biz_code,
    epoi_id, callback_timestamp, sign_verified, biz_type, inner_timestamp,
    platform_msg_id, app_id, app_poi_code, msg_type, msg_source, message_cts,
    order_id, open_user_id, scene_tag, scene_name, has_reply,
    msg_content_plaintext, msg_content_hash, parse_status, decrypt_status,
    received_at, create_time, update_time
)
SELECT
    @shop_id, @conversation_id, p.session_id, 'MEITUAN_CALLBACK', 'USER',
    NULL, NULL, 'RECEIVED', p.event_time,
    CONCAT('LOCAL_TEST_USER_', LPAD(p.pair_no, 3, '0')), 'imMsg', NULL, 16, @op_biz_code,
    @epoi_id, UNIX_TIMESTAMP(p.event_time), 1, 1, UNIX_TIMESTAMP(p.event_time),
    9200000000000 + p.pair_no, 154, @epoi_id, 1, 2, UNIX_TIMESTAMP(p.event_time),
    @order_id, @open_user_id, -1, '本地测试会话', 0,
    p.user_content, SHA2(p.user_content, 256), 'SUCCESS', 'SUCCESS',
    p.event_time, p.event_time, p.event_time
FROM tmp_local_im_pairs p
ORDER BY p.pair_no;

INSERT INTO mt_im_auto_reply (
    shop_id, conversation_id, session_id, trigger_message_id, reply_message_id,
    outbound_message_id, status, skip_reason, rag_used, rag_chunk_count,
    rag_max_score, model_provider, model_name, prompt_tokens, completion_tokens,
    power_amount, rewrite_count, generated_at, billed_at, sent_at,
    last_error_code, last_error_summary, create_time, update_time
)
SELECT
    @shop_id, @conversation_id, p.session_id, u.id, NULL,
    9300000000000 + p.pair_no, p.reply_status,
    CASE p.reply_status
        WHEN 'SUPERSEDED' THEN 'NEWER_USER_MESSAGE'
        WHEN 'SKIPPED' THEN 'MANUAL_REPLY_DETECTED'
        ELSE NULL
    END,
    p.rag_used, IF(p.rag_used = 1, 2, 0), IF(p.rag_used = 1, 0.918000, NULL),
    IF(p.ai_content IS NULL, NULL, 'TOKENHUB'),
    IF(p.ai_content IS NULL, NULL, 'deepseek-v4-flash'),
    IF(p.ai_content IS NULL, NULL, 320 + p.pair_no),
    IF(p.ai_content IS NULL, NULL, 30 + p.pair_no),
    IF(p.ai_content IS NULL, NULL, 350 + p.pair_no), 0,
    IF(p.ai_content IS NULL, NULL, DATE_ADD(p.event_time, INTERVAL 30 SECOND)),
    IF(p.ai_content IS NULL, NULL, DATE_ADD(p.event_time, INTERVAL 35 SECOND)),
    IF(p.reply_status = 'SENT', DATE_ADD(p.event_time, INTERVAL 1 MINUTE), NULL),
    IF(p.reply_status = 'DEAD', 'LOCAL_TEST_SEND_FAILED', NULL),
    IF(p.reply_status = 'DEAD', '本地测试：模拟美团发送失败', NULL),
    p.event_time, DATE_ADD(p.event_time, INTERVAL 1 MINUTE)
FROM tmp_local_im_pairs p
JOIN mt_im_message u
  ON u.shop_id = @shop_id
 AND u.callback_msg_id = CONCAT('LOCAL_TEST_USER_', LPAD(p.pair_no, 3, '0'))
ORDER BY p.pair_no;

INSERT INTO mt_im_message (
    shop_id, conversation_id, session_id, record_source, sender_role,
    reply_to_message_id, auto_reply_id, delivery_status, event_time,
    callback_msg_id, callback_msg_type, developer_id, business_id, op_biz_code,
    epoi_id, callback_timestamp, sign_verified, biz_type, inner_timestamp,
    platform_msg_id, app_id, app_poi_code, msg_type, msg_source, message_cts,
    order_id, open_user_id, scene_tag, scene_name, has_reply,
    msg_content_plaintext, msg_content_hash, parse_status, decrypt_status,
    received_at, create_time, update_time
)
SELECT
    @shop_id, @conversation_id, p.session_id, 'AI_AUTO_REPLY', 'AI',
    u.id, a.id, p.ai_delivery_status, DATE_ADD(p.event_time, INTERVAL 1 MINUTE),
    NULL, NULL, NULL, 16, @op_biz_code,
    @epoi_id, NULL, 1, 1, UNIX_TIMESTAMP(DATE_ADD(p.event_time, INTERVAL 1 MINUTE)),
    a.outbound_message_id, 154, @epoi_id, 1, 1,
    UNIX_TIMESTAMP(DATE_ADD(p.event_time, INTERVAL 1 MINUTE)),
    @order_id, @open_user_id, -1, '本地测试会话', 2,
    p.ai_content, SHA2(p.ai_content, 256), 'SUCCESS', 'NOT_APPLICABLE',
    DATE_ADD(p.event_time, INTERVAL 1 MINUTE),
    DATE_ADD(p.event_time, INTERVAL 1 MINUTE),
    DATE_ADD(p.event_time, INTERVAL 1 MINUTE)
FROM tmp_local_im_pairs p
JOIN mt_im_message u
  ON u.shop_id = @shop_id
 AND u.callback_msg_id = CONCAT('LOCAL_TEST_USER_', LPAD(p.pair_no, 3, '0'))
JOIN mt_im_auto_reply a ON a.trigger_message_id = u.id
WHERE p.ai_content IS NOT NULL
ORDER BY p.pair_no;

UPDATE mt_im_auto_reply a
JOIN mt_im_message m ON m.auto_reply_id = a.id
SET a.reply_message_id = m.id
WHERE a.shop_id = @shop_id;

UPDATE mt_im_conversation_session cs
JOIN (
    SELECT session_id,
           COUNT(*) AS message_count,
           SUM(sender_role = 'USER') AS user_message_count,
           SUM(sender_role = 'AI' AND delivery_status = 'SENT') AS ai_message_count,
           SUM(sender_role = 'MERCHANT') AS merchant_message_count,
           MAX(event_time) AS last_active_at
    FROM mt_im_message
    WHERE conversation_id = @conversation_id
    GROUP BY session_id
) stat ON stat.session_id = cs.id
SET cs.message_count = stat.message_count,
    cs.user_message_count = stat.user_message_count,
    cs.ai_message_count = stat.ai_message_count,
    cs.merchant_message_count = stat.merchant_message_count,
    cs.last_active_at = stat.last_active_at,
    cs.update_time = @now;

SET @last_message_id := (
    SELECT id FROM mt_im_message
    WHERE conversation_id = @conversation_id
    ORDER BY event_time DESC, id DESC LIMIT 1
);

UPDATE mt_im_conversation
SET current_session_id = @session_3_id,
    last_message_id = @last_message_id,
    message_count = (SELECT COUNT(*) FROM mt_im_message WHERE conversation_id = @conversation_id),
    user_message_count = (SELECT COUNT(*) FROM mt_im_message WHERE conversation_id = @conversation_id AND sender_role = 'USER'),
    ai_message_count = (SELECT COUNT(*) FROM mt_im_message WHERE conversation_id = @conversation_id AND sender_role = 'AI' AND delivery_status = 'SENT'),
    merchant_message_count = (SELECT COUNT(*) FROM mt_im_message WHERE conversation_id = @conversation_id AND sender_role = 'MERCHANT'),
    session_count = 3,
    first_message_at = (SELECT MIN(event_time) FROM mt_im_message WHERE conversation_id = @conversation_id),
    last_message_at = (SELECT MAX(event_time) FROM mt_im_message WHERE conversation_id = @conversation_id),
    last_user_message_at = (SELECT MAX(event_time) FROM mt_im_message WHERE conversation_id = @conversation_id AND sender_role = 'USER'),
    last_ai_reply_at = (SELECT MAX(event_time) FROM mt_im_message WHERE conversation_id = @conversation_id AND sender_role = 'AI' AND delivery_status = 'SENT'),
    version = 1,
    update_time = @now
WHERE id = @conversation_id;

-- 第二个 Conversation：人工商家回复场景，便于验证会话列表聚合和人工消息展示。
SET @manual_open_user_id := 880000002;
SET @manual_start := DATE_SUB(@now, INTERVAL 2 HOUR);

INSERT INTO mt_im_conversation (
    shop_id, open_user_id, message_count, user_message_count, ai_message_count,
    merchant_message_count, session_count, version, create_time, update_time
) VALUES (@shop_id, @manual_open_user_id, 0, 0, 0, 0, 0, 0, @manual_start, @now);
SET @manual_conversation_id := LAST_INSERT_ID();

INSERT INTO mt_im_conversation_session (
    conversation_id, session_no, status, boundary_reason, started_at,
    last_active_at, closed_at, message_count, user_message_count,
    ai_message_count, merchant_message_count, create_time, update_time
) VALUES (
    @manual_conversation_id, 1, 'ACTIVE', 'INITIAL', @manual_start,
    DATE_ADD(@manual_start, INTERVAL 2 MINUTE), NULL,
    2, 1, 0, 1, @manual_start, @now
);
SET @manual_session_id := LAST_INSERT_ID();

INSERT INTO mt_im_message (
    shop_id, conversation_id, session_id, record_source, sender_role,
    delivery_status, event_time, callback_msg_id, callback_msg_type,
    business_id, op_biz_code, epoi_id, callback_timestamp, sign_verified,
    biz_type, inner_timestamp, platform_msg_id, app_id, app_poi_code,
    msg_type, msg_source, message_cts, order_id, open_user_id,
    scene_tag, scene_name, has_reply, msg_content_plaintext, msg_content_hash,
    parse_status, decrypt_status, received_at, create_time, update_time
) VALUES
    (@shop_id, @manual_conversation_id, @manual_session_id, 'MEITUAN_CALLBACK', 'USER',
     'RECEIVED', @manual_start, 'LOCAL_TEST_MANUAL_USER', 'imMsg',
     16, @op_biz_code, @epoi_id, UNIX_TIMESTAMP(@manual_start), 1,
     1, UNIX_TIMESTAMP(@manual_start), 9200000000101, 154, @epoi_id,
     1, 2, UNIX_TIMESTAMP(@manual_start), 990000002, @manual_open_user_id,
     -1, '人工回复测试', 0, '这份订单少了一套餐具。', SHA2('这份订单少了一套餐具。', 256),
     'SUCCESS', 'SUCCESS', @manual_start, @manual_start, @manual_start),
    (@shop_id, @manual_conversation_id, @manual_session_id, 'MEITUAN_CALLBACK', 'MERCHANT',
     'SENT', DATE_ADD(@manual_start, INTERVAL 2 MINUTE), 'LOCAL_TEST_MANUAL_MERCHANT', 'imMsg',
     16, @op_biz_code, @epoi_id, UNIX_TIMESTAMP(DATE_ADD(@manual_start, INTERVAL 2 MINUTE)), 1,
     1, UNIX_TIMESTAMP(DATE_ADD(@manual_start, INTERVAL 2 MINUTE)), 9200000000102, 154, @epoi_id,
     1, 1, UNIX_TIMESTAMP(DATE_ADD(@manual_start, INTERVAL 2 MINUTE)), 990000002, @manual_open_user_id,
     -1, '人工回复测试', 2, '您好，已为您登记并由人工客服跟进。', SHA2('您好，已为您登记并由人工客服跟进。', 256),
     'SUCCESS', 'SUCCESS', DATE_ADD(@manual_start, INTERVAL 2 MINUTE),
     DATE_ADD(@manual_start, INTERVAL 2 MINUTE), DATE_ADD(@manual_start, INTERVAL 2 MINUTE));

SET @manual_last_message_id := (
    SELECT id FROM mt_im_message
    WHERE conversation_id = @manual_conversation_id
    ORDER BY event_time DESC, id DESC LIMIT 1
);

UPDATE mt_im_conversation
SET current_session_id = @manual_session_id,
    last_message_id = @manual_last_message_id,
    message_count = 2,
    user_message_count = 1,
    ai_message_count = 0,
    merchant_message_count = 1,
    session_count = 1,
    first_message_at = @manual_start,
    last_message_at = DATE_ADD(@manual_start, INTERVAL 2 MINUTE),
    last_user_message_at = @manual_start,
    last_ai_reply_at = NULL,
    version = 1,
    update_time = @now
WHERE id = @manual_conversation_id;

DROP TEMPORARY TABLE tmp_local_im_pairs;

-- 让默认本地超级管理员在总控台能直接看到测试门店；不存在该账号时不插入。
INSERT IGNORE INTO ai_user_shop_binding (user_id, shop_id, poi_id, create_time, update_time)
SELECT u.user_id, @shop_id, @poi_id, @now, @now
FROM ai_user u
JOIN ai_user_role ur ON ur.user_id = u.user_id AND ur.role_id = 'ROLE_SUPER'
WHERE u.status = 1;

COMMIT;

SELECT
    'LOCAL_TEST_SEED_READY' AS result,
    @shop_id AS shop_id,
    @epoi_id AS wm_poi_id,
    (SELECT COUNT(*) FROM mt_im_conversation WHERE shop_id = @shop_id) AS conversation_count,
    (SELECT COUNT(*) FROM mt_im_conversation_session cs
       JOIN mt_im_conversation c ON c.id = cs.conversation_id
      WHERE c.shop_id = @shop_id) AS session_count,
    (SELECT COUNT(*) FROM mt_im_message WHERE shop_id = @shop_id) AS message_count,
    (SELECT COUNT(*) FROM mt_im_auto_reply WHERE shop_id = @shop_id) AS auto_reply_count,
    (SELECT COUNT(*) FROM mt_knowledge_chunk WHERE shop_id = @shop_id AND enabled = 1 AND deleted = 0) AS knowledge_chunk_count;
