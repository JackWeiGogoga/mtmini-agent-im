ALTER TABLE `user_behavior_event`
  ADD COLUMN `client_event_id` varchar(96) DEFAULT NULL COMMENT '客户端事件唯一ID' AFTER `event_type`,
  ADD COLUMN `session_id` varchar(96) DEFAULT NULL COMMENT '客户端应用会话ID' AFTER `client_event_id`,
  ADD UNIQUE KEY `uk_user_behavior_event_client_event_id` (`client_event_id`),
  ADD KEY `idx_user_behavior_event_session_time` (`session_id`,`event_time`);
