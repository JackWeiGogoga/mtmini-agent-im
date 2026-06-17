INSERT INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`,
  `order_num`, `visible`, `status`, `create_time`, `update_time`
)
SELECT
  'MENU_MT_APP_ORDER', '0', '美团订单', '/mt-app-orders', 'pages/mt-app-order-manage/MtAppOrderManage',
  'shop:mt-app-order:list', 'ProfileOutlined', 'C', 7, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_MT_APP_ORDER'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_ADMIN', 'MENU_MT_APP_ORDER', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_ADMIN' AND `menu_id` = 'MENU_MT_APP_ORDER'
);
