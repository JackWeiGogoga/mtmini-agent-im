CREATE TABLE IF NOT EXISTS `ai_sales_redemption_code` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(16) NOT NULL,
  `sales_user_id` VARCHAR(64) NOT NULL,
  `sales_user_phone` VARCHAR(20) NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `created_by_user_id` VARCHAR(64) NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sales_redemption_code_code` (`code`),
  UNIQUE KEY `uk_sales_redemption_code_user_id` (`sales_user_id`),
  KEY `idx_sales_redemption_code_phone` (`sales_user_phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ai_sales_redemption_use_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `recharge_id` BIGINT NOT NULL,
  `recharge_no` VARCHAR(64) NOT NULL,
  `shop_id` BIGINT NOT NULL,
  `poi_id` VARCHAR(64) NOT NULL,
  `shop_name` VARCHAR(255) DEFAULT NULL,
  `sales_user_id` VARCHAR(64) NOT NULL,
  `sales_user_phone` VARCHAR(20) NOT NULL,
  `redemption_code_id` BIGINT NOT NULL,
  `redemption_code` VARCHAR(16) NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sales_redemption_use_record_recharge_no` (`recharge_no`),
  KEY `idx_sales_redemption_use_record_shop_id` (`shop_id`),
  KEY `idx_sales_redemption_use_record_sales_user_id` (`sales_user_id`),
  KEY `idx_sales_redemption_use_record_code_id` (`redemption_code_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ai_sales_user_profile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` VARCHAR(64) NOT NULL,
  `receipt_code_url` VARCHAR(1024) DEFAULT NULL,
  `receipt_code_uploaded_at` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sales_user_profile_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ai_sales_commission_snapshot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `recharge_id` BIGINT NOT NULL,
  `recharge_no` VARCHAR(64) NOT NULL,
  `shop_id` BIGINT NOT NULL,
  `poi_id` VARCHAR(64) NOT NULL,
  `shop_name` VARCHAR(255) DEFAULT NULL,
  `shop_phone` VARCHAR(50) DEFAULT NULL,
  `power_amount` INT NOT NULL,
  `paid_amount_fen` INT NOT NULL,
  `commission_ratio` INT NOT NULL,
  `commission_amount_fen` INT NOT NULL,
  `sales_user_id` VARCHAR(64) NOT NULL,
  `sales_user_phone` VARCHAR(20) NOT NULL,
  `redemption_code` VARCHAR(16) NOT NULL,
  `settlement_status` VARCHAR(32) NOT NULL DEFAULT 'UNSETTLED',
  `settled_at` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sales_commission_snapshot_recharge_id` (`recharge_id`),
  UNIQUE KEY `uk_sales_commission_snapshot_recharge_no` (`recharge_no`),
  KEY `idx_sales_commission_snapshot_sales_user_id` (`sales_user_id`),
  KEY `idx_sales_commission_snapshot_shop_id` (`shop_id`),
  KEY `idx_sales_commission_snapshot_status` (`settlement_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `mt_power_recharge`
  ADD COLUMN `original_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '原价金额（分）' AFTER `unit_price_fen`,
  ADD COLUMN `discount_context_type` VARCHAR(64) DEFAULT NULL COMMENT '冻结折扣上下文类型' AFTER `original_amount_fen`,
  ADD COLUMN `discount_tag` VARCHAR(64) DEFAULT NULL COMMENT '冻结折扣标签' AFTER `discount_context_type`,
  ADD COLUMN `discount_rate_basis_points` INT NOT NULL DEFAULT 10000 COMMENT '冻结折扣比例，10000 表示原价' AFTER `discount_tag`,
  ADD COLUMN `redemption_code_id` BIGINT DEFAULT NULL COMMENT '冻结兑换码ID' AFTER `discount_rate_basis_points`,
  ADD COLUMN `redemption_code` VARCHAR(16) DEFAULT NULL COMMENT '冻结兑换码' AFTER `redemption_code_id`,
  ADD COLUMN `sales_user_id` VARCHAR(64) DEFAULT NULL COMMENT '冻结网销用户ID' AFTER `redemption_code`,
  ADD COLUMN `sales_user_phone` VARCHAR(20) DEFAULT NULL COMMENT '冻结网销用户手机号' AFTER `sales_user_id`,
  ADD COLUMN `commission_window_start_at` DATETIME DEFAULT NULL COMMENT '佣金默认比例窗口起始时间' AFTER `sales_user_phone`,
  ADD COLUMN `default_commission_ratio` INT DEFAULT NULL COMMENT '冻结默认佣金比例' AFTER `commission_window_start_at`;

CREATE INDEX `idx_mt_power_recharge_sales_user_id` ON `mt_power_recharge` (`sales_user_id`);
CREATE INDEX `idx_mt_power_recharge_redemption_code` ON `mt_power_recharge` (`redemption_code`);
