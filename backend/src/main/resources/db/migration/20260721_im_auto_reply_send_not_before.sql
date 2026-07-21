-- 增量：IM 自动回复新增「最早可发送时间」，用于 3-10 秒随机发送延迟。
-- 该值必须落库：任务因等待被重排后会重新执行，若每次重新随机则永远到不了发送时刻。
-- 幂等：先查 information_schema，已存在则跳过。

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `mt_im_auto_reply`
       ADD COLUMN `send_not_before` datetime DEFAULT NULL
       COMMENT ''最早可发送时间，3-10秒随机延迟'' AFTER `generated_at`',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'mt_im_auto_reply'
    AND column_name = 'send_not_before'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
