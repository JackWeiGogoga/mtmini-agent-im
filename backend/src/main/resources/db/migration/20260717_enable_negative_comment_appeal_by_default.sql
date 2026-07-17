-- 新授权门店在尚未产生独立配置记录时，差评处理/申诉开关默认开启。
-- 仅调整新记录的数据库默认值，不覆盖商户已经保存的开关选择。

ALTER TABLE `mt_comment_reply_setting`
  MODIFY COLUMN `negative_comment_appeal_enabled` tinyint(1) NOT NULL DEFAULT '1'
  COMMENT '差评处理/申诉开关 1开启 0关闭';

ALTER TABLE `mt_comment_reply_setting_history`
  MODIFY COLUMN `negative_comment_appeal_enabled` tinyint(1) NOT NULL DEFAULT '1'
  COMMENT '差评处理/申诉开关快照';
