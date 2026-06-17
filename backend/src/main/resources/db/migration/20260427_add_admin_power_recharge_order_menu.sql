INSERT INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`,
  `order_num`, `visible`, `status`, `create_time`, `update_time`
)
SELECT
  'MENU_POWER_RECHARGE_ORDER', '0', '充值订单', '/power-recharge-orders', 'pages/power-recharge-order-manage/PowerRechargeOrderManage',
  'system:power-recharge-order:list', 'CreditCardOutlined', 'C', 9, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_POWER_RECHARGE_ORDER'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_SUPER', 'MENU_POWER_RECHARGE_ORDER', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_SUPER' AND `menu_id` = 'MENU_POWER_RECHARGE_ORDER'
);
