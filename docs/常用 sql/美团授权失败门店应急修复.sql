-- 美团授权失败门店应急修复
--
-- 默认处理方式：
--   不批量清理历史门店。等待下一次评价自动回复任务或人工重新授权。
--   自然命中 auth_status = 1 的门店，确认鉴权失败、刷新 token 重试仍失败后自动标记。
--
-- 仅当重复报错量过大、外呼成本或告警噪声不可接受时，才按明确 shop_id 清单执行本文件。
-- 禁止仅凭 mt_promotion_setting.promotion_enabled = 0 批量更新 auth_status。

-- 1. 核验待修复门店当前状态。
-- 将下面 shop_id 列表替换为从任务日志或人工确认得到的明确鉴权失败门店。
SELECT
    t.shop_id,
    t.auth_status,
    t.auth_status_changed_at,
    t.last_auth_failure_at,
    t.expires_at,
    s.promotion_enabled,
    s.update_time AS promotion_setting_update_time
FROM mt_access_token t
LEFT JOIN mt_promotion_setting s ON s.shop_id = t.shop_id
WHERE t.shop_id IN (
    -- 1001, 1002
)
ORDER BY t.shop_id;

-- 2. 应急标记为需重新授权。
-- 执行前必须确认第 1 步结果中的 shop_id 均有明确美团鉴权失败证据。
UPDATE mt_access_token
SET auth_status = 2,
    auth_status_changed_at = NOW(),
    last_auth_failure_at = NOW(),
    update_time = NOW()
WHERE shop_id IN (
    -- 1001, 1002
)
  AND auth_status = 1;

-- 3. 应急关闭仍开启的本地推广总开关。
-- 正常业务代码会通过 MtPromotionSettingService 记录系统变更历史。
-- 本 SQL 仅用于紧急止血；如需要完整历史，应优先写内部脚本调用服务方法。
UPDATE mt_promotion_setting
SET promotion_enabled = 0,
    update_time = NOW()
WHERE shop_id IN (
    -- 1001, 1002
)
  AND promotion_enabled = 1;
