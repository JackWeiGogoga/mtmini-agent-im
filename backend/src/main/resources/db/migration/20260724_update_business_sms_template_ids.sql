-- 腾讯云业务短信模板版本升级。
-- 仅刷新尚未展开的待发送事件；已完成、已取消和已展开事件保留原模板审计快照。
UPDATE `sms_notification_event`
SET `template_id` = '2693564',
    `update_time` = NOW()
WHERE `scene` = 'POWER_EXHAUSTED'
  AND `status` = 'PENDING'
  AND `template_id` = '2687571';

UPDATE `sms_notification_event`
SET `template_id` = '2693563',
    `update_time` = NOW()
WHERE `scene` = 'APP_EXPIRED'
  AND `status` = 'PENDING'
  AND `template_id` = '2687569';
