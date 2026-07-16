-- =====================================================
-- Initialization data for mtmini-agent-reply
-- Generated from old online export auth_init_data.sql and filtered for reply-project menus.
-- Excludes business/runtime data: shops, tokens, orders, payments, logs, analytics events.
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
/*!40000 ALTER TABLE `ai_menu` DISABLE KEYS */;
INSERT IGNORE INTO `ai_menu` (`id`, `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`) VALUES
  (2,'MENU_SYSTEM','0','系统管理','/system',NULL,'','SettingOutlined','M',100,1,1,'2026-03-24 12:30:38','2026-04-26 22:28:27'),
  (3,'MENU_USER_LIST','MENU_SYSTEM','用户管理','/system/user','pages/user-manage/UserManage','system:user:list','UserOutlined','C',0,1,1,'2026-03-24 12:30:38','2026-03-24 12:30:38'),
  (4,'MENU_USER_ADD','MENU_USER_LIST','新增用户',NULL,NULL,'system:user:add',NULL,'F',0,1,1,'2026-03-24 12:30:38','2026-03-24 12:30:38'),
  (5,'MENU_USER_EDIT','MENU_USER_LIST','编辑用户',NULL,NULL,'system:user:edit',NULL,'F',1,1,1,'2026-03-24 12:30:38','2026-03-24 12:30:38'),
  (6,'MENU_USER_DELETE','MENU_USER_LIST','删除用户',NULL,NULL,'system:user:delete',NULL,'F',2,1,1,'2026-03-24 12:30:38','2026-03-24 12:30:38'),
  (7,'MENU_USER_ASSIGN','MENU_USER_LIST','分配角色',NULL,NULL,'system:user:assign',NULL,'F',3,1,1,'2026-03-24 12:30:38','2026-03-24 12:30:38'),
  (8,'MENU_ROLE_LIST','MENU_SYSTEM','角色管理','/system/role','pages/role-manage/RoleManage','system:role:list','SafetyCertificateOutlined','C',1,1,1,'2026-03-24 12:30:38','2026-03-24 12:30:38'),
  (9,'MENU_ROLE_ADD','MENU_ROLE_LIST','新增角色',NULL,NULL,'system:role:add',NULL,'F',0,1,1,'2026-03-24 12:30:38','2026-03-24 12:30:38'),
  (10,'MENU_ROLE_EDIT','MENU_ROLE_LIST','编辑角色',NULL,NULL,'system:role:edit',NULL,'F',1,1,1,'2026-03-24 12:30:38','2026-03-24 12:30:38'),
  (11,'MENU_ROLE_DELETE','MENU_ROLE_LIST','删除角色',NULL,NULL,'system:role:delete',NULL,'F',2,1,1,'2026-03-24 12:30:38','2026-03-24 12:30:38'),
  (12,'MENU_ROLE_ASSIGN','MENU_ROLE_LIST','分配权限',NULL,NULL,'system:role:assign',NULL,'F',3,1,1,'2026-03-24 12:30:38','2026-03-24 12:30:38'),
  (13,'MENU_MENU_LIST','MENU_SYSTEM','菜单管理','/system/menu','pages/menu-manage/MenuManage','system:menu:list','MenuOutlined','C',2,1,1,'2026-03-24 12:30:38','2026-03-24 12:30:38'),
  (14,'MENU_MENU_ADD','MENU_MENU_LIST','新增菜单',NULL,NULL,'system:menu:add',NULL,'F',0,1,1,'2026-03-24 12:30:38','2026-03-24 12:30:38'),
  (15,'MENU_MENU_EDIT','MENU_MENU_LIST','编辑菜单',NULL,NULL,'system:menu:edit',NULL,'F',1,1,1,'2026-03-24 12:30:38','2026-03-24 12:30:38'),
  (16,'MENU_MENU_DELETE','MENU_MENU_LIST','删除菜单',NULL,NULL,'system:menu:delete',NULL,'F',2,1,1,'2026-03-24 12:30:38','2026-03-24 12:30:38'),
  (17,'MENU_SHOP_POWER_GRANT_LIST','MENU_SHOP_MANAGE','查看算力发放',NULL,NULL,'shop:power-grant:list',NULL,'F',2,1,1,'2026-04-09 21:53:35','2026-04-11 22:11:28'),
  (18,'MENU_SHOP_POWER_GRANT_ADD','MENU_SHOP_MANAGE','发放算力',NULL,NULL,'shop:power-grant:add',NULL,'F',3,1,1,'2026-04-09 21:53:35','2026-04-11 22:11:28'),
  (19,'MENU_MASTER_CONSOLE','0','总控台','/master-console','pages/master-console/MasterConsole','shop:master-console:list','DashboardOutlined','C',4,1,1,'2026-04-11 10:47:31','2026-04-11 10:47:31'),
  (22,'MENU_SHOP_MANAGE','0','门店管理','/shops','pages/shop-manage/ShopManage','shop:binding:list','ShopOutlined','C',5,1,1,'2026-04-11 22:11:28','2026-04-11 22:11:28'),
  (23,'MENU_SHOP_BIND','MENU_SHOP_MANAGE','绑定门店',NULL,NULL,'shop:binding:add',NULL,'F',0,1,1,'2026-04-11 22:11:28','2026-04-11 22:11:28'),
  (24,'MENU_SHOP_UNBIND','MENU_SHOP_MANAGE','解绑门店',NULL,NULL,'shop:binding:delete',NULL,'F',1,1,1,'2026-04-11 22:11:28','2026-04-11 22:11:28'),
  (25,'MENU_POWER_MANAGE','0','算力管理','/power-management','pages/power-manage/PowerManage','shop:power-management:list','DatabaseOutlined','C',6,1,1,'2026-04-12 00:36:59','2026-04-12 00:36:59'),
  (26,'MENU_MT_APP_ORDER','0','美团订单','/mt-app-orders','pages/mt-app-order-manage/MtAppOrderManage','shop:mt-app-order:list','CreditCardOutlined','C',10,1,1,'2026-04-12 13:58:23','2026-04-26 22:28:34'),
  (27,'MENU_SALES_MASTER_CONSOLE','0','总控台','/master-console','pages/master-console/MasterConsole','sales:master-console:list','DashboardOutlined','C',4,1,1,'2026-04-26 22:25:22','2026-04-26 22:25:22'),
  (30,'MENU_SALES_SHOP_MANAGE','0','门店管理','/shops','pages/shop-manage/ShopManage','sales:binding:list','ShopOutlined','C',5,1,1,'2026-04-26 22:25:22','2026-04-26 22:25:22'),
  (31,'MENU_SALES_SHOP_BIND','MENU_SALES_SHOP_MANAGE','绑定门店',NULL,NULL,'sales:binding:add',NULL,'F',0,1,1,'2026-04-26 22:25:22','2026-04-26 22:25:22'),
  (32,'MENU_SALES_SHOP_UNBIND','MENU_SALES_SHOP_MANAGE','解绑门店',NULL,NULL,'sales:binding:delete',NULL,'F',1,1,1,'2026-04-26 22:25:22','2026-04-26 22:25:22'),
  (33,'MENU_SALES_CONFIG','0','网销配置','/sales/config','pages/sales-config/SalesConfig','sales:self-config:view','SettingOutlined','C',6,1,1,'2026-04-26 22:25:22','2026-04-26 22:25:22'),
  (34,'MENU_SALES_CONFIG_UPLOAD','MENU_SALES_CONFIG','上传收款码',NULL,NULL,'sales:self-config:upload-receipt',NULL,'F',0,1,1,'2026-04-26 22:25:22','2026-04-26 22:25:22'),
  (35,'MENU_SALES_COMMISSION','0','提成明细','/sales/commissions','pages/sales-commission-manage/SalesCommissionManage','sales:commission:list','ProfileOutlined','C',7,1,1,'2026-04-26 22:25:22','2026-04-26 22:25:22'),
  (36,'MENU_SALES_COMMISSION_RATIO_EDIT','MENU_SALES_COMMISSION','修改佣金比例',NULL,NULL,'sales:commission:ratio-edit',NULL,'F',0,1,1,'2026-04-26 22:25:22','2026-04-26 22:25:22'),
  (37,'MENU_SALES_COMMISSION_SETTLE','MENU_SALES_COMMISSION','修改结算状态',NULL,NULL,'sales:commission:settle',NULL,'F',1,1,1,'2026-04-26 22:25:22','2026-04-26 22:25:22'),
  (38,'MENU_SALES_REDEMPTION_CODES','0','兑换码','/sales/redemption-codes','pages/sales-redemption-code-manage/SalesRedemptionCodeManage','sales:redemption-code:list','AppstoreOutlined','C',8,1,1,'2026-04-26 22:25:22','2026-04-26 22:25:22'),
  (39,'MENU_SALES_REDEMPTION_CODES_CREATE','MENU_SALES_REDEMPTION_CODES','生成兑换码',NULL,NULL,'sales:redemption-code:create',NULL,'F',0,1,1,'2026-04-26 22:25:22','2026-04-26 22:25:22'),
  (40,'MENU_SALES_REDEMPTION_CODES_RECEIPT_CLEAR','MENU_SALES_REDEMPTION_CODES','删除收款码',NULL,NULL,'sales:redemption-code:receipt-clear',NULL,'F',1,1,1,'2026-04-26 22:25:22','2026-04-26 22:25:22'),
  (41,'MENU_POWER_RECHARGE_ORDER','0','充值订单','/power-recharge-orders','pages/power-recharge-order-manage/PowerRechargeOrderManage','system:power-recharge-order:list','CreditCardOutlined','C',9,1,1,'2026-04-26 22:25:43','2026-04-26 22:25:43'),
  (42,'MENU_YOUZAN_APP_ORDER','0','有赞订单','/youzan-app-orders','pages/youzan-app-order-manage/YouzanAppOrderManage','youzan:app-order:list','ProfileOutlined','C',9,1,1,'2026-04-29 22:58:57','2026-04-29 22:58:57'),
  (43,'MENU_YOUZAN_APP_ORDER_REDEEM','MENU_YOUZAN_APP_ORDER','核销有赞订单',NULL,NULL,'youzan:app-order:redeem',NULL,'F',0,1,1,'2026-04-29 22:58:57','2026-04-29 22:58:57'),
  (45,'MENU_USER_BEHAVIOR_ANALYTICS','0','行为统计','/analytics/user-behavior','pages/user-behavior-analytics/UserBehaviorAnalytics','analytics:user-behavior:list','LineChartOutlined','C',11,1,1,'2026-05-24 12:32:34','2026-05-24 12:32:34'),
  (46,'MENU_COMMENT_REPLY_KNOWLEDGE','0','回复文案','/comment-reply-knowledge','pages/comment-reply-knowledge/CommentReplyKnowledgeManage','comment:reply-knowledge:list','MessageOutlined','C',12,1,1,'2026-06-23 01:00:00','2026-06-23 01:00:00'),
  (47,'MENU_COMMENT_REPLY_KNOWLEDGE_ADD','MENU_COMMENT_REPLY_KNOWLEDGE','新增回复文案',NULL,NULL,'comment:reply-knowledge:add',NULL,'F',0,1,1,'2026-06-23 01:00:00','2026-06-23 01:00:00'),
  (48,'MENU_COMMENT_REPLY_KNOWLEDGE_EDIT','MENU_COMMENT_REPLY_KNOWLEDGE','编辑回复文案',NULL,NULL,'comment:reply-knowledge:edit',NULL,'F',1,1,1,'2026-06-23 01:00:00','2026-06-23 01:00:00'),
  (49,'MENU_COMMENT_REPLY_KNOWLEDGE_DELETE','MENU_COMMENT_REPLY_KNOWLEDGE','删除回复文案',NULL,NULL,'comment:reply-knowledge:delete',NULL,'F',2,1,1,'2026-06-23 01:00:00','2026-06-23 01:00:00'),
  (50,'MENU_MERCHANT_KNOWLEDGE','0','知识库管理','/merchant-knowledge','pages/merchant-knowledge/MerchantKnowledgeManage','comment:merchant-knowledge:list','DatabaseOutlined','C',13,1,1,'2026-06-23 02:00:00','2026-06-23 02:00:00'),
  (51,'MENU_MERCHANT_KNOWLEDGE_ADD','MENU_MERCHANT_KNOWLEDGE','新增知识',NULL,NULL,'comment:merchant-knowledge:add',NULL,'F',0,1,1,'2026-06-23 02:00:00','2026-06-23 02:00:00'),
  (52,'MENU_MERCHANT_KNOWLEDGE_EDIT','MENU_MERCHANT_KNOWLEDGE','编辑知识',NULL,NULL,'comment:merchant-knowledge:edit',NULL,'F',1,1,1,'2026-06-23 02:00:00','2026-06-23 02:00:00'),
  (53,'MENU_MERCHANT_KNOWLEDGE_DELETE','MENU_MERCHANT_KNOWLEDGE','删除知识',NULL,NULL,'comment:merchant-knowledge:delete',NULL,'F',2,1,1,'2026-06-23 02:00:00','2026-06-23 02:00:00'),
  (54,'MENU_SYSTEM_PROMPT','MENU_SYSTEM','系统提示词','/system/prompts','pages/system-prompt/SystemPromptManage','system:prompt:list','MessageOutlined','C',3,1,1,'2026-06-27 00:00:00','2026-06-27 00:00:00'),
  (55,'MENU_SYSTEM_PROMPT_QUERY','MENU_SYSTEM_PROMPT','查看提示词',NULL,NULL,'system:prompt:query',NULL,'F',0,1,1,'2026-06-27 00:00:00','2026-06-27 00:00:00'),
  (56,'MENU_SYSTEM_PROMPT_EDIT','MENU_SYSTEM_PROMPT','编辑提示词',NULL,NULL,'system:prompt:edit',NULL,'F',1,1,1,'2026-06-27 00:00:00','2026-06-27 00:00:00'),
  (57,'MENU_COMMENT_PROCESSING_LOG','0','评价处理日志','/comment-processing-logs','pages/comment-processing-log/CommentProcessingLog','comment:processing-log:list','FileSearchOutlined','C',14,1,1,'2026-07-14 00:00:00','2026-07-14 00:00:00'),
  (58,'MENU_COMMENT_PROCESSING_LOG_QUERY','MENU_COMMENT_PROCESSING_LOG','查看评价处理详情',NULL,NULL,'comment:processing-log:query',NULL,'F',0,1,1,'2026-07-14 00:00:00','2026-07-14 00:00:00'),
  (59,'MENU_COMMENT_PROCESSING_LOG_RETRY','MENU_COMMENT_PROCESSING_LOG','重试评价处理任务',NULL,NULL,'comment:processing-log:retry',NULL,'F',1,1,1,'2026-07-16 00:00:00','2026-07-16 00:00:00');
