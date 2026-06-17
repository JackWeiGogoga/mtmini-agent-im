CREATE TABLE IF NOT EXISTS `mt_h5_guide_state` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `shop_id` bigint NOT NULL COMMENT '店铺ID',
  `guide_code` varchar(64) NOT NULL COMMENT 'H5引导编码',
  `completed` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已完成',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_mt_h5_guide_state_shop_guide` (`shop_id`, `guide_code`),
  KEY `idx_mt_h5_guide_state_guide_code` (`guide_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='H5门店引导状态表';
