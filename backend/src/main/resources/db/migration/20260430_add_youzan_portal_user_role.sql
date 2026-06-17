INSERT INTO `ai_role` (`role_id`, `role_code`, `role_name`, `description`, `order_num`, `status`, `create_time`, `update_time`)
SELECT 'ROLE_YOUZAN_USER', 'youzan_user', '有赞用户', '有赞商户自助总控台与门店管理权限', 4, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role` WHERE `role_id` = 'ROLE_YOUZAN_USER'
);

INSERT INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`,
  `order_num`, `visible`, `status`, `create_time`, `update_time`
)
SELECT
  'MENU_YOUZAN_MASTER_CONSOLE', '0', '总控台', '/master-console', 'pages/master-console/MasterConsole',
  'youzan:master-console:list', 'DashboardOutlined', 'C', 4, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_YOUZAN_MASTER_CONSOLE'
);

INSERT INTO `ai_menu` (`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`)
SELECT 'MENU_YOUZAN_MASTER_CONSOLE_CONFIG', 'MENU_YOUZAN_MASTER_CONSOLE', '配置推广', NULL, NULL, 'youzan:master-console:config', NULL, 'F', 0, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_YOUZAN_MASTER_CONSOLE_CONFIG'
);

INSERT INTO `ai_menu` (`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`)
SELECT 'MENU_YOUZAN_MASTER_CONSOLE_LOG', 'MENU_YOUZAN_MASTER_CONSOLE', '查看推广日志', NULL, NULL, 'youzan:master-console:log', NULL, 'F', 1, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_YOUZAN_MASTER_CONSOLE_LOG'
);

INSERT INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`,
  `order_num`, `visible`, `status`, `create_time`, `update_time`
)
SELECT
  'MENU_YOUZAN_SHOP_MANAGE', '0', '门店管理', '/shops', 'pages/shop-manage/ShopManage',
  'youzan:binding:list', 'ShopOutlined', 'C', 5, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_YOUZAN_SHOP_MANAGE'
);

INSERT INTO `ai_menu` (`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`)
SELECT 'MENU_YOUZAN_SHOP_BIND', 'MENU_YOUZAN_SHOP_MANAGE', '绑定门店', NULL, NULL, 'youzan:binding:add', NULL, 'F', 0, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_YOUZAN_SHOP_BIND'
);

INSERT INTO `ai_menu` (`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`)
SELECT 'MENU_YOUZAN_SHOP_UNBIND', 'MENU_YOUZAN_SHOP_MANAGE', '解绑门店', NULL, NULL, 'youzan:binding:delete', NULL, 'F', 1, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_YOUZAN_SHOP_UNBIND'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_YOUZAN_USER', menu_id, NOW()
FROM `ai_menu` m
WHERE menu_id IN (
  'MENU_YOUZAN_MASTER_CONSOLE',
  'MENU_YOUZAN_MASTER_CONSOLE_CONFIG',
  'MENU_YOUZAN_MASTER_CONSOLE_LOG',
  'MENU_YOUZAN_SHOP_MANAGE',
  'MENU_YOUZAN_SHOP_BIND',
  'MENU_YOUZAN_SHOP_UNBIND'
)
AND NOT EXISTS (
  SELECT 1
  FROM `ai_role_menu` rm
  WHERE rm.`role_id` = 'ROLE_YOUZAN_USER'
    AND rm.`menu_id` = m.`menu_id`
);