/*!40000 ALTER TABLE `ai_menu` ENABLE KEYS */;

/*!40000 ALTER TABLE `ai_role` DISABLE KEYS */;
INSERT IGNORE INTO `ai_role` (`id`, `role_id`, `role_code`, `role_name`, `description`, `order_num`, `status`, `create_time`, `update_time`) VALUES
  (1,'ROLE_SUPER','super_admin','超级管理员','最高权限，可访问所有功能',0,1,'2026-03-24 12:30:38','2026-03-24 12:30:38'),
  (2,'ROLE_ADMIN','admin','系统管理员','系统管理权限',1,1,'2026-03-24 12:30:38','2026-03-24 12:30:38'),
  (3,'ROLE_USER','user','普通用户','基本操作权限',2,1,'2026-03-24 12:30:38','2026-03-24 12:30:38'),
  (4,'ROLE_SALES','sales_user','网销用户','网销与提成查看权限',3,1,'2026-04-26 22:25:22','2026-04-26 22:25:22'),
  (5,'ROLE_YOUZAN_OPERATOR','youzan_operator','有赞运营','查看并核销有赞订购订单',3,1,'2026-04-29 22:58:57','2026-04-29 22:58:57');
/*!40000 ALTER TABLE `ai_role` ENABLE KEYS */;

