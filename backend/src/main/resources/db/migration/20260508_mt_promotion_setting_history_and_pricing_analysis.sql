CREATE TABLE IF NOT EXISTS `mt_promotion_setting_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `setting_id` bigint DEFAULT NULL COMMENT '推广配置ID',
  `shop_id` bigint NOT NULL COMMENT '店铺ID',
  `version_no` int NOT NULL COMMENT '门店内递增版本号',
  `promotion_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否开启推广',
  `strategy_configured` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已配置策略',
  `strategy_config_json` longtext COMMENT '策略配置JSON',
  `strategy_config_hash` varchar(64) DEFAULT NULL COMMENT '策略配置Hash',
  `change_source` varchar(64) NOT NULL COMMENT '变更来源 H5/ADMIN/SYSTEM',
  `operator_user_id` varchar(64) DEFAULT NULL COMMENT '操作人用户ID',
  `operator_name` varchar(128) DEFAULT NULL COMMENT '操作人名称',
  `change_reason` varchar(255) DEFAULT NULL COMMENT '变更原因',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_promotion_setting_history_shop_version` (`shop_id`, `version_no`),
  KEY `idx_mt_promotion_setting_history_shop_time` (`shop_id`, `create_time`),
  KEY `idx_mt_promotion_setting_history_setting_id` (`setting_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店推广配置历史表';

INSERT INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`,
  `order_num`, `visible`, `status`, `create_time`, `update_time`
)
SELECT
  'MENU_PRICING_ANALYSIS', '0', '定价分析', '/pricing-analysis', 'pages/pricing-analysis/PricingAnalysis',
  'system:pricing-analysis:list', 'LineChartOutlined', 'C', 10, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_menu` WHERE `menu_id` = 'MENU_PRICING_ANALYSIS'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_ADMIN', 'MENU_PRICING_ANALYSIS', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_ADMIN' AND `menu_id` = 'MENU_PRICING_ANALYSIS'
);

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_SUPER', 'MENU_PRICING_ANALYSIS', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `ai_role_menu` WHERE `role_id` = 'ROLE_SUPER' AND `menu_id` = 'MENU_PRICING_ANALYSIS'
);
