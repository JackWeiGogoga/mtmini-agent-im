-- 增量：IM 定时消息群发（门店配置 / 触发批次 / 发送明细）
-- 幂等：可重复执行。

-- 1) 门店群发配置（一店一条）
CREATE TABLE IF NOT EXISTS `mt_im_broadcast_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '本地门店ID',
  `enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '定时群发是否启用',
  `week_days` varchar(32) NOT NULL COMMENT '触发星期，1-7逗号分隔，1=周一',
  `repeat_mode` varchar(16) NOT NULL COMMENT '触发频率 EVERY=每周重复/ONCE=仅一次',
  `send_time` time NOT NULL COMMENT '触发时间，精确到秒',
  `content` varchar(500) NOT NULL COMMENT '群发文案，最长100字',
  `total_sent_count` bigint NOT NULL DEFAULT '0' COMMENT '累计成功群发条数',
  `last_trigger_date` date DEFAULT NULL COMMENT '最近一次触发日期',
  `last_task_id` bigint DEFAULT NULL COMMENT '最近一次群发批次ID',
  `operator_user_id` varchar(64) DEFAULT NULL COMMENT '最近配置人用户ID',
  `operator_name` varchar(128) DEFAULT NULL COMMENT '最近配置人姓名',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mt_im_broadcast_setting_shop` (`shop_id`),
  KEY `idx_mt_im_broadcast_setting_enabled` (`enabled`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店IM定时群发配置表';

-- 2) 群发触发批次
CREATE TABLE IF NOT EXISTS `mt_im_broadcast_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '本地门店ID',
  `setting_id` bigint NOT NULL COMMENT '群发配置ID',
  `trigger_date` date NOT NULL COMMENT '触发日期',
  `plan_time` time NOT NULL COMMENT '计划触发时间',
  `content` varchar(500) NOT NULL COMMENT '触发时的文案快照',
  `week_days` varchar(32) NOT NULL COMMENT '触发时的星期快照',
  `repeat_mode` varchar(16) NOT NULL COMMENT '触发时的频率快照',
  `status` varchar(32) NOT NULL COMMENT 'PENDING/PLANNING/SENDING/SUCCESS/PARTIAL/FAILED/SKIPPED',
  `target_count` int NOT NULL DEFAULT '0' COMMENT '目标人数',
  `truncated_count` int NOT NULL DEFAULT '0' COMMENT '超出上限被截断的人数',
  `shard_count` int NOT NULL DEFAULT '0' COMMENT '分片总数',
  `success_count` int NOT NULL DEFAULT '0' COMMENT '发送成功条数',
  `fail_count` int NOT NULL DEFAULT '0' COMMENT '发送失败条数',
  `skip_reason` varchar(64) DEFAULT NULL COMMENT '整批跳过原因',
  `planned_at` datetime DEFAULT NULL COMMENT '目标人群物化完成时间',
  `started_at` datetime DEFAULT NULL COMMENT '开始发送时间',
  `finished_at` datetime DEFAULT NULL COMMENT '批次结束时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mt_im_broadcast_task_shop_plan` (`shop_id`,`trigger_date`,`plan_time`),
  KEY `idx_mt_im_broadcast_task_status` (`status`,`id`),
  KEY `idx_mt_im_broadcast_task_shop_time` (`shop_id`,`create_time`),
  KEY `idx_mt_im_broadcast_task_time` (`create_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM定时群发触发批次表';

-- 3) 群发发送明细（每个目标顾客一条）
CREATE TABLE IF NOT EXISTS `mt_im_broadcast_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint NOT NULL COMMENT '群发批次ID',
  `shop_id` bigint NOT NULL COMMENT '本地门店ID',
  `conversation_id` bigint NOT NULL COMMENT '长期会话ID',
  `open_user_id` bigint NOT NULL COMMENT '开放用户ID',
  `shard_no` int NOT NULL COMMENT '分片序号，从0开始',
  `outbound_message_id` bigint NOT NULL COMMENT '出站消息ID，重试复用保证幂等',
  `status` varchar(16) NOT NULL COMMENT 'PENDING/SENT/FAILED',
  `error_code` varchar(64) DEFAULT NULL COMMENT '发送失败错误码',
  `error_summary` varchar(500) DEFAULT NULL COMMENT '发送失败摘要',
  `sent_at` datetime DEFAULT NULL COMMENT '发送成功时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mt_im_broadcast_record_task_user` (`task_id`,`open_user_id`),
  KEY `idx_mt_im_broadcast_record_shard` (`task_id`,`shard_no`,`status`,`id`),
  KEY `idx_mt_im_broadcast_record_shop_time` (`shop_id`,`create_time`),
  KEY `idx_mt_im_broadcast_record_status` (`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM定时群发发送明细表';

-- 4) 会话表补覆盖索引：群发目标人群按 (shop_id, last_user_message_at, id) 游标扫描并直接取 open_user_id，避免回表。
--    刻意用 last_user_message_at 而非 last_message_at：群发与 AI 回复都会推高 last_message_at，
--    若以它为准，被群发过的顾客会永远停留在「近一个月活跃」窗口内，人群只增不减。
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mt_im_conversation'
    AND INDEX_NAME = 'idx_mt_im_conversation_shop_user_latest');
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE `mt_im_conversation` ADD KEY `idx_mt_im_conversation_shop_user_latest` (`shop_id`,`last_user_message_at`,`id`,`open_user_id`)',
  'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5) 管理后台「群发管理」菜单与角色授权
INSERT IGNORE INTO `ai_menu`
  (`id`,`menu_id`,`parent_id`,`menu_name`,`path`,`component`,`permission`,`icon`,`menu_type`,`order_num`,`visible`,`status`,`create_time`,`update_time`)
VALUES
  (58,'MENU_IM_BROADCAST_RECORD','0','群发管理','/im-broadcasts','pages/im-broadcasts/ImBroadcastManage','system:im-broadcast:list','NotificationOutlined','C',15,1,1,'2026-07-20 00:00:00','2026-07-20 00:00:00');

INSERT IGNORE INTO `ai_role_menu` (`id`,`role_id`,`menu_id`,`create_time`) VALUES
  (119,'ROLE_SUPER','MENU_IM_BROADCAST_RECORD','2026-07-20 00:00:00'),
  (120,'ROLE_ADMIN','MENU_IM_BROADCAST_RECORD','2026-07-20 00:00:00');