/*!40000 ALTER TABLE `ai_role_menu` DISABLE KEYS */;
INSERT IGNORE INTO `ai_role_menu` (`id`, `role_id`, `menu_id`, `create_time`) VALUES
  (1,'ROLE_SUPER','MENU_MENU_ADD','2026-03-24 12:30:38'),
  (2,'ROLE_SUPER','MENU_MENU_DELETE','2026-03-24 12:30:38'),
  (3,'ROLE_SUPER','MENU_MENU_EDIT','2026-03-24 12:30:38'),
  (4,'ROLE_SUPER','MENU_MENU_LIST','2026-03-24 12:30:38'),
  (5,'ROLE_SUPER','MENU_ROLE_ADD','2026-03-24 12:30:38'),
  (6,'ROLE_SUPER','MENU_ROLE_ASSIGN','2026-03-24 12:30:38'),
  (7,'ROLE_SUPER','MENU_ROLE_DELETE','2026-03-24 12:30:38'),
  (8,'ROLE_SUPER','MENU_ROLE_EDIT','2026-03-24 12:30:38'),
  (9,'ROLE_SUPER','MENU_ROLE_LIST','2026-03-24 12:30:38'),
  (10,'ROLE_SUPER','MENU_SYSTEM','2026-03-24 12:30:38'),
  (11,'ROLE_SUPER','MENU_USER_ADD','2026-03-24 12:30:38'),
  (12,'ROLE_SUPER','MENU_USER_ASSIGN','2026-03-24 12:30:38'),
  (13,'ROLE_SUPER','MENU_USER_DELETE','2026-03-24 12:30:38'),
  (14,'ROLE_SUPER','MENU_USER_EDIT','2026-03-24 12:30:38'),
  (15,'ROLE_SUPER','MENU_USER_LIST','2026-03-24 12:30:38'),
  (33,'ROLE_ADMIN','MENU_SYSTEM','2026-03-24 12:30:38'),
  (34,'ROLE_ADMIN','MENU_USER_LIST','2026-03-24 12:30:38'),
  (35,'ROLE_ADMIN','MENU_USER_ADD','2026-03-24 12:30:38'),
  (36,'ROLE_ADMIN','MENU_USER_EDIT','2026-03-24 12:30:38'),
  (37,'ROLE_ADMIN','MENU_USER_DELETE','2026-03-24 12:30:38'),
  (38,'ROLE_ADMIN','MENU_USER_ASSIGN','2026-03-24 12:30:38'),
  (39,'ROLE_ADMIN','MENU_ROLE_LIST','2026-03-24 12:30:38'),
  (40,'ROLE_ADMIN','MENU_ROLE_ADD','2026-03-24 12:30:38'),
  (41,'ROLE_ADMIN','MENU_ROLE_EDIT','2026-03-24 12:30:38'),
  (42,'ROLE_ADMIN','MENU_ROLE_DELETE','2026-03-24 12:30:38'),
  (43,'ROLE_ADMIN','MENU_ROLE_ASSIGN','2026-03-24 12:30:38'),
  (44,'ROLE_ADMIN','MENU_MENU_LIST','2026-03-24 12:30:38'),
  (45,'ROLE_ADMIN','MENU_MENU_ADD','2026-03-24 12:30:38'),
  (46,'ROLE_ADMIN','MENU_MENU_EDIT','2026-03-24 12:30:38'),
  (47,'ROLE_ADMIN','MENU_MENU_DELETE','2026-03-24 12:30:38'),
  (49,'ROLE_ADMIN','MENU_SHOP_POWER_GRANT_LIST','2026-04-09 21:53:35'),
  (50,'ROLE_ADMIN','MENU_SHOP_POWER_GRANT_ADD','2026-04-09 21:53:35'),
  (51,'ROLE_SUPER','MENU_MASTER_CONSOLE','2026-04-11 10:47:32'),
  (54,'ROLE_ADMIN','MENU_MASTER_CONSOLE','2026-04-11 10:47:32'),
  (57,'ROLE_USER','MENU_MASTER_CONSOLE','2026-04-11 11:17:55'),
  (60,'ROLE_ADMIN','MENU_SHOP_MANAGE','2026-04-11 22:11:28'),
  (61,'ROLE_ADMIN','MENU_SHOP_BIND','2026-04-11 22:11:28'),
  (62,'ROLE_ADMIN','MENU_SHOP_UNBIND','2026-04-11 22:11:28'),
  (63,'ROLE_USER','MENU_SHOP_MANAGE','2026-04-11 22:11:28'),
  (64,'ROLE_USER','MENU_SHOP_BIND','2026-04-11 22:11:28'),
  (65,'ROLE_USER','MENU_SHOP_UNBIND','2026-04-11 22:11:28'),
  (66,'ROLE_ADMIN','MENU_MT_APP_ORDER','2026-04-12 13:58:23'),
  (67,'ROLE_SALES','MENU_SALES_MASTER_CONSOLE','2026-04-26 22:25:22'),
  (70,'ROLE_SALES','MENU_SALES_SHOP_MANAGE','2026-04-26 22:25:22'),
  (71,'ROLE_SALES','MENU_SALES_SHOP_BIND','2026-04-26 22:25:22'),
  (72,'ROLE_SALES','MENU_SALES_SHOP_UNBIND','2026-04-26 22:25:22'),
  (73,'ROLE_SALES','MENU_SALES_CONFIG','2026-04-26 22:25:22'),
  (74,'ROLE_SALES','MENU_SALES_CONFIG_UPLOAD','2026-04-26 22:25:22'),
  (75,'ROLE_SALES','MENU_SALES_COMMISSION','2026-04-26 22:25:23'),
  (76,'ROLE_SUPER','MENU_SALES_COMMISSION','2026-04-26 22:25:23'),
  (77,'ROLE_SUPER','MENU_SALES_COMMISSION_RATIO_EDIT','2026-04-26 22:25:23'),
  (78,'ROLE_SUPER','MENU_SALES_COMMISSION_SETTLE','2026-04-26 22:25:23'),
  (79,'ROLE_SUPER','MENU_SALES_REDEMPTION_CODES','2026-04-26 22:25:23'),
  (80,'ROLE_SUPER','MENU_SALES_REDEMPTION_CODES_CREATE','2026-04-26 22:25:23'),
  (81,'ROLE_SUPER','MENU_SALES_REDEMPTION_CODES_RECEIPT_CLEAR','2026-04-26 22:25:23'),
  (82,'ROLE_SUPER','MENU_POWER_RECHARGE_ORDER','2026-04-26 22:25:43'),
  (83,'ROLE_SUPER','MENU_YOUZAN_APP_ORDER','2026-04-29 22:58:57'),
  (84,'ROLE_SUPER','MENU_YOUZAN_APP_ORDER_REDEEM','2026-04-29 22:58:57'),
  (85,'ROLE_YOUZAN_OPERATOR','MENU_YOUZAN_APP_ORDER','2026-04-29 22:58:57'),
  (86,'ROLE_YOUZAN_OPERATOR','MENU_YOUZAN_APP_ORDER_REDEEM','2026-04-29 22:58:57'),
  (92,'ROLE_ADMIN','MENU_USER_BEHAVIOR_ANALYTICS','2026-05-24 12:32:34'),
  (93,'ROLE_SUPER','MENU_USER_BEHAVIOR_ANALYTICS','2026-05-24 12:32:34'),
  (94,'ROLE_SALES','MENU_USER_BEHAVIOR_ANALYTICS','2026-05-24 12:32:34'),
  (95,'ROLE_SUPER','MENU_COMMENT_REPLY_KNOWLEDGE','2026-06-23 01:00:00'),
  (96,'ROLE_SUPER','MENU_COMMENT_REPLY_KNOWLEDGE_ADD','2026-06-23 01:00:00'),
  (97,'ROLE_SUPER','MENU_COMMENT_REPLY_KNOWLEDGE_EDIT','2026-06-23 01:00:00'),
  (98,'ROLE_SUPER','MENU_COMMENT_REPLY_KNOWLEDGE_DELETE','2026-06-23 01:00:00'),
  (99,'ROLE_SUPER','MENU_MERCHANT_KNOWLEDGE','2026-06-23 02:00:00'),
  (100,'ROLE_SUPER','MENU_MERCHANT_KNOWLEDGE_ADD','2026-06-23 02:00:00'),
  (101,'ROLE_SUPER','MENU_MERCHANT_KNOWLEDGE_EDIT','2026-06-23 02:00:00'),
  (102,'ROLE_SUPER','MENU_MERCHANT_KNOWLEDGE_DELETE','2026-06-23 02:00:00'),
  (103,'ROLE_ADMIN','MENU_MERCHANT_KNOWLEDGE','2026-06-23 02:00:00'),
  (104,'ROLE_ADMIN','MENU_MERCHANT_KNOWLEDGE_ADD','2026-06-23 02:00:00'),
  (105,'ROLE_ADMIN','MENU_MERCHANT_KNOWLEDGE_EDIT','2026-06-23 02:00:00'),
  (106,'ROLE_ADMIN','MENU_MERCHANT_KNOWLEDGE_DELETE','2026-06-23 02:00:00'),
  (107,'ROLE_USER','MENU_MERCHANT_KNOWLEDGE','2026-06-23 02:00:00'),
  (108,'ROLE_USER','MENU_MERCHANT_KNOWLEDGE_ADD','2026-06-23 02:00:00'),
  (109,'ROLE_USER','MENU_MERCHANT_KNOWLEDGE_EDIT','2026-06-23 02:00:00'),
  (110,'ROLE_USER','MENU_MERCHANT_KNOWLEDGE_DELETE','2026-06-23 02:00:00'),
  (111,'ROLE_SUPER','MENU_SYSTEM_PROMPT','2026-06-27 00:00:00'),
  (112,'ROLE_SUPER','MENU_SYSTEM_PROMPT_QUERY','2026-06-27 00:00:00'),
  (113,'ROLE_SUPER','MENU_SYSTEM_PROMPT_EDIT','2026-06-27 00:00:00'),
  (114,'ROLE_ADMIN','MENU_SYSTEM_PROMPT','2026-06-27 00:00:00'),
  (115,'ROLE_ADMIN','MENU_SYSTEM_PROMPT_QUERY','2026-06-27 00:00:00'),
  (116,'ROLE_ADMIN','MENU_SYSTEM_PROMPT_EDIT','2026-06-27 00:00:00'),
  (117,'ROLE_SUPER','MENU_COMMENT_PROCESSING_LOG','2026-07-14 00:00:00'),
  (118,'ROLE_SUPER','MENU_COMMENT_PROCESSING_LOG_QUERY','2026-07-14 00:00:00'),
  (119,'ROLE_ADMIN','MENU_COMMENT_PROCESSING_LOG','2026-07-14 00:00:00'),
  (120,'ROLE_ADMIN','MENU_COMMENT_PROCESSING_LOG_QUERY','2026-07-14 00:00:00'),
  (121,'ROLE_SUPER','MENU_COMMENT_PROCESSING_LOG_RETRY','2026-07-16 00:00:00'),
  (122,'ROLE_ADMIN','MENU_COMMENT_PROCESSING_LOG_RETRY','2026-07-16 00:00:00');
