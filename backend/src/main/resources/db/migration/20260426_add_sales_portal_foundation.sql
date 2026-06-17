CREATE TABLE IF NOT EXISTS `ai_sales_user_shop_binding` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` VARCHAR(64) NOT NULL,
  `shop_id` BIGINT NOT NULL,
  `poi_id` VARCHAR(64) NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sales_user_shop_binding_poi_id` (`poi_id`),
  UNIQUE KEY `uk_sales_user_shop_binding_user_shop` (`user_id`, `shop_id`),
  KEY `idx_sales_user_shop_binding_user_id` (`user_id`),
  KEY `idx_sales_user_shop_binding_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `ai_role` (`role_id`, `role_code`, `role_name`, `description`, `order_num`, `status`, `create_time`, `update_time`)
SELECT 'ROLE_SALES', 'sales_user', '网销用户', '网销推广与提成查看权限', 3, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role` WHERE `role_id` = 'ROLE_SALES'
);

INSERT INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`,
  `order_num`, `visible`, `status`, `create_time`, `update_time`
)
SELECT
  'MENU_SALES_MASTER_CONSOLE', '0', '总控台', '/master-console', 'pages/master-console/MasterConsole',
  'sales:master-console:list', 'DashboardOutlined', 'C', 4, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_SALES_MASTER_CONSOLE'
);

INSERT INTO `ai_menu` (`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`)
SELECT 'MENU_SALES_MASTER_CONSOLE_VIEW', 'MENU_SALES_MASTER_CONSOLE', '查看推广配置', NULL, NULL, 'sales:master-console:view', NULL, 'F', 0, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_SALES_MASTER_CONSOLE_VIEW'
);

INSERT INTO `ai_menu` (`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`)
SELECT 'MENU_SALES_MASTER_CONSOLE_LOG', 'MENU_SALES_MASTER_CONSOLE', '查看推广日志', NULL, NULL, 'sales:master-console:log', NULL, 'F', 1, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_SALES_MASTER_CONSOLE_LOG'
);

INSERT INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`,
  `order_num`, `visible`, `status`, `create_time`, `update_time`
)
SELECT
  'MENU_SALES_SHOP_MANAGE', '0', '门店管理', '/shops', 'pages/shop-manage/ShopManage',
  'sales:binding:list', 'ShopOutlined', 'C', 5, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_SALES_SHOP_MANAGE'
);

INSERT INTO `ai_menu` (`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`)
SELECT 'MENU_SALES_SHOP_BIND', 'MENU_SALES_SHOP_MANAGE', '绑定门店', NULL, NULL, 'sales:binding:add', NULL, 'F', 0, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_SALES_SHOP_BIND'
);

INSERT INTO `ai_menu` (`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`)
SELECT 'MENU_SALES_SHOP_UNBIND', 'MENU_SALES_SHOP_MANAGE', '解绑门店', NULL, NULL, 'sales:binding:delete', NULL, 'F', 1, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_SALES_SHOP_UNBIND'
);

INSERT INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`,
  `order_num`, `visible`, `status`, `create_time`, `update_time`
)
SELECT
  'MENU_SALES_CONFIG', '0', '网销配置', '/sales/config', 'pages/sales-config/SalesConfig',
  'sales:self-config:view', 'SettingOutlined', 'C', 6, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_SALES_CONFIG'
);

INSERT INTO `ai_menu` (`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`)
SELECT 'MENU_SALES_CONFIG_UPLOAD', 'MENU_SALES_CONFIG', '上传收款码', NULL, NULL, 'sales:self-config:upload-receipt', NULL, 'F', 0, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_SALES_CONFIG_UPLOAD'
);

INSERT INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`,
  `order_num`, `visible`, `status`, `create_time`, `update_time`
)
SELECT
  'MENU_SALES_COMMISSION', '0', '提成明细', '/sales/commissions', 'pages/sales-commission-manage/SalesCommissionManage',
  'sales:commission:list', 'ProfileOutlined', 'C', 7, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_SALES_COMMISSION'
);

INSERT INTO `ai_menu` (`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`)
SELECT 'MENU_SALES_COMMISSION_RATIO_EDIT', 'MENU_SALES_COMMISSION', '修改佣金比例', NULL, NULL, 'sales:commission:ratio-edit', NULL, 'F', 0, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_SALES_COMMISSION_RATIO_EDIT'
);

