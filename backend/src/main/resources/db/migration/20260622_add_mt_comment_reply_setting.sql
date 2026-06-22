-- 门店评价自动回复配置独立表
-- 适用于从旧版本 mt_shop 开关字段迁移到独立配置表/历史表。

CREATE TABLE IF NOT EXISTS `mt_comment_reply_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '店铺ID',
  `auto_reply_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'AI评价自动回复主开关 1开启 0关闭',
  `negative_comment_appeal_enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '差评处理/申诉开关 1开启 0关闭',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_comment_reply_setting_shop_id` (`shop_id`),
  KEY `idx_mt_comment_reply_setting_auto_reply` (`auto_reply_enabled`,`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店评价自动回复配置表';

CREATE TABLE IF NOT EXISTS `mt_comment_reply_setting_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `setting_id` bigint DEFAULT NULL COMMENT '配置ID',
  `shop_id` bigint NOT NULL COMMENT '店铺ID',
  `version_no` int NOT NULL COMMENT '配置版本号',
  `auto_reply_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'AI评价自动回复主开关快照',
  `negative_comment_appeal_enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '差评处理/申诉开关快照',
  `change_source` varchar(32) NOT NULL COMMENT '变更来源 H5/ADMIN/SYSTEM',
  `operator_user_id` varchar(64) DEFAULT NULL COMMENT '操作人用户ID',
  `operator_name` varchar(128) DEFAULT NULL COMMENT '操作人名称',
  `change_reason` varchar(255) DEFAULT NULL COMMENT '变更原因',
  `before_snapshot_json` longtext COMMENT '变更前配置快照JSON',
  `after_snapshot_json` longtext COMMENT '变更后配置快照JSON',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_comment_reply_setting_history_shop_version` (`shop_id`,`version_no`),
  KEY `idx_mt_comment_reply_setting_history_shop_time` (`shop_id`,`create_time`),
  KEY `idx_mt_comment_reply_setting_history_setting_id` (`setting_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店评价自动回复配置历史表';