/*!40000 ALTER TABLE `ai_role_menu` ENABLE KEYS */;

/*!40000 ALTER TABLE `mt_version_service_mapping` DISABLE KEYS */;
INSERT IGNORE INTO `mt_version_service_mapping` (`id`, `mt_version`, `service_code`, `enabled`, `sort`, `create_time`, `update_time`) VALUES
  (1,'v2','H5_PROMOTION',1,0,'2026-04-06 10:59:20','2026-04-06 10:59:20'),
  (2,'v1','H5_PROMOTION',1,0,'2026-04-06 10:59:20','2026-04-06 10:59:20'),
  (3,'v6','H5_PROMOTION',1,0,'2026-04-06 10:59:20','2026-04-06 10:59:20'),
  (4,'v7','MT_APP_OPEN_ONLY',1,0,'2026-04-06 10:59:20','2026-04-06 10:59:20'),
  (5,'v8','H5_PROMOTION',1,0,'2026-04-06 10:59:20','2026-04-06 10:59:20'),
  (6,'v9','H5_PROMOTION',1,0,'2026-04-06 10:59:20','2026-04-06 10:59:20'),
  (7,'v10','H5_PROMOTION',1,0,'2026-04-06 10:59:20','2026-04-06 10:59:20'),
  (8,'v11','H5_PROMOTION',1,0,'2026-04-06 10:59:20','2026-04-06 10:59:20'),
  (9,'v12','H5_PROMOTION',1,0,'2026-04-06 10:59:20','2026-04-06 10:59:20'),
  (10,'v13','H5_PROMOTION',1,0,'2026-04-06 10:59:20','2026-04-06 10:59:20'),
  (11,'v14','H5_PROMOTION',1,0,'2026-04-06 10:59:20','2026-04-06 10:59:20'),
  (12,'v15','H5_PROMOTION',1,0,'2026-04-06 10:59:20','2026-04-06 10:59:20'),
  (13,'v16','H5_PROMOTION',1,0,'2026-04-06 10:59:20','2026-04-06 10:59:20'),
  (14,'v17','H5_PROMOTION',1,0,'2026-04-06 10:59:20','2026-04-06 10:59:20'),
  (15,'v18','H5_PROMOTION',1,0,'2026-04-06 10:59:20','2026-04-06 10:59:20'),
  (16,'v19','H5_PROMOTION',1,0,'2026-04-06 10:59:20','2026-04-06 10:59:20'),
  (17,'v20','H5_PROMOTION',1,0,'2026-04-06 10:59:20','2026-04-06 10:59:20');
