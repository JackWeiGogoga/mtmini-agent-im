INSERT INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`,
  `order_num`, `visible`, `status`, `create_time`, `update_time`
)
SELECT
  'MENU_POWER_MANAGE', '0', '算力管理', '/power-management', 'pages/power-manage/PowerManage',
  'shop:power-management:list', 'DatabaseOutlined', 'C', 6, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_POWER_MANAGE'
);
