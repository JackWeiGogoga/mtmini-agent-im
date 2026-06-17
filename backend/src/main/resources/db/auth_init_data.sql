-- =============================================
-- 初始化数据
-- =============================================

-- 初始化菜单数据
INSERT INTO `ai_menu` (`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `permission`, `icon`, `menu_type`, `order_num`, `visible`, `status`, `create_time`, `update_time`)
VALUES
    -- 门店管理
    ('MENU_MASTER_CONSOLE', '0', '总控台', '/master-console', 'pages/master-console/MasterConsole', 'shop:master-console:list', 'DashboardOutlined', 'C', 4, 1, 1, NOW(), NOW()),
    ('MENU_MASTER_CONSOLE_CONFIG', 'MENU_MASTER_CONSOLE', '配置推广', NULL, NULL, 'shop:master-console:config', NULL, 'F', 0, 1, 1, NOW(), NOW()),
    ('MENU_MASTER_CONSOLE_LOG', 'MENU_MASTER_CONSOLE', '查看推广日志', NULL, NULL, 'shop:master-console:log', NULL, 'F', 1, 1, 1, NOW(), NOW()),
    ('MENU_SHOP_MANAGE', '0', '门店管理', '/shops', 'pages/shop-manage/ShopManage', 'shop:binding:list', 'ShopOutlined', 'C', 5, 1, 1, NOW(), NOW()),
    ('MENU_POWER_MANAGE', '0', '算力管理', '/power-management', 'pages/power-manage/PowerManage', 'shop:power-management:list', 'DatabaseOutlined', 'C', 6, 1, 1, NOW(), NOW()),
    ('MENU_POWER_RECHARGE_ORDER', '0', '充值订单', '/power-recharge-orders', 'pages/power-recharge-order-manage/PowerRechargeOrderManage', 'system:power-recharge-order:list', 'CreditCardOutlined', 'C', 7, 1, 1, NOW(), NOW()),
    ('MENU_MT_APP_ORDER', '0', '美团订单', '/mt-app-orders', 'pages/mt-app-order-manage/MtAppOrderManage', 'shop:mt-app-order:list', 'ProfileOutlined', 'C', 8, 1, 1, NOW(), NOW()),
    ('MENU_YOUZAN_APP_ORDER', '0', '有赞订单', '/youzan-app-orders', 'pages/youzan-app-order-manage/YouzanAppOrderManage', 'youzan:app-order:list', 'ProfileOutlined', 'C', 9, 1, 1, NOW(), NOW()),
    ('MENU_YOUZAN_APP_ORDER_REDEEM', 'MENU_YOUZAN_APP_ORDER', '核销有赞订单', NULL, NULL, 'youzan:app-order:redeem', NULL, 'F', 0, 1, 1, NOW(), NOW()),
    ('MENU_SHOP_BIND', 'MENU_SHOP_MANAGE', '绑定门店', NULL, NULL, 'shop:binding:add', NULL, 'F', 0, 1, 1, NOW(), NOW()),
    ('MENU_SHOP_UNBIND', 'MENU_SHOP_MANAGE', '解绑门店', NULL, NULL, 'shop:binding:delete', NULL, 'F', 1, 1, 1, NOW(), NOW()),
    ('MENU_SHOP_POWER_GRANT_LIST', 'MENU_SHOP_MANAGE', '查看算力发放', NULL, NULL, 'shop:power-grant:list', NULL, 'F', 2, 1, 1, NOW(), NOW()),
    ('MENU_SHOP_POWER_GRANT_ADD', 'MENU_SHOP_MANAGE', '发放算力', NULL, NULL, 'shop:power-grant:add', NULL, 'F', 3, 1, 1, NOW(), NOW()),

    -- 系统管理目录
    ('MENU_SYSTEM', '0', '系统管理', '/system', NULL, '', 'SettingOutlined', 'M', 10, 1, 1, NOW(), NOW()),
    
    -- 用户管理
    ('MENU_USER_LIST', 'MENU_SYSTEM', '用户管理', '/system/user', 'pages/user-manage/UserManage', 'system:user:list', 'UserOutlined', 'C', 0, 1, 1, NOW(), NOW()),
    ('MENU_USER_ADD', 'MENU_USER_LIST', '新增用户', NULL, NULL, 'system:user:add', NULL, 'F', 0, 1, 1, NOW(), NOW()),
    ('MENU_USER_EDIT', 'MENU_USER_LIST', '编辑用户', NULL, NULL, 'system:user:edit', NULL, 'F', 1, 1, 1, NOW(), NOW()),
    ('MENU_USER_DELETE', 'MENU_USER_LIST', '删除用户', NULL, NULL, 'system:user:delete', NULL, 'F', 2, 1, 1, NOW(), NOW()),
    ('MENU_USER_ASSIGN', 'MENU_USER_LIST', '分配角色', NULL, NULL, 'system:user:assign', NULL, 'F', 3, 1, 1, NOW(), NOW()),
    
    -- 角色管理
    ('MENU_ROLE_LIST', 'MENU_SYSTEM', '角色管理', '/system/role', 'pages/role-manage/RoleManage', 'system:role:list', 'SafetyCertificateOutlined', 'C', 1, 1, 1, NOW(), NOW()),
    ('MENU_ROLE_ADD', 'MENU_ROLE_LIST', '新增角色', NULL, NULL, 'system:role:add', NULL, 'F', 0, 1, 1, NOW(), NOW()),
    ('MENU_ROLE_EDIT', 'MENU_ROLE_LIST', '编辑角色', NULL, NULL, 'system:role:edit', NULL, 'F', 1, 1, 1, NOW(), NOW()),
    ('MENU_ROLE_DELETE', 'MENU_ROLE_LIST', '删除角色', NULL, NULL, 'system:role:delete', NULL, 'F', 2, 1, 1, NOW(), NOW()),
    ('MENU_ROLE_ASSIGN', 'MENU_ROLE_LIST', '分配权限', NULL, NULL, 'system:role:assign', NULL, 'F', 3, 1, 1, NOW(), NOW()),
    
    -- 菜单管理
    ('MENU_MENU_LIST', 'MENU_SYSTEM', '菜单管理', '/system/menu', 'pages/menu-manage/MenuManage', 'system:menu:list', 'MenuOutlined', 'C', 2, 1, 1, NOW(), NOW()),
    ('MENU_MENU_ADD', 'MENU_MENU_LIST', '新增菜单', NULL, NULL, 'system:menu:add', NULL, 'F', 0, 1, 1, NOW(), NOW()),
    ('MENU_MENU_EDIT', 'MENU_MENU_LIST', '编辑菜单', NULL, NULL, 'system:menu:edit', NULL, 'F', 1, 1, 1, NOW(), NOW()),
    ('MENU_MENU_DELETE', 'MENU_MENU_LIST', '删除菜单', NULL, NULL, 'system:menu:delete', NULL, 'F', 2, 1, 1, NOW(), NOW());

-- 初始化角色数据
INSERT INTO `ai_role` (`role_id`, `role_code`, `role_name`, `description`, `order_num`, `status`, `create_time`, `update_time`)
VALUES
    ('ROLE_SUPER', 'super_admin', '超级管理员', '最高权限，可访问所有功能', 0, 1, NOW(), NOW()),
    ('ROLE_ADMIN', 'admin', '系统管理员', '系统管理权限', 1, 1, NOW(), NOW()),
    ('ROLE_USER', 'user', '普通用户', '基本操作权限', 2, 1, NOW(), NOW()),
    ('ROLE_YOUZAN_OPERATOR', 'youzan_operator', '有赞运营', '查看并核销有赞订购订单', 3, 1, NOW(), NOW());

-- 超级管理员分配所有菜单权限（实际上超级管理员会跳过权限检查，这里只是为了数据完整性）
INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
SELECT 'ROLE_SUPER', menu_id, NOW() FROM `ai_menu`;

-- 系统管理员分配权限管理相关菜单
INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
VALUES
    ('ROLE_ADMIN', 'MENU_MASTER_CONSOLE', NOW()),
    ('ROLE_ADMIN', 'MENU_MASTER_CONSOLE_CONFIG', NOW()),
    ('ROLE_ADMIN', 'MENU_MASTER_CONSOLE_LOG', NOW()),
    ('ROLE_ADMIN', 'MENU_SHOP_MANAGE', NOW()),
    ('ROLE_ADMIN', 'MENU_SHOP_BIND', NOW()),
    ('ROLE_ADMIN', 'MENU_SHOP_UNBIND', NOW()),
    ('ROLE_ADMIN', 'MENU_SHOP_POWER_GRANT_LIST', NOW()),
    ('ROLE_ADMIN', 'MENU_SHOP_POWER_GRANT_ADD', NOW()),
    ('ROLE_ADMIN', 'MENU_MT_APP_ORDER', NOW()),
    ('ROLE_ADMIN', 'MENU_SYSTEM', NOW()),
    ('ROLE_ADMIN', 'MENU_USER_LIST', NOW()),
    ('ROLE_ADMIN', 'MENU_USER_ADD', NOW()),
    ('ROLE_ADMIN', 'MENU_USER_EDIT', NOW()),
    ('ROLE_ADMIN', 'MENU_USER_DELETE', NOW()),
    ('ROLE_ADMIN', 'MENU_USER_ASSIGN', NOW()),
    ('ROLE_ADMIN', 'MENU_ROLE_LIST', NOW()),
    ('ROLE_ADMIN', 'MENU_ROLE_ADD', NOW()),
    ('ROLE_ADMIN', 'MENU_ROLE_EDIT', NOW()),
    ('ROLE_ADMIN', 'MENU_ROLE_DELETE', NOW()),
    ('ROLE_ADMIN', 'MENU_ROLE_ASSIGN', NOW()),
    ('ROLE_ADMIN', 'MENU_MENU_LIST', NOW()),
    ('ROLE_ADMIN', 'MENU_MENU_ADD', NOW()),
    ('ROLE_ADMIN', 'MENU_MENU_EDIT', NOW()),
    ('ROLE_ADMIN', 'MENU_MENU_DELETE', NOW());

-- 普通用户默认仅分配欢迎页
INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
VALUES
    ('ROLE_USER', 'MENU_MASTER_CONSOLE', NOW()),
    ('ROLE_USER', 'MENU_MASTER_CONSOLE_CONFIG', NOW()),
    ('ROLE_USER', 'MENU_MASTER_CONSOLE_LOG', NOW()),
    ('ROLE_USER', 'MENU_SHOP_MANAGE', NOW()),
    ('ROLE_USER', 'MENU_SHOP_BIND', NOW()),
    ('ROLE_USER', 'MENU_SHOP_UNBIND', NOW());

INSERT INTO `ai_role_menu` (`role_id`, `menu_id`, `create_time`)
VALUES
    ('ROLE_YOUZAN_OPERATOR', 'MENU_YOUZAN_APP_ORDER', NOW()),
    ('ROLE_YOUZAN_OPERATOR', 'MENU_YOUZAN_APP_ORDER_REDEEM', NOW());

-- =============================================
-- 初始化超级管理员用户
-- 默认密码: wukxi4-garwut-tuVhah (BCrypt 加密)
INSERT INTO ai_user (user_id, username, nickname, email, password, auth_provider, status)
VALUES ('1', 'admin', '超级管理员', 'admin@waimaimaster.com', '$2a$10$kyEHbvzE6GzelFAasRfFpe7queowCsPpwL95I8OJFplnqOaeDwCry', 'PASSWORD', 1);

-- 为超级管理员分配超级管理员角色
INSERT INTO `ai_user_role` (`user_id`, `role_id`, `create_time`)
VALUES
    ('1', 'ROLE_SUPER', NOW());