/*!40000 ALTER TABLE `mt_version_service_mapping` ENABLE KEYS */;

/*!40000 ALTER TABLE `ai_user` DISABLE KEYS */;
-- 初始化超级管理员用户
-- 默认密码: wukxi4-garwut-tuVhah (BCrypt 加密)
INSERT IGNORE INTO `ai_user` (`id`, `user_id`, `username`, `nickname`, `email`, `phone`, `password`, `auth_provider`, `avatar`, `status`, `create_time`, `update_time`) VALUES
  (1,'1','admin','超级管理员','admin@waimaimaster.com',NULL,'$2a$10$kyEHbvzE6GzelFAasRfFpe7queowCsPpwL95I8OJFplnqOaeDwCry','PASSWORD',NULL,1,'2026-03-24 12:30:38','2026-04-11 10:53:46');
/*!40000 ALTER TABLE `ai_user` ENABLE KEYS */;

/*!40000 ALTER TABLE `ai_user_role` DISABLE KEYS */;
INSERT IGNORE INTO `ai_user_role` (`id`, `user_id`, `role_id`, `create_time`) VALUES
  (1,'1','ROLE_SUPER','2026-03-24 12:30:38');
/*!40000 ALTER TABLE `ai_user_role` ENABLE KEYS */;

/*!40000 ALTER TABLE `ai_user_data_scope` DISABLE KEYS */;
INSERT IGNORE INTO `ai_user_data_scope` (`id`, `user_id`, `scope_type`, `create_time`, `update_time`) VALUES
  (1,'1','ALL','2026-04-11 23:48:19','2026-04-11 23:48:19');
/*!40000 ALTER TABLE `ai_user_data_scope` ENABLE KEYS */;


/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
