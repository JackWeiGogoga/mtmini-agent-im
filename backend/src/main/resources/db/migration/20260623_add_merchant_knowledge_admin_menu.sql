-- 管理后台商户私有知识库菜单与权限。
INSERT IGNORE INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`,
  `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`
) VALUES
  ('MENU_MERCHANT_KNOWLEDGE','0','知识库管理','/merchant-knowledge','pages/merchant-knowledge/MerchantKnowledgeManage','comment:merchant-knowledge:list','DatabaseOutlined','C',13,1,1,'2026-06-23 02:00:00','2026-06-23 02:00:00'),
  ('MENU_MERCHANT_KNOWLEDGE_ADD','MENU_MERCHANT_KNOWLEDGE','新增知识',NULL,NULL,'comment:merchant-knowledge:add',NULL,'F',0,1,1,'2026-06-23 02:00:00','2026-06-23 02:00:00'),
  ('MENU_MERCHANT_KNOWLEDGE_EDIT','MENU_MERCHANT_KNOWLEDGE','编辑知识',NULL,NULL,'comment:merchant-knowledge:edit',NULL,'F',1,1,1,'2026-06-23 02:00:00','2026-06-23 02:00:00'),
  ('MENU_MERCHANT_KNOWLEDGE_DELETE','MENU_MERCHANT_KNOWLEDGE','删除知识',NULL,NULL,'comment:merchant-knowledge:delete',NULL,'F',2,1,1,'2026-06-23 02:00:00','2026-06-23 02:00:00');

INSERT IGNORE INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`) VALUES
  ('ROLE_SUPER','MENU_MERCHANT_KNOWLEDGE','2026-06-23 02:00:00'),
  ('ROLE_SUPER','MENU_MERCHANT_KNOWLEDGE_ADD','2026-06-23 02:00:00'),
  ('ROLE_SUPER','MENU_MERCHANT_KNOWLEDGE_EDIT','2026-06-23 02:00:00'),
  ('ROLE_SUPER','MENU_MERCHANT_KNOWLEDGE_DELETE','2026-06-23 02:00:00'),
  ('ROLE_ADMIN','MENU_MERCHANT_KNOWLEDGE','2026-06-23 02:00:00'),
  ('ROLE_ADMIN','MENU_MERCHANT_KNOWLEDGE_ADD','2026-06-23 02:00:00'),
  ('ROLE_ADMIN','MENU_MERCHANT_KNOWLEDGE_EDIT','2026-06-23 02:00:00'),
  ('ROLE_ADMIN','MENU_MERCHANT_KNOWLEDGE_DELETE','2026-06-23 02:00:00'),
  ('ROLE_USER','MENU_MERCHANT_KNOWLEDGE','2026-06-23 02:00:00'),
  ('ROLE_USER','MENU_MERCHANT_KNOWLEDGE_ADD','2026-06-23 02:00:00'),
  ('ROLE_USER','MENU_MERCHANT_KNOWLEDGE_EDIT','2026-06-23 02:00:00'),
  ('ROLE_USER','MENU_MERCHANT_KNOWLEDGE_DELETE','2026-06-23 02:00:00');
