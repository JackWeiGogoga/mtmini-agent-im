ALTER TABLE `mt_power_account`
    ADD COLUMN `recharge_balance` int NOT NULL DEFAULT 0 COMMENT '实充剩余算力' AFTER `balance`,
    ADD COLUMN `grant_balance` int NOT NULL DEFAULT 0 COMMENT '赠送剩余算力' AFTER `recharge_balance`,
    ADD COLUMN `total_grant` int NOT NULL DEFAULT 0 COMMENT '累计赠送算力' AFTER `total_recharge`;

UPDATE `mt_power_account`
SET `recharge_balance` = `balance`
WHERE `recharge_balance` = 0
  AND `grant_balance` = 0
  AND `balance` > 0;

CREATE TABLE IF NOT EXISTS `mt_power_consume_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `consume_id` bigint NOT NULL COMMENT '主消费记录ID',
  `consume_no` varchar(64) NOT NULL COMMENT '消费流水号',
  `shop_id` bigint NOT NULL COMMENT '店铺ID',
  `source_balance_type` varchar(32) NOT NULL COMMENT '扣减来源类型 GRANT/RECHARGE',
  `power_amount` int NOT NULL COMMENT '本来源扣减算力',
  `balance_before` int NOT NULL COMMENT '该来源扣减前余额',
  `balance_after` int NOT NULL COMMENT '该来源扣减后余额',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_mt_power_consume_detail_consume_id` (`consume_id`),
  KEY `idx_mt_power_consume_detail_consume_no` (`consume_no`),
  KEY `idx_mt_power_consume_detail_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店算力消费来源明细表';

CREATE TABLE IF NOT EXISTS `mt_power_grant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `grant_no` varchar(64) NOT NULL COMMENT '后台发放单号',
  `shop_id` bigint NOT NULL COMMENT '店铺ID',
  `power_amount` int NOT NULL COMMENT '发放算力数量',
  `grant_reason` varchar(255) NOT NULL COMMENT '发放原因',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `operator_user_id` varchar(64) NOT NULL COMMENT '操作人用户ID',
  `operator_username` varchar(128) DEFAULT NULL COMMENT '操作人名称',
  `status` varchar(32) NOT NULL COMMENT '状态',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_power_grant_no` (`grant_no`),
  KEY `idx_mt_power_grant_shop_id` (`shop_id`),
  KEY `idx_mt_power_grant_operator` (`operator_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店算力后台发放表';

INSERT INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`,
  `order_num`, `visible`, `status`, `create_time`, `update_time`
)
SELECT
  'MENU_SHOP_POWER_GRANT_LIST', 'MENU_SHOP_MANAGE', '查看算力发放', NULL, NULL, 'shop:power-grant:list', NULL, 'F',
  2, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_SHOP_POWER_GRANT_LIST'
);

INSERT INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`,
  `order_num`, `visible`, `status`, `create_time`, `update_time`
)
SELECT
  'MENU_SHOP_POWER_GRANT_ADD', 'MENU_SHOP_MANAGE', '发放算力', NULL, NULL, 'shop:power-grant:add', NULL, 'F',
  3, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_SHOP_POWER_GRANT_ADD'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_ADMIN', 'MENU_SHOP_POWER_GRANT_LIST', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_ADMIN' AND `menu_id` = 'MENU_SHOP_POWER_GRANT_LIST'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_ADMIN', 'MENU_SHOP_POWER_GRANT_ADD', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_ADMIN' AND `menu_id` = 'MENU_SHOP_POWER_GRANT_ADD'
);
