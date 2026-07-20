-- =====================================================
-- Full schema for mtmini-agent-reply
-- Generated from old online export full_schema.sql plus reply-project-only fallback tables.
-- Execute on an empty database. Contains table structure only, no business data.
-- =====================================================

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `menu_id` varchar(50) NOT NULL COMMENT '菜单ID',
  `parent_id` varchar(50) DEFAULT '0' COMMENT '父菜单ID',
  `menu_name` varchar(50) NOT NULL COMMENT '菜单名称',
  `path` varchar(200) DEFAULT NULL COMMENT '路由路径',
  `component` varchar(200) DEFAULT NULL COMMENT '组件路径',
  `permission` varchar(100) DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) DEFAULT NULL COMMENT '图标',
  `menu_type` char(1) DEFAULT 'C' COMMENT '类型 M:目录 C:菜单 F:按钮',
  `order_num` int DEFAULT '0' COMMENT '排序',
  `visible` tinyint DEFAULT '1' COMMENT '是否可见 0:隐藏 1:显示',
  `status` tinyint DEFAULT '1' COMMENT '状态 0:禁用 1:正常',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_menuid` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单和权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_prompt_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `prompt_key` varchar(100) NOT NULL COMMENT '提示词唯一键',
  `prompt_name` varchar(100) NOT NULL COMMENT '提示词名称',
  `prompt_type` varchar(32) NOT NULL COMMENT '提示词类型 SYSTEM/USER/VISION/ANALYSIS',
  `content` longtext NOT NULL COMMENT '提示词内容',
  `default_resource_path` varchar(255) DEFAULT NULL COMMENT '默认资源路径',
  `variables_json` longtext COMMENT '变量说明JSON',
  `model_hint` varchar(128) DEFAULT NULL COMMENT '建议模型或用途说明',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_system_prompt_config_key` (`prompt_key`),
  KEY `idx_system_prompt_config_enabled` (`enabled`,`prompt_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统提示词配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` varchar(50) NOT NULL COMMENT '角色ID',
  `role_code` varchar(50) NOT NULL COMMENT '角色编码',
  `role_name` varchar(50) NOT NULL COMMENT '角色名称',
  `description` varchar(255) DEFAULT NULL COMMENT '描述',
  `order_num` int DEFAULT '0' COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '状态 0:禁用 1:正常',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_roleid` (`role_id`),
  UNIQUE KEY `uniq_rolecode` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_role_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` varchar(50) NOT NULL COMMENT '角色ID',
  `menu_id` varchar(50) NOT NULL COMMENT '菜单ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_role_menu` (`role_id`,`menu_id`),
  KEY `idx_roleid` (`role_id`),
  KEY `idx_menuid` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色菜单关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_sales_commission_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `recharge_id` bigint NOT NULL,
  `recharge_no` varchar(64) NOT NULL,
  `shop_id` bigint NOT NULL,
  `poi_id` varchar(64) NOT NULL,
  `shop_name` varchar(255) DEFAULT NULL,
  `shop_phone` varchar(50) DEFAULT NULL,
  `power_amount` int NOT NULL,
  `paid_amount_fen` int NOT NULL,
  `commission_ratio` int NOT NULL,
  `commission_amount_fen` int NOT NULL,
  `sales_user_id` varchar(64) NOT NULL,
  `sales_user_phone` varchar(20) NOT NULL,
  `redemption_code` varchar(16) NOT NULL,
  `settlement_status` varchar(32) NOT NULL DEFAULT 'UNSETTLED',
  `settled_at` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sales_commission_snapshot_recharge_id` (`recharge_id`),
  UNIQUE KEY `uk_sales_commission_snapshot_recharge_no` (`recharge_no`),
  KEY `idx_sales_commission_snapshot_sales_user_id` (`sales_user_id`),
  KEY `idx_sales_commission_snapshot_shop_id` (`shop_id`),
  KEY `idx_sales_commission_snapshot_status` (`settlement_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_sales_redemption_code` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(16) NOT NULL,
  `sales_user_id` varchar(64) NOT NULL,
  `sales_user_phone` varchar(20) NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `created_by_user_id` varchar(64) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sales_redemption_code_code` (`code`),
  UNIQUE KEY `uk_sales_redemption_code_user_id` (`sales_user_id`),
  KEY `idx_sales_redemption_code_phone` (`sales_user_phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_sales_redemption_use_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `recharge_id` bigint NOT NULL,
  `recharge_no` varchar(64) NOT NULL,
  `shop_id` bigint NOT NULL,
  `poi_id` varchar(64) NOT NULL,
  `shop_name` varchar(255) DEFAULT NULL,
  `sales_user_id` varchar(64) NOT NULL,
  `sales_user_phone` varchar(20) NOT NULL,
  `redemption_code_id` bigint NOT NULL,
  `redemption_code` varchar(16) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sales_redemption_use_record_recharge_no` (`recharge_no`),
  KEY `idx_sales_redemption_use_record_shop_id` (`shop_id`),
  KEY `idx_sales_redemption_use_record_sales_user_id` (`sales_user_id`),
  KEY `idx_sales_redemption_use_record_code_id` (`redemption_code_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_sales_user_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(64) NOT NULL,
  `receipt_code_url` varchar(1024) DEFAULT NULL,
  `receipt_code_storage_provider` varchar(64) DEFAULT NULL COMMENT '收款码存储提供方',
  `receipt_code_object_key` varchar(1024) DEFAULT NULL COMMENT '收款码对象键',
  `receipt_code_uploaded_at` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sales_user_profile_user_id` (`user_id`),
  KEY `idx_ai_sales_user_profile_receipt_code_object_key` (`receipt_code_object_key`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_sales_user_shop_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(64) NOT NULL,
  `shop_id` bigint NOT NULL,
  `poi_id` varchar(64) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sales_user_shop_binding_poi_id` (`poi_id`),
  UNIQUE KEY `uk_sales_user_shop_binding_user_shop` (`user_id`,`shop_id`),
  KEY `idx_sales_user_shop_binding_user_id` (`user_id`),
  KEY `idx_sales_user_shop_binding_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `password` varchar(255) DEFAULT NULL COMMENT '密码(BCrypt加密)',
  `auth_provider` varchar(20) DEFAULT 'PASSWORD' COMMENT '认证方式: PASSWORD/SMS/GOOGLE/GITHUB/OPENID',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `status` tinyint DEFAULT '1' COMMENT '状态 0:禁用 1:正常',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_userid` (`user_id`),
  UNIQUE KEY `uniq_email` (`email`),
  UNIQUE KEY `uniq_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_user_auth` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `provider` varchar(20) NOT NULL COMMENT '认证提供者(GOOGLE/GITHUB/OPENID)',
  `provider_user_id` varchar(255) NOT NULL COMMENT '提供者侧用户唯一标识',
  `provider_username` varchar(255) DEFAULT NULL COMMENT '提供者侧用户名',
  `provider_email` varchar(255) DEFAULT NULL COMMENT '提供者侧邮箱',
  `provider_avatar` varchar(500) DEFAULT NULL COMMENT '提供者侧头像URL',
  `access_token` varchar(2000) DEFAULT NULL COMMENT 'OAuth访问令牌',
  `refresh_token` varchar(2000) DEFAULT NULL COMMENT 'OAuth刷新令牌',
  `token_expire_time` datetime DEFAULT NULL COMMENT '令牌过期时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_provider_user` (`provider`,`provider_user_id`),
  KEY `idx_user_provider` (`user_id`,`provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户第三方账号绑定表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_user_data_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `scope_type` varchar(20) NOT NULL COMMENT '数据范围 SELF/TEAM/ALL',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_ai_user_data_scope_user_id` (`user_id`),
  KEY `idx_ai_user_data_scope_scope_type` (`scope_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台用户数据范围表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_user_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `manager_user_id` varchar(50) NOT NULL COMMENT '上级用户ID',
  `relation_type` varchar(20) NOT NULL DEFAULT 'DIRECT' COMMENT '关系类型',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_ai_user_relation_user_id` (`user_id`),
  KEY `idx_ai_user_relation_manager_user_id` (`manager_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台用户上下级关系表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `role_id` varchar(50) NOT NULL COMMENT '角色ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_user_role` (`user_id`,`role_id`),
  KEY `idx_userid` (`user_id`),
  KEY `idx_roleid` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_user_shop_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(50) NOT NULL COMMENT '后台用户ID',
  `shop_id` bigint NOT NULL COMMENT '门店主键ID',
  `poi_id` varchar(64) NOT NULL COMMENT '美团门店poi_id',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_user_poi` (`user_id`,`poi_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台用户门店绑定表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mt_access_token` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `access_token` varchar(1000) DEFAULT NULL COMMENT '访问token',
  `shop_id` bigint DEFAULT NULL COMMENT '店铺ID',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `op_biz_code` varchar(255) DEFAULT NULL COMMENT '商家唯一标识',
  `refresh_token` varchar(1000) DEFAULT NULL COMMENT '刷新token',
  `expires_at` datetime DEFAULT NULL COMMENT 'access token过期时间',
  `auth_status` tinyint NOT NULL DEFAULT '1' COMMENT '授权状态 1有效 2需重新授权',
  `auth_status_changed_at` datetime DEFAULT NULL COMMENT '授权状态最近变更时间',
  `last_auth_failure_at` datetime DEFAULT NULL COMMENT '最近一次鉴权失败时间',
  `order_push_blocked` tinyint(1) DEFAULT NULL COMMENT '订单推送是否已停止 1已停止 0未停止 NULL待同步',
  `order_push_last_sync_at` datetime DEFAULT NULL COMMENT '订单推送最近一次同步时间',
  `order_push_last_sync_error` varchar(1000) DEFAULT NULL COMMENT '订单推送最近一次同步错误',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_access_token_op_biz_code` (`op_biz_code`),
  KEY `idx_mt_access_token_shop_id` (`shop_id`),
  KEY `idx_mt_access_token_expires_at` (`expires_at`),
  KEY `idx_mt_access_token_order_push` (`order_push_blocked`,`shop_id`,`expires_at`),
  KEY `idx_mt_access_token_auth_status` (`auth_status`,`shop_id`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='美团授权token表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mt_access_token_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint DEFAULT NULL COMMENT '店铺ID，授权核心落库前可能为空',
  `op_biz_code` varchar(255) DEFAULT NULL COMMENT '商家唯一标识',
  `event_type` varchar(32) NOT NULL COMMENT '事件类型 AUTH_CALLBACK/REFRESH',
  `event_status` varchar(32) NOT NULL COMMENT '事件状态 SUCCESS/FAILED',
  `access_token_cipher` text COMMENT '加密后的access token',
  `refresh_token_cipher` text COMMENT '加密后的refresh token',
  `access_token_fingerprint` varchar(64) DEFAULT NULL COMMENT 'access token指纹',
  `refresh_token_fingerprint` varchar(64) DEFAULT NULL COMMENT 'refresh token指纹',
  `expires_at` datetime DEFAULT NULL COMMENT 'access token过期时间',
  `response_code` int DEFAULT NULL COMMENT '美团响应码',
  `response_msg` varchar(1000) DEFAULT NULL COMMENT '美团响应消息',
  `failure_reason` varchar(1000) DEFAULT NULL COMMENT '本地处理失败原因',
  `trace_id` varchar(128) DEFAULT NULL COMMENT '链路追踪ID',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_mt_access_token_history_shop_time` (`shop_id`,`create_time`),
  KEY `idx_mt_access_token_history_op_biz_time` (`op_biz_code`,`create_time`),
  KEY `idx_mt_access_token_history_access_fp` (`access_token_fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='美团token历史表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mt_app_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` varchar(255) DEFAULT NULL COMMENT '美团订单号',
  `order_view_id` varchar(255) DEFAULT NULL COMMENT '美团商家后台显示订单号',
  `app_id` varchar(20) DEFAULT NULL COMMENT '应用ID',
  `service_code` varchar(255) DEFAULT NULL COMMENT '商家opbizcode',
  `pay_status` int DEFAULT NULL COMMENT '支付状态',
  `version` varchar(255) DEFAULT NULL COMMENT '版本号',
  `version_name` varchar(255) DEFAULT NULL COMMENT '版本名称',
  `days` int DEFAULT NULL COMMENT '本次购买服务时长',
  `start_time` bigint DEFAULT NULL COMMENT '服务开始时间',
  `end_time` bigint DEFAULT NULL COMMENT '服务结束时间',
  `right_status` varchar(255) DEFAULT NULL COMMENT '权益状态',
  `real_pay_price` int DEFAULT NULL COMMENT '实付金额，分',
  `order_time` bigint DEFAULT NULL COMMENT '下单时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `type` int DEFAULT NULL COMMENT '服务时长是否已累加 1已累加 0未累加',
  `address` varchar(255) DEFAULT NULL COMMENT '店铺地址',
  `shop_name` varchar(255) DEFAULT NULL COMMENT '店铺名',
  `phone` varchar(50) DEFAULT NULL COMMENT '店铺电话',
  `shop_id` bigint DEFAULT NULL COMMENT '店铺ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_app_order_order_id` (`order_id`),
  KEY `idx_mt_app_order_order_view_id` (`order_view_id`),
  KEY `idx_mt_app_order_service_code` (`service_code`),
  KEY `idx_mt_app_order_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='美团小应用订阅订单表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mt_async_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_type` varchar(64) NOT NULL COMMENT '任务类型',
  `biz_key` varchar(255) NOT NULL COMMENT '业务唯一键',
  `scope_key` varchar(128) DEFAULT NULL COMMENT '作用域键，例如shopId',
  `payload_json` longtext COMMENT '任务载荷JSON',
  `status` varchar(32) NOT NULL COMMENT '任务状态 PENDING/PROCESSING/SUCCESS/DEAD',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '当前重试次数',
  `max_retry_count` int NOT NULL DEFAULT '0' COMMENT '最大重试次数',
  `available_at` datetime NOT NULL COMMENT '下次可执行时间',
  `claimed_at` datetime DEFAULT NULL COMMENT '领取时间',
  `finished_at` datetime DEFAULT NULL COMMENT '完成时间',
  `worker_id` varchar(128) DEFAULT NULL COMMENT '执行worker标识',
  `last_error` varchar(1000) DEFAULT NULL COMMENT '最近一次错误摘要',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_async_task_type_biz` (`task_type`,`biz_key`),
  KEY `idx_mt_async_task_status_available` (`status`,`available_at`,`task_type`),
  KEY `idx_mt_async_task_status_claimed` (`status`,`claimed_at`),
  KEY `idx_mt_async_task_type_scope_status` (`task_type`,`scope_key`,`status`),
  KEY `idx_mt_async_task_status_finished` (`status`,`finished_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据库异步任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

CREATE TABLE `mt_knowledge_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '门店ID',
  `title` varchar(255) NOT NULL COMMENT '标题',
  `question` longtext COMMENT '问题',
  `answer` longtext NOT NULL COMMENT '回答',
  `category` varchar(32) NOT NULL DEFAULT 'GENERAL' COMMENT '知识类型 GENERAL/DELIVERY/AFTER_SALE/OTHER',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_mt_knowledge_item_shop` (`shop_id`,`deleted`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店知识条目表';

CREATE TABLE `mt_knowledge_document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '门店ID',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_type` varchar(16) NOT NULL COMMENT '文件类型 TXT/DOCX/XLSX',
  `file_size` bigint NOT NULL COMMENT '文件大小字节',
  `storage_provider` varchar(32) NOT NULL COMMENT '存储提供方',
  `object_key` varchar(500) NOT NULL COMMENT '对象存储Key',
  `parse_status` varchar(32) NOT NULL COMMENT 'PENDING/PARSING/SUCCESS/FAILED',
  `parse_error` varchar(1000) DEFAULT NULL COMMENT '安全错误摘要',
  `chunk_count` int NOT NULL DEFAULT '0' COMMENT '切片数',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_mt_knowledge_document_shop` (`shop_id`,`deleted`,`enabled`),
  KEY `idx_mt_knowledge_document_status` (`parse_status`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店知识文档表';

CREATE TABLE `mt_knowledge_chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '门店ID',
  `source_type` varchar(32) NOT NULL COMMENT 'QA/DOCUMENT',
  `source_id` bigint NOT NULL COMMENT '来源ID',
  `item_id` bigint DEFAULT NULL COMMENT '知识条目ID',
  `document_id` bigint DEFAULT NULL COMMENT '知识文档ID',
  `chunk_text` longtext NOT NULL COMMENT '切片文本',
  `category` varchar(32) NOT NULL DEFAULT 'GENERAL' COMMENT '知识类型 GENERAL/DELIVERY/AFTER_SALE/OTHER',
  `embedding_provider` varchar(64) DEFAULT NULL COMMENT '向量提供方',
  `embedding_model` varchar(128) DEFAULT NULL COMMENT '向量模型',
  `embedding_dim` int DEFAULT NULL COMMENT '向量维度',
  `embedding_blob` mediumblob COMMENT 'float32向量二进制',
  `token_count` int DEFAULT NULL COMMENT '估算token数',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_mt_knowledge_chunk_shop` (`shop_id`,`deleted`,`enabled`),
  KEY `idx_mt_knowledge_chunk_source` (`source_type`,`source_id`),
  KEY `idx_mt_knowledge_chunk_item` (`item_id`),
  KEY `idx_mt_knowledge_chunk_document` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店知识切片表';

CREATE TABLE `mt_im_auto_reply_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '门店ID',
  `auto_reply_enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '本地IM自动回复总开关',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mt_im_auto_reply_setting_shop` (`shop_id`),
  KEY `idx_mt_im_auto_reply_setting_enabled` (`auto_reply_enabled`,`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店IM自动回复设置表';

CREATE TABLE `mt_im_auto_reply_setting_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `setting_id` bigint NOT NULL COMMENT '设置ID',
  `shop_id` bigint NOT NULL COMMENT '门店ID',
  `version_no` int NOT NULL COMMENT '版本号',
  `auto_reply_enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '开关快照',
  `change_source` varchar(32) NOT NULL COMMENT 'AUTH/H5/ADMIN/SYSTEM',
  `operator_user_id` varchar(64) DEFAULT NULL COMMENT '操作用户ID',
  `operator_name` varchar(128) DEFAULT NULL COMMENT '操作用户名称',
  `change_reason` varchar(255) DEFAULT NULL COMMENT '变更原因',
  `before_snapshot_json` longtext COMMENT '变更前快照',
  `after_snapshot_json` longtext COMMENT '变更后快照',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mt_im_auto_reply_history_shop_version` (`shop_id`,`version_no`),
  KEY `idx_mt_im_auto_reply_history_shop_time` (`shop_id`,`create_time`),
  KEY `idx_mt_im_auto_reply_history_setting` (`setting_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店IM自动回复设置历史表';

CREATE TABLE `mt_im_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '本地门店ID',
  `open_user_id` bigint NOT NULL COMMENT '开放用户ID',
  `current_session_id` bigint DEFAULT NULL COMMENT '当前短期记忆Session ID',
  `last_message_id` bigint DEFAULT NULL COMMENT '最后一条统一消息ID',
  `message_count` int NOT NULL DEFAULT '0' COMMENT '全部消息数',
  `user_message_count` int NOT NULL DEFAULT '0' COMMENT '用户消息数',
  `ai_message_count` int NOT NULL DEFAULT '0' COMMENT 'AI自动回复消息数',
  `merchant_message_count` int NOT NULL DEFAULT '0' COMMENT '商家人工消息数',
  `session_count` int NOT NULL DEFAULT '0' COMMENT '历史Session数',
  `first_message_at` datetime DEFAULT NULL COMMENT '首条消息时间',
  `last_message_at` datetime DEFAULT NULL COMMENT '最后消息时间',
  `last_user_message_at` datetime DEFAULT NULL COMMENT '最后用户消息时间',
  `last_ai_reply_at` datetime DEFAULT NULL COMMENT '最后AI回复时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mt_im_conversation_shop_user` (`shop_id`,`open_user_id`),
  KEY `idx_mt_im_conversation_latest` (`last_message_at`,`id`),
  KEY `idx_mt_im_conversation_shop_latest` (`shop_id`,`last_message_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店与顾客的长期IM会话流';

CREATE TABLE `mt_im_conversation_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` bigint NOT NULL COMMENT '长期会话ID',
  `session_no` int NOT NULL COMMENT '会话内Session序号',
  `status` varchar(32) NOT NULL COMMENT 'ACTIVE/CLOSED',
  `boundary_reason` varchar(32) NOT NULL COMMENT 'INITIAL/IDLE_TIMEOUT',
  `started_at` datetime NOT NULL COMMENT 'Session开始时间',
  `last_active_at` datetime NOT NULL COMMENT 'Session最后活动时间',
  `closed_at` datetime DEFAULT NULL COMMENT 'Session关闭时间',
  `message_count` int NOT NULL DEFAULT '0' COMMENT '全部消息数',
  `user_message_count` int NOT NULL DEFAULT '0' COMMENT '用户消息数',
  `ai_message_count` int NOT NULL DEFAULT '0' COMMENT 'AI消息数',
  `merchant_message_count` int NOT NULL DEFAULT '0' COMMENT '商家人工消息数',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mt_im_session_conversation_no` (`conversation_id`,`session_no`),
  KEY `idx_mt_im_session_conversation_status` (`conversation_id`,`status`,`last_active_at`),
  KEY `idx_mt_im_session_conversation_latest` (`conversation_id`,`last_active_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM会话短期记忆Session';

CREATE TABLE `mt_im_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint DEFAULT NULL COMMENT '本地门店ID',
  `conversation_id` bigint DEFAULT NULL COMMENT '长期会话ID',
  `session_id` bigint DEFAULT NULL COMMENT '短期记忆Session ID',
  `record_source` varchar(32) NOT NULL DEFAULT 'MEITUAN_CALLBACK' COMMENT 'MEITUAN_CALLBACK/AI_AUTO_REPLY',
  `sender_role` varchar(32) DEFAULT NULL COMMENT 'USER/MERCHANT/AI',
  `reply_to_message_id` bigint DEFAULT NULL COMMENT '回复的用户消息ID',
  `auto_reply_id` bigint DEFAULT NULL COMMENT '自动回复编排记录ID',
  `delivery_status` varchar(32) NOT NULL DEFAULT 'RECEIVED' COMMENT 'RECEIVED/GENERATED/SENDING/SENT/FAILED',
  `event_time` datetime NOT NULL COMMENT '统一消息事件时间',
  `callback_msg_id` varchar(128) DEFAULT NULL COMMENT '外层回调消息ID，内部AI消息为空',
  `callback_msg_type` varchar(64) DEFAULT NULL COMMENT '外层消息类型',
  `developer_id` bigint DEFAULT NULL COMMENT '开发者ID',
  `business_id` int DEFAULT NULL COMMENT '业务ID',
  `op_biz_code` varchar(128) DEFAULT NULL COMMENT '业务编码',
  `epoi_id` varchar(128) DEFAULT NULL COMMENT '外层门店标识',
  `callback_timestamp` bigint DEFAULT NULL COMMENT '外层原始时间戳',
  `sign_verified` tinyint(1) NOT NULL DEFAULT '0' COMMENT '外层验签结果',
  `biz_type` int DEFAULT NULL COMMENT '业务消息类型',
  `extended_field` longtext COMMENT '扩展字段',
  `inner_sig` varchar(128) DEFAULT NULL COMMENT '业务消息内层sig，仅留存',
  `inner_timestamp` bigint DEFAULT NULL COMMENT '业务消息内层时间戳',
  `platform_msg_id` bigint DEFAULT NULL COMMENT '美团IM消息ID',
  `app_id` bigint DEFAULT NULL COMMENT '应用ID',
  `app_poi_code` varchar(128) DEFAULT NULL COMMENT '内部门店标识',
  `msg_type` int DEFAULT NULL COMMENT '消息内容类型',
  `msg_source` int DEFAULT NULL COMMENT '消息来源',
  `message_cts` bigint DEFAULT NULL COMMENT '消息10位时间戳',
  `order_id` bigint DEFAULT NULL COMMENT '订单ID',
  `open_user_id` bigint DEFAULT NULL COMMENT '开放用户ID',
  `scene_tag` int DEFAULT NULL COMMENT '场景标签',
  `scene_name` varchar(255) DEFAULT NULL COMMENT '场景名称',
  `has_reply` int DEFAULT NULL COMMENT '回复标记',
  `msg_content_ciphertext` longtext COMMENT '消息原始密文',
  `msg_content_plaintext` longtext COMMENT '可用消息明文',
  `msg_content_hash` char(64) DEFAULT NULL COMMENT '消息内容SHA-256',
  `parse_status` varchar(32) NOT NULL COMMENT 'SUCCESS/SHOP_UNMATCHED/PARSE_FAILED',
  `decrypt_status` varchar(32) NOT NULL COMMENT 'SUCCESS/NOT_APPLICABLE/FAILED',
  `error_code` varchar(128) DEFAULT NULL COMMENT '安全错误码',
  `error_summary` varchar(1000) DEFAULT NULL COMMENT '安全错误摘要',
  `raw_message_json` longtext COMMENT '原始业务消息JSON',
  `raw_push_content_json` longtext COMMENT '原始push_content JSON',
  `received_at` datetime NOT NULL COMMENT '接收时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mt_im_message_callback` (`callback_msg_id`),
  UNIQUE KEY `uk_mt_im_message_platform` (`app_id`,`platform_msg_id`),
  UNIQUE KEY `uk_mt_im_message_auto_reply` (`auto_reply_id`),
  KEY `idx_mt_im_message_conversation_cursor` (`conversation_id`,`event_time`,`id`),
  KEY `idx_mt_im_message_session_cursor` (`session_id`,`event_time`,`id`),
  KEY `idx_mt_im_message_shop_cursor` (`shop_id`,`event_time`,`id`),
  KEY `idx_mt_im_message_shop_role_cursor` (`shop_id`,`sender_role`,`event_time`,`id`),
  KEY `idx_mt_im_message_open_user` (`shop_id`,`open_user_id`,`event_time`,`id`),
  KEY `idx_mt_im_message_reply_candidate` (`shop_id`,`msg_source`,`has_reply`,`message_cts`),
  KEY `idx_mt_im_message_order` (`order_id`),
  KEY `idx_mt_im_message_received` (`received_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM会话统一消息时间线';

CREATE TABLE `mt_im_auto_reply` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '本地门店ID',
  `conversation_id` bigint NOT NULL COMMENT '长期会话ID',
  `session_id` bigint NOT NULL COMMENT '短期记忆Session ID',
  `trigger_message_id` bigint NOT NULL COMMENT '触发自动回复的用户消息ID',
  `reply_message_id` bigint DEFAULT NULL COMMENT '生成的AI统一消息ID',
  `outbound_message_id` bigint NOT NULL COMMENT '稳定的美团出站消息ID',
  `status` varchar(32) NOT NULL COMMENT 'PENDING/GENERATING/GENERATED/BILLED/SENDING/SENT/SUPERSEDED/SKIPPED/DEAD',
  `skip_reason` varchar(64) DEFAULT NULL COMMENT '跳过或淘汰原因',
  `user_message_category` varchar(32) DEFAULT NULL COMMENT '用户消息分类 GENERAL/DELIVERY/AFTER_SALE',
  `rag_used` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否使用RAG上下文',
  `rag_chunk_count` int NOT NULL DEFAULT '0' COMMENT '采用知识片段数',
  `rag_max_score` decimal(8,6) DEFAULT NULL COMMENT '最高知识相似度',
  `model_provider` varchar(64) DEFAULT NULL COMMENT 'Chat模型供应商',
  `model_name` varchar(128) DEFAULT NULL COMMENT 'Chat模型名称',
  `prompt_tokens` int DEFAULT NULL COMMENT '输入token数',
  `completion_tokens` int DEFAULT NULL COMMENT '输出token数',
  `power_amount` int DEFAULT NULL COMMENT '实际扣减算力',
  `rewrite_count` int NOT NULL DEFAULT '0' COMMENT '敏感词改写次数',
  `generated_at` datetime DEFAULT NULL COMMENT '生成完成时间',
  `billed_at` datetime DEFAULT NULL COMMENT '算力扣减完成时间',
  `sent_at` datetime DEFAULT NULL COMMENT '发送成功时间',
  `last_error_code` varchar(128) DEFAULT NULL COMMENT '最近错误码',
  `last_error_summary` varchar(1000) DEFAULT NULL COMMENT '安全错误摘要',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mt_im_auto_reply_trigger` (`trigger_message_id`),
  UNIQUE KEY `uk_mt_im_auto_reply_reply` (`reply_message_id`),
  UNIQUE KEY `uk_mt_im_auto_reply_outbound` (`outbound_message_id`),
  KEY `idx_mt_im_auto_reply_shop_time` (`shop_id`,`create_time`,`id`),
  KEY `idx_mt_im_auto_reply_conversation_time` (`conversation_id`,`create_time`,`id`),
  KEY `idx_mt_im_auto_reply_status_time` (`status`,`create_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM自动回复编排与计费记录';

CREATE TABLE `mt_im_test_chat_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '本地门店ID',
  `session_id` varchar(64) DEFAULT NULL COMMENT '测试会话ID（前端每轮对话生成，用于按会话分组）',
  `operator_user_id` varchar(64) DEFAULT NULL COMMENT '发起测试的管理员用户ID',
  `operator_name` varchar(128) DEFAULT NULL COMMENT '发起测试的管理员姓名',
  `user_message` text COMMENT '测试用户输入文本',
  `ai_reply` text COMMENT 'AI回复文本',
  `user_message_category` varchar(32) DEFAULT NULL COMMENT '用户消息分类 GENERAL/DELIVERY/AFTER_SALE',
  `rag_used` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否使用RAG上下文',
  `rag_chunk_count` int NOT NULL DEFAULT '0' COMMENT '采用知识片段数',
  `rag_max_score` decimal(8,6) DEFAULT NULL COMMENT '最高知识相似度',
  `rewrite_count` int NOT NULL DEFAULT '0' COMMENT '敏感词改写次数',
  `blocked` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否被敏感词兜底拦截',
  `model_provider` varchar(64) DEFAULT NULL COMMENT 'Chat模型供应商',
  `model_name` varchar(128) DEFAULT NULL COMMENT 'Chat模型名称',
  `prompt_tokens` int DEFAULT NULL COMMENT '输入token数',
  `completion_tokens` int DEFAULT NULL COMMENT '输出token数',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_mt_im_test_chat_log_shop_time` (`shop_id`,`create_time`),
  KEY `idx_mt_im_test_chat_log_shop_session` (`shop_id`,`session_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IM知识库测试聊天审计与限流记录';

CREATE TABLE `mt_h5_guide_state` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '店铺ID',
  `guide_code` varchar(64) NOT NULL COMMENT 'H5引导编码',
  `completed` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已完成',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_h5_guide_state_shop_guide` (`shop_id`,`guide_code`),
  KEY `idx_mt_h5_guide_state_guide_code` (`guide_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='H5门店引导状态表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mt_power_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '店铺ID',
  `balance` int NOT NULL DEFAULT '0' COMMENT '可用算力余额',
  `recharge_balance` int NOT NULL DEFAULT '0' COMMENT '实充剩余算力',
  `grant_balance` int NOT NULL DEFAULT '0' COMMENT '赠送剩余算力',
  `total_recharge` int NOT NULL DEFAULT '0' COMMENT '累计充值算力',
  `total_grant` int NOT NULL DEFAULT '0' COMMENT '累计赠送算力',
  `total_consume` int NOT NULL DEFAULT '0' COMMENT '累计消费算力',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_power_account_shop_id` (`shop_id`),
  KEY `idx_mt_power_account_balance` (`balance`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店算力账户表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mt_power_consume` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `consume_no` varchar(64) NOT NULL COMMENT '消费流水号',
  `shop_id` bigint NOT NULL COMMENT '店铺ID',
  `source_type` varchar(64) NOT NULL COMMENT '消费来源类型',
  `source_id` varchar(64) NOT NULL COMMENT '消费来源ID',
  `request_no` varchar(64) NOT NULL COMMENT '幂等请求号',
  `power_amount` int NOT NULL COMMENT '消费算力数量',
  `balance_before` int NOT NULL COMMENT '消费前余额',
  `balance_after` int NOT NULL COMMENT '消费后余额',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_power_consume_no` (`consume_no`),
  UNIQUE KEY `uk_mt_power_consume_shop_request_no` (`shop_id`,`request_no`),
  KEY `idx_mt_power_consume_source` (`source_type`,`source_id`),
  KEY `idx_mt_power_consume_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店算力消费表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mt_power_consume_detail` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店算力消费来源明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mt_power_grant` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店算力后台发放表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mt_power_recharge` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `recharge_no` varchar(64) NOT NULL COMMENT '充值单号',
  `shop_id` bigint NOT NULL COMMENT '店铺ID',
  `power_amount` int NOT NULL COMMENT '购买算力数量',
  `unit_power` int NOT NULL COMMENT '价格单位对应算力',
  `unit_price_fen` int NOT NULL COMMENT '价格单位金额（分）',
  `original_amount_fen` int NOT NULL DEFAULT '0' COMMENT '原价金额（分）',
  `discount_context_type` varchar(64) DEFAULT NULL COMMENT '冻结折扣上下文类型',
  `discount_tag` varchar(64) DEFAULT NULL COMMENT '冻结折扣标签',
  `discount_rate_basis_points` int NOT NULL DEFAULT '10000' COMMENT '冻结折扣比例，10000 表示原价',
  `redemption_code_id` bigint DEFAULT NULL COMMENT '冻结兑换码ID',
  `redemption_code` varchar(16) DEFAULT NULL COMMENT '冻结兑换码',
  `sales_user_id` varchar(64) DEFAULT NULL COMMENT '冻结网销用户ID',
  `sales_user_phone` varchar(20) DEFAULT NULL COMMENT '冻结网销用户手机号',
  `commission_window_start_at` datetime DEFAULT NULL COMMENT '佣金默认比例窗口起始时间',
  `default_commission_ratio` int DEFAULT NULL COMMENT '冻结默认佣金比例',
  `total_amount_fen` int NOT NULL COMMENT '订单总金额（分）',
  `pay_channel` varchar(32) DEFAULT NULL COMMENT '支付渠道',
  `pay_status` int NOT NULL DEFAULT '0' COMMENT '支付状态 0待支付 1支付中 2支付成功 3支付失败 4已关闭',
  `payment_order_no` varchar(64) DEFAULT NULL COMMENT '支付侧订单号',
  `transaction_id` varchar(128) DEFAULT NULL COMMENT '支付交易号',
  `pay_payload` longtext COMMENT '支付下单返回内容',
  `notify_payload` longtext COMMENT '支付通知内容',
  `paid_at` datetime DEFAULT NULL COMMENT '支付成功时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_power_recharge_no` (`recharge_no`),
  UNIQUE KEY `uk_mt_power_recharge_payment_order_no` (`payment_order_no`),
  KEY `idx_mt_power_recharge_shop_id` (`shop_id`),
  KEY `idx_mt_power_recharge_pay_status` (`pay_status`),
  KEY `idx_mt_power_recharge_sales_user_id` (`sales_user_id`),
  KEY `idx_mt_power_recharge_redemption_code` (`redemption_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店算力充值表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mt_shop` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(500) DEFAULT NULL COMMENT '店铺名字',
  `contact_name` varchar(255) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(255) DEFAULT NULL COMMENT '联系方式',
  `less_delivery_price` decimal(10,2) DEFAULT NULL COMMENT '减配送',
  `member_price` decimal(10,2) DEFAULT NULL COMMENT '会员价',
  `new_person_price` decimal(10,2) DEFAULT NULL COMMENT '新客减',
  `reduction` decimal(10,2) DEFAULT NULL COMMENT '平台扣点',
  `category1` bigint DEFAULT NULL COMMENT '一级分类',
  `category2` bigint DEFAULT NULL COMMENT '二级分类',
  `category3` bigint DEFAULT NULL COMMENT '三级分类',
  `address` varchar(255) DEFAULT NULL COMMENT '商家地址',
  `latitude` varchar(255) DEFAULT NULL COMMENT '纬度',
  `longitude` varchar(255) DEFAULT NULL COMMENT '经度',
  `epoi_id` varchar(64) DEFAULT NULL COMMENT '系统绑定美团店铺ID',
  `poi_id` varchar(64) DEFAULT NULL COMMENT '美团店铺ID',
  `poi_name` varchar(255) DEFAULT NULL COMMENT '美团店铺名称',
  `send_price` decimal(10,2) DEFAULT NULL COMMENT '配送费/起送相关金额',
  `guaranteed_price` decimal(10,2) DEFAULT NULL COMMENT '保底金额',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `promotion_info` varchar(1000) DEFAULT NULL COMMENT '店铺公告',
  `door_head_posters` longtext COMMENT '门头海报图JSON',
  `promote_img` longtext COMMENT '推广图JSON',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `door_head_posters_code` varchar(1000) DEFAULT NULL COMMENT '门头海报图云code',
  `promote_img_code` varchar(1000) DEFAULT NULL COMMENT '推广图云code',
  `mini_poi_id` varchar(100) DEFAULT NULL COMMENT '小程序店铺ID',
  `laber_name` varchar(255) DEFAULT NULL COMMENT '标签名称',
  `logo` varchar(1000) DEFAULT NULL COMMENT '店铺logo',
  `phone` varchar(255) DEFAULT NULL COMMENT '通知手机号',
  `shipping_time` varchar(255) DEFAULT NULL COMMENT '营业时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_shop_epoi_id` (`epoi_id`),
  KEY `idx_mt_shop_poi_id` (`poi_id`),
  KEY `idx_mt_shop_name` (`name`(191)),
  KEY `idx_mt_shop_poi_name` (`poi_name`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='美团店铺表';
/*!40101 SET character_set_client = @saved_cs_client */;

CREATE TABLE `mt_shop_right` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '店铺ID',
  `service_code` varchar(64) NOT NULL COMMENT '权益编码',
  `type` int NOT NULL DEFAULT '2' COMMENT '权益类型 1按次 2按时长',
  `expire_at` datetime DEFAULT NULL COMMENT '到期时间',
  `remaining_times` bigint DEFAULT NULL COMMENT '剩余次数',
  `source_version` varchar(128) DEFAULT NULL COMMENT '来源版本号',
  `last_order_id` varchar(255) DEFAULT NULL COMMENT '最近一次应用订单号',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_shop_right_shop_service` (`shop_id`,`service_code`),
  KEY `idx_mt_shop_right_expire_at` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店权益表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mt_version_service_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `mt_version` varchar(128) NOT NULL COMMENT '美团版本号',
  `service_code` varchar(64) NOT NULL COMMENT '权益编码',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序值',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_version_service_mapping` (`mt_version`,`service_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='美团版本权益映射表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_behavior_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_source` varchar(32) NOT NULL COMMENT '事件来源 H5/ADMIN_BACKEND',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型',
  `client_event_id` varchar(96) DEFAULT NULL COMMENT '客户端事件唯一ID',
  `session_id` varchar(96) DEFAULT NULL COMMENT '客户端应用会话ID',
  `shop_id` bigint NOT NULL COMMENT '门店ID',
  `shop_name_snapshot` varchar(255) DEFAULT NULL COMMENT '商家名称快照',
  `shop_phone_snapshot` varchar(64) DEFAULT NULL COMMENT '门店商家手机号快照',
  `backend_user_id` varchar(64) DEFAULT NULL COMMENT '后台用户ID',
  `h5_epoi_id` varchar(128) DEFAULT NULL COMMENT 'H5门店态epoiId',
  `event_time` datetime NOT NULL COMMENT '事件发生时间',
  `context_json` text COMMENT '事件上下文JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_behavior_event_client_event_id` (`client_event_id`),
  KEY `idx_user_behavior_event_time_shop` (`event_time`,`shop_id`),
  KEY `idx_user_behavior_event_shop_time` (`shop_id`,`event_time`),
  KEY `idx_user_behavior_event_source_type_time` (`event_source`,`event_type`,`event_time`),
  KEY `idx_user_behavior_event_session_time` (`session_id`,`event_time`),
  KEY `idx_user_behavior_event_backend_user` (`backend_user_id`,`event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户核心行为埋点事件明细';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `yz_app_auth` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(128) DEFAULT NULL COMMENT '有赞订购订单号',
  `yz_shop_id` bigint DEFAULT NULL COMMENT '有赞店铺表ID',
  `kdt_id` bigint DEFAULT NULL COMMENT '有赞店铺ID',
  `app_id` bigint DEFAULT NULL COMMENT '有赞应用编号',
  `buyer_id` bigint DEFAULT NULL COMMENT '购买者有赞用户ID',
  `buyer_phone` varchar(64) DEFAULT NULL COMMENT '购买者手机号',
  `open_id` varchar(128) DEFAULT NULL COMMENT '购买者有赞开放ID',
  `shop_display_no` varchar(64) DEFAULT NULL COMMENT '有赞店铺编号',
  `shop_type` varchar(64) DEFAULT NULL COMMENT '店铺类型',
  `shop_role` varchar(64) DEFAULT NULL COMMENT '店铺角色',
  `effect_time` bigint DEFAULT NULL COMMENT '授权生效时间戳，毫秒',
  `effect_time_at` datetime DEFAULT NULL COMMENT '授权生效时间',
  `expire_time` bigint DEFAULT NULL COMMENT '授权失效时间戳，毫秒',
  `expire_time_at` datetime DEFAULT NULL COMMENT '授权失效时间',
  `pay_time` bigint DEFAULT NULL COMMENT '支付时间戳，毫秒',
  `pay_time_at` datetime DEFAULT NULL COMMENT '支付时间',
  `env` varchar(32) DEFAULT NULL COMMENT '有赞环境',
  `raw_payload_json` longtext COMMENT '原始解密消息JSON',
  `callback_event_id` bigint DEFAULT NULL COMMENT '最近一次回调事件ID',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_yz_app_auth_order_kdt_app` (`order_no`,`kdt_id`,`app_id`),
  KEY `idx_yz_app_auth_kdt_id` (`kdt_id`),
  KEY `idx_yz_app_auth_yz_shop_id` (`yz_shop_id`),
  KEY `idx_yz_app_auth_expire_time` (`expire_time_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='有赞应用授权消息表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `yz_app_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(128) NOT NULL COMMENT '有赞订购订单号',
  `yz_shop_id` bigint DEFAULT NULL COMMENT '有赞店铺表ID',
  `kdt_id` bigint DEFAULT NULL COMMENT '有赞店铺ID',
  `root_kdt_id` bigint DEFAULT NULL COMMENT '有赞连锁总部店铺ID',
  `app_id` bigint DEFAULT NULL COMMENT '有赞应用编号',
  `buyer_id` bigint DEFAULT NULL COMMENT '购买者有赞用户ID',
  `buyer_phone` varchar(64) DEFAULT NULL COMMENT '购买者手机号',
  `open_id` varchar(128) DEFAULT NULL COMMENT '购买者有赞开放ID',
  `shop_name` varchar(255) DEFAULT NULL COMMENT '有赞店铺名称',
  `shop_display_no` varchar(64) DEFAULT NULL COMMENT '有赞店铺编号',
  `shop_type` varchar(64) DEFAULT NULL COMMENT '店铺类型',
  `shop_role` varchar(64) DEFAULT NULL COMMENT '店铺角色',
  `sku_version_text` varchar(255) DEFAULT NULL COMMENT '套餐名称',
  `sku_interval_text` varchar(64) DEFAULT NULL COMMENT '套餐有效期文本，单位天',
  `price` int DEFAULT NULL COMMENT '订单价格，分',
  `status` int DEFAULT NULL COMMENT '支付状态，20表示支付成功',
  `pay_time` bigint DEFAULT NULL COMMENT '支付时间戳，毫秒',
  `pay_time_at` datetime DEFAULT NULL COMMENT '支付时间',
  `env` varchar(32) DEFAULT NULL COMMENT '有赞环境',
  `use_status` varchar(32) NOT NULL DEFAULT 'UNUSED' COMMENT '使用状态 UNUSED/USED',
  `used_mt_shop_id` bigint DEFAULT NULL COMMENT '已核销到的美团门店ID',
  `used_right_code` varchar(64) DEFAULT NULL COMMENT '已授予权益编码',
  `used_at` datetime DEFAULT NULL COMMENT '核销时间',
  `used_by_user_id` varchar(64) DEFAULT NULL COMMENT '核销操作人ID',
  `used_by_username` varchar(128) DEFAULT NULL COMMENT '核销操作人名称',
  `use_remark` varchar(500) DEFAULT NULL COMMENT '核销备注',
  `raw_payload_json` longtext COMMENT '原始解密消息JSON',
  `callback_event_id` bigint DEFAULT NULL COMMENT '最近一次回调事件ID',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_yz_app_order_order_no` (`order_no`),
  KEY `idx_yz_app_order_kdt_id` (`kdt_id`),
  KEY `idx_yz_app_order_yz_shop_id` (`yz_shop_id`),
  KEY `idx_yz_app_order_pay_time` (`pay_time_at`),
  KEY `idx_yz_app_order_use_status` (`use_status`),
  KEY `idx_yz_app_order_used_mt_shop` (`used_mt_shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='有赞应用订购订单表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `yz_app_token` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `yz_shop_id` bigint DEFAULT NULL COMMENT '有赞店铺表ID',
  `kdt_id` bigint DEFAULT NULL COMMENT '有赞店铺ID',
  `authority_id` varchar(128) DEFAULT NULL COMMENT '有赞授权主体ID',
  `access_token` varchar(2000) DEFAULT NULL COMMENT '有赞访问令牌',
  `refresh_token` varchar(2000) DEFAULT NULL COMMENT '有赞刷新令牌',
  `scope` text COMMENT '授权范围',
  `expires_in` bigint DEFAULT NULL COMMENT 'access token有效秒数',
  `expires_at` datetime DEFAULT NULL COMMENT 'access token过期时间',
  `refresh_expires_in` bigint DEFAULT NULL COMMENT 'refresh token有效秒数',
  `refresh_expires_at` datetime DEFAULT NULL COMMENT 'refresh token过期时间',
  `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/REFRESH_FAILED/INVALID',
  `last_refresh_at` datetime DEFAULT NULL COMMENT '最近刷新时间',
  `raw_response_json` longtext COMMENT '令牌接口原始响应JSON',
  `last_error_msg` varchar(1000) DEFAULT NULL COMMENT '最近错误信息',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_yz_app_token_authority_id` (`authority_id`),
  KEY `idx_yz_app_token_kdt_id` (`kdt_id`),
  KEY `idx_yz_app_token_yz_shop_id` (`yz_shop_id`),
  KEY `idx_yz_app_token_expires_at` (`expires_at`),
  KEY `idx_yz_app_token_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='有赞应用授权token表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `yz_callback_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_type` varchar(64) DEFAULT NULL COMMENT '事件类型 APP_SUBSCRIBE/APP_AUTH/CODE/UNSUPPORTED',
  `request_uri` varchar(500) DEFAULT NULL COMMENT '请求URI',
  `raw_query_string` longtext COMMENT '原始查询串',
  `message_ciphertext` longtext COMMENT '有赞message密文',
  `auth_code` varchar(500) DEFAULT NULL COMMENT '有赞授权code',
  `decoded_payload_json` longtext COMMENT '解密后的消息JSON',
  `process_status` varchar(32) NOT NULL DEFAULT 'INIT' COMMENT '处理状态 INIT/SUCCESS/FAILED/UNSUPPORTED',
  `error_msg` varchar(1000) DEFAULT NULL COMMENT '错误信息',
  `client_ip` varchar(128) DEFAULT NULL COMMENT '客户端IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_yz_callback_event_type_status` (`event_type`,`process_status`),
  KEY `idx_yz_callback_event_create_time` (`create_time`),
  KEY `idx_yz_callback_event_auth_code` (`auth_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='有赞回调事件表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `yz_order_right_use_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `yz_app_order_id` bigint NOT NULL COMMENT '有赞订购订单ID',
  `order_no` varchar(128) NOT NULL COMMENT '有赞订购订单号',
  `mt_shop_id` bigint NOT NULL COMMENT '美团门店ID',
  `right_code` varchar(64) NOT NULL COMMENT '授予权益编码',
  `interval_days` int NOT NULL COMMENT '本次核销权益天数',
  `previous_expire_at` datetime DEFAULT NULL COMMENT '核销前权益过期时间',
  `new_expire_at` datetime NOT NULL COMMENT '核销后权益过期时间',
  `operator_user_id` varchar(64) DEFAULT NULL COMMENT '操作人ID',
  `operator_username` varchar(128) DEFAULT NULL COMMENT '操作人名称',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `status` varchar(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态 SUCCESS',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_yz_order_right_use_order_success` (`yz_app_order_id`,`status`),
  KEY `idx_yz_order_right_use_order_no` (`order_no`),
  KEY `idx_yz_order_right_use_mt_shop` (`mt_shop_id`),
  KEY `idx_yz_order_right_use_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='有赞订单权益核销记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `yz_shop` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `kdt_id` bigint NOT NULL COMMENT '有赞店铺ID',
  `root_kdt_id` bigint DEFAULT NULL COMMENT '有赞连锁总部店铺ID',
  `app_id` bigint DEFAULT NULL COMMENT '有赞应用编号',
  `shop_name` varchar(255) DEFAULT NULL COMMENT '有赞店铺名称',
  `shop_display_no` varchar(64) DEFAULT NULL COMMENT '有赞店铺编号',
  `shop_type` varchar(64) DEFAULT NULL COMMENT '店铺类型',
  `shop_role` varchar(64) DEFAULT NULL COMMENT '店铺角色',
  `buyer_id` bigint DEFAULT NULL COMMENT '购买者有赞用户ID',
  `buyer_phone` varchar(64) DEFAULT NULL COMMENT '购买者手机号',
  `open_id` varchar(128) DEFAULT NULL COMMENT '购买者有赞开放ID',
  `env` varchar(32) DEFAULT NULL COMMENT '有赞环境',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_yz_shop_kdt_id` (`kdt_id`),
  KEY `idx_yz_shop_root_kdt_id` (`root_kdt_id`),
  KEY `idx_yz_shop_open_id` (`open_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='有赞店铺表';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
