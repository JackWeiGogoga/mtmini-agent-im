CREATE TABLE IF NOT EXISTS `ai_user_data_scope` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `scope_type` varchar(20) NOT NULL COMMENT '数据范围 SELF/TEAM/ALL',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_ai_user_data_scope_user_id` (`user_id`),
  KEY `idx_ai_user_data_scope_scope_type` (`scope_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台用户数据范围表';

CREATE TABLE IF NOT EXISTS `ai_user_relation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` varchar(50) NOT NULL COMMENT '用户ID',
  `manager_user_id` varchar(50) NOT NULL COMMENT '上级用户ID',
  `relation_type` varchar(20) NOT NULL DEFAULT 'DIRECT' COMMENT '关系类型',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_ai_user_relation_user_id` (`user_id`),
  KEY `idx_ai_user_relation_manager_user_id` (`manager_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台用户上下级关系表';

INSERT INTO `ai_user_data_scope` (`user_id`, `scope_type`, `create_time`, `update_time`)
SELECT
  u.`user_id`,
  CASE
    WHEN EXISTS (
      SELECT 1
      FROM `ai_user_role` ur
      INNER JOIN `ai_role` r ON r.`role_id` = ur.`role_id`
      WHERE ur.`user_id` = u.`user_id`
        AND r.`role_code` = 'super_admin'
    ) THEN 'ALL'
    ELSE 'SELF'
  END,
  NOW(),
  NOW()
FROM `ai_user` u
WHERE COALESCE(u.`auth_provider`, '') <> 'MT_H5'
  AND NOT EXISTS (
    SELECT 1
    FROM `ai_user_data_scope` s
    WHERE s.`user_id` = u.`user_id`
  );
