CREATE TABLE IF NOT EXISTS `user_behavior_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_source` varchar(32) NOT NULL COMMENT '事件来源 H5/ADMIN_BACKEND',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型',
  `shop_id` bigint NOT NULL COMMENT '门店ID',
  `shop_name_snapshot` varchar(255) DEFAULT NULL COMMENT '商家名称快照',
  `shop_phone_snapshot` varchar(64) DEFAULT NULL COMMENT '门店商家手机号快照',
  `backend_user_id` varchar(64) DEFAULT NULL COMMENT '后台用户ID',
  `h5_epoi_id` varchar(128) DEFAULT NULL COMMENT 'H5门店态epoiId',
  `event_time` datetime NOT NULL COMMENT '事件发生时间',
  `context_json` text DEFAULT NULL COMMENT '事件上下文JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_behavior_event_time_shop` (`event_time`, `shop_id`),
  KEY `idx_user_behavior_event_shop_time` (`shop_id`, `event_time`),
  KEY `idx_user_behavior_event_source_type_time` (`event_source`, `event_type`, `event_time`),
  KEY `idx_user_behavior_event_backend_user` (`backend_user_id`, `event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户核心行为埋点事件明细';

INSERT INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`,
  `order_num`, `visible`, `status`, `create_time`, `update_time`
)
SELECT
  'MENU_USER_BEHAVIOR_ANALYTICS', '0', '行为统计', '/analytics/user-behavior', 'pages/user-behavior-analytics/UserBehaviorAnalytics',
  'analytics:user-behavior:list', 'LineChartOutlined', 'C', 11, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_USER_BEHAVIOR_ANALYTICS'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT role_id, menu_id, NOW()
FROM (
  SELECT 'ROLE_ADMIN' AS role_id, 'MENU_USER_BEHAVIOR_ANALYTICS' AS menu_id
  UNION ALL
  SELECT 'ROLE_SUPER', 'MENU_USER_BEHAVIOR_ANALYTICS'
  UNION ALL
  SELECT 'ROLE_SALES', 'MENU_USER_BEHAVIOR_ANALYTICS'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` arm
  WHERE arm.role_id = seed.role_id
    AND arm.menu_id = seed.menu_id
);

DELETE FROM `ai_role_menu`
WHERE `menu_id` = 'MENU_SALES_USER_BEHAVIOR_ANALYTICS';

DELETE FROM `ai_menu`
WHERE `menu_id` = 'MENU_SALES_USER_BEHAVIOR_ANALYTICS';