INSERT INTO `ai_menu` (`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`)
SELECT 'MENU_SALES_COMMISSION_SETTLE', 'MENU_SALES_COMMISSION', '修改结算状态', NULL, NULL, 'sales:commission:settle', NULL, 'F', 1, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_SALES_COMMISSION_SETTLE'
);

INSERT INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`,
  `order_num`, `visible`, `status`, `create_time`, `update_time`
)
SELECT
  'MENU_SALES_REDEMPTION_CODES', '0', '兑换码', '/sales/redemption-codes', 'pages/sales-redemption-code-manage/SalesRedemptionCodeManage',
  'sales:redemption-code:list', 'AppstoreOutlined', 'C', 8, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_SALES_REDEMPTION_CODES'
);

INSERT INTO `ai_menu` (`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`)
SELECT 'MENU_SALES_REDEMPTION_CODES_CREATE', 'MENU_SALES_REDEMPTION_CODES', '生成兑换码', NULL, NULL, 'sales:redemption-code:create', NULL, 'F', 0, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_SALES_REDEMPTION_CODES_CREATE'
);

INSERT INTO `ai_menu` (`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`)
SELECT 'MENU_SALES_REDEMPTION_CODES_RECEIPT_CLEAR', 'MENU_SALES_REDEMPTION_CODES', '删除收款码', NULL, NULL, 'sales:redemption-code:receipt-clear', NULL, 'F', 1, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_SALES_REDEMPTION_CODES_RECEIPT_CLEAR'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_SALES', 'MENU_SALES_MASTER_CONSOLE', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_SALES' AND `menu_id` = 'MENU_SALES_MASTER_CONSOLE'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_SALES', 'MENU_SALES_MASTER_CONSOLE_VIEW', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_SALES' AND `menu_id` = 'MENU_SALES_MASTER_CONSOLE_VIEW'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_SALES', 'MENU_SALES_MASTER_CONSOLE_LOG', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_SALES' AND `menu_id` = 'MENU_SALES_MASTER_CONSOLE_LOG'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_SALES', 'MENU_SALES_SHOP_MANAGE', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_SALES' AND `menu_id` = 'MENU_SALES_SHOP_MANAGE'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_SALES', 'MENU_SALES_SHOP_BIND', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_SALES' AND `menu_id` = 'MENU_SALES_SHOP_BIND'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_SALES', 'MENU_SALES_SHOP_UNBIND', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_SALES' AND `menu_id` = 'MENU_SALES_SHOP_UNBIND'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_SALES', 'MENU_SALES_CONFIG', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_SALES' AND `menu_id` = 'MENU_SALES_CONFIG'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_SALES', 'MENU_SALES_CONFIG_UPLOAD', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_SALES' AND `menu_id` = 'MENU_SALES_CONFIG_UPLOAD'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_SALES', 'MENU_SALES_COMMISSION', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_SALES' AND `menu_id` = 'MENU_SALES_COMMISSION'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_SUPER', 'MENU_SALES_COMMISSION', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_SUPER' AND `menu_id` = 'MENU_SALES_COMMISSION'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_SUPER', 'MENU_SALES_COMMISSION_RATIO_EDIT', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_SUPER' AND `menu_id` = 'MENU_SALES_COMMISSION_RATIO_EDIT'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_SUPER', 'MENU_SALES_COMMISSION_SETTLE', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_SUPER' AND `menu_id` = 'MENU_SALES_COMMISSION_SETTLE'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_SUPER', 'MENU_SALES_REDEMPTION_CODES', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_SUPER' AND `menu_id` = 'MENU_SALES_REDEMPTION_CODES'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_SUPER', 'MENU_SALES_REDEMPTION_CODES_CREATE', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_SUPER' AND `menu_id` = 'MENU_SALES_REDEMPTION_CODES_CREATE'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_SUPER', 'MENU_SALES_REDEMPTION_CODES_RECEIPT_CLEAR', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_SUPER' AND `menu_id` = 'MENU_SALES_REDEMPTION_CODES_RECEIPT_CLEAR'
);
