-- 增量：门店自定义联系方式，用于在自动回复结尾固定拼接「可以联系门店 {联系方式}」。
-- 不改写 mt_shop.contact_phone / mt_shop.phone：那两个字段来自美团同步与商家资料，
-- 会被同步覆盖，也被订单、绑定等其它业务读取；这里只存商家为 IM 回复自定义的号码。
-- 落在 mt_im_auto_reply_setting：门店维度唯一、已有 H5/ADMIN 变更历史快照可复用。
-- 幂等：先查 information_schema，已存在则跳过。

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `mt_im_auto_reply_setting`
       ADD COLUMN `custom_contact_phone` varchar(64) DEFAULT NULL
       COMMENT ''商家自定义联系方式，优先于门店电话拼接在自动回复结尾'' AFTER `auto_reply_enabled`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'mt_im_auto_reply_setting'
    AND column_name = 'custom_contact_phone'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
