-- 群发配置变更历史。
-- mt_im_broadcast_setting 是单店一行、保存即原地覆盖的表，删除还会清空字段，
-- 因此改过什么、删过什么在业务表里完全无痕；已触发的批次虽然冻结了文案快照，
-- 但配了又在触发前删掉的配置连批次都不会产生。这张表用于补齐审计链路。
CREATE TABLE IF NOT EXISTS `mt_im_broadcast_setting_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `setting_id` bigint NOT NULL COMMENT '配置ID',
  `shop_id` bigint NOT NULL COMMENT '门店ID',
  `version_no` int NOT NULL COMMENT '版本号，按门店自增',
  `change_type` varchar(32) NOT NULL COMMENT 'SAVE=保存配置；DELETE=清空配置',
  `change_source` varchar(32) NOT NULL COMMENT 'H5/ADMIN/SYSTEM',
  `enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '变更后是否启用',
  `week_days` varchar(32) DEFAULT NULL COMMENT '变更后触发星期，1=周一 … 7=周日',
  `repeat_mode` varchar(16) DEFAULT NULL COMMENT '变更后频率 EVERY/ONCE',
  `send_time` time DEFAULT NULL COMMENT '变更后触发时间',
  `content` varchar(512) DEFAULT NULL COMMENT '变更后群发文案全文',
  `operator_user_id` varchar(64) DEFAULT NULL COMMENT '操作用户ID',
  `operator_name` varchar(128) DEFAULT NULL COMMENT '操作用户名称',
  `before_snapshot_json` longtext COMMENT '变更前快照',
  `after_snapshot_json` longtext COMMENT '变更后快照',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mt_im_broadcast_history_shop_version` (`shop_id`,`version_no`),
  KEY `idx_mt_im_broadcast_history_shop_time` (`shop_id`,`create_time`),
  KEY `idx_mt_im_broadcast_history_setting` (`setting_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店IM群发配置历史表';
