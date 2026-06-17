ALTER TABLE `ai_sales_user_profile`
  ADD COLUMN `receipt_code_storage_provider` VARCHAR(64) DEFAULT NULL COMMENT '收款码存储提供方' AFTER `receipt_code_url`,
  ADD COLUMN `receipt_code_object_key` VARCHAR(1024) DEFAULT NULL COMMENT '收款码对象键' AFTER `receipt_code_storage_provider`;

CREATE INDEX `idx_ai_sales_user_profile_receipt_code_object_key`
  ON `ai_sales_user_profile` (`receipt_code_object_key`(255));
