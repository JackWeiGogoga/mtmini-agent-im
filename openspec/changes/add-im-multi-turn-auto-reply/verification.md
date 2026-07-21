# 验证记录

## 自动化场景

| 场景 | 验证方式 | 结果 |
| --- | --- | --- |
| `/imMsg` 入库后创建 Conversation、Session、自动回复占位和幂等任务 | `MtImMessageIngestionServiceTest`、`MtImConversationServiceTest`、`MtImAutoReplyEnqueueServiceTest` | 通过 |
| 重复回调及美团商家消息回推合并内部 AI 消息 | `MtImMessageIngestionServiceTest` | 通过 |
| 当前 Session 最近 100 个用户轮次、字符预算、未发送 AI 草稿排除 | `ImAutoReplyContextServiceTest` 与 Mapper 条件审计 | 通过 |
| 门店知识存在时向量召回、无知识跳过、异常重试 | `ImAutoReplyRagServiceTest`、编排测试 | 通过 |
| 连续用户消息淘汰、人工回复跳过、Conversation 锁重排 | `MtImAutoReplyOrchestrationServiceTest` | 通过 |
| 敏感词改写、usage 累加、固定安全兜底 | `MtImAutoReplyOrchestrationServiceTest` | 通过 |
| 实际 token 算力幂等记账、算力不足关停门店开关 | `MtImAutoReplyStateServiceTest`、`MtPowerConsumeServiceTest` | 通过 |
| 发送失败复用相同正文和出站 `msg_id`，重试耗尽同步业务 `DEAD` | `MtImAutoReplyOrchestrationServiceTest`、`MtAsyncTaskWorkerTaskTest` | 通过 |
| 管理/H5 门店范围、越权拒绝、复合游标跨 Session 历史 | `ImReplyLogQueryServiceTest` | 通过 |
| 管理后台双 Tab、总控台日志抽屉和会话滚动锚点 | TypeScript、ESLint、生产构建 | 通过 |
| H5 日志卡片、分页、完整会话及向上加载 | uni-app H5 生产构建 | 通过 |

## 外联灰度检查

自动回复全局配置默认关闭。本地测试不使用生产美团、模型、Redis 和算力账户凭据，因此真实外联需在灰度环境按顺序确认：

1. 执行数据库结构与权限初始化，确认新表、索引、提示词和菜单生效。
2. 选择测试门店开启 `MT_IM_AUTO_REPLY_ENABLED` 与门店开关，发送两条连续顾客文本，确认只产生一条回复。
3. 分别验证无知识和有知识门店，检查 RAG 元数据、实际 token 算力记录和正文安全日志边界。
4. 模拟发送超时后确认相同 `outbound_message_id` 重试，美团回推后统一时间线不重复。
5. 等待超过 Session 空闲阈值后再次发消息，确认创建新 Session；管理后台和 H5 向上滚动仍能加载旧 Session。
6. 验证管理员全局、总控台可见门店和 H5 当前门店三种数据范围，确认无正文导出入口。
