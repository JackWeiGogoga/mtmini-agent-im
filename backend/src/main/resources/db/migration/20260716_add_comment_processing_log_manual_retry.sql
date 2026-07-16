-- 管理员可在评价处理日志中手动重试失败的自动回复、差评申诉和群聊跟进。

INSERT IGNORE INTO `ai_menu` (
  `menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`,
  `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`
) VALUES
  ('MENU_COMMENT_PROCESSING_LOG_RETRY','MENU_COMMENT_PROCESSING_LOG','重试评价处理任务',NULL,NULL,'comment:processing-log:retry',NULL,'F',1,1,1,NOW(),NOW());

INSERT IGNORE INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`) VALUES
  ('ROLE_SUPER','MENU_COMMENT_PROCESSING_LOG_RETRY',NOW()),
  ('ROLE_ADMIN','MENU_COMMENT_PROCESSING_LOG_RETRY',NOW());
