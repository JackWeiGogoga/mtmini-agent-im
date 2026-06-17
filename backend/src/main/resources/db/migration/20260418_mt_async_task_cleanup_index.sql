ALTER TABLE `mt_async_task`
  ADD KEY `idx_mt_async_task_status_finished` (`status`, `finished_at`, `id`);
