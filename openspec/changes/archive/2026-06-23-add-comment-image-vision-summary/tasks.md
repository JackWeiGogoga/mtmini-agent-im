## 1. 数据模型与配置

- [x] 1.1 新增数据库迁移脚本，创建 `mt_comment_image_summary` 表并设置 `comment_id` 幂等约束
- [x] 1.2 新增 `MtCommentImageSummary` 实体、Mapper 接口和 XML
- [x] 1.3 在 `MtProperties.Knowledge` 和 `application.yml` 中新增图片识别配置项
- [x] 1.4 补充本地环境变量说明，包括 TokenHub 视觉模型 API key、模型名、最大图片数量和开关
- [x] 1.5 明确 `summary_text`、`summary_json`、token usage 和 `power_amount` 字段的保存与查询逻辑

## 2. 图片识别服务

- [x] 2.1 新增图片 URL 解析工具，支持逗号分隔、去空值、去重和最大数量截断
- [x] 2.2 新增 `CommentImageVisionService`，负责按评价获取或生成图片摘要
- [x] 2.3 使用 OpenAI 兼容 Chat Completions 调用 TokenHub `qwen3.5-flash`
- [x] 2.4 设计图片识别 prompt，要求输出结构化 JSON 并限制只描述可见事实
- [x] 2.5 使用 `JsonUtil` 解析模型返回，保存成功摘要、失败原因和原始异常信息
- [x] 2.6 实现图片识别失败降级文案，不阻塞自动回复主流程
- [x] 2.7 读取模型返回 usage，按 RAG 相同规则扣减图片识别算力并保证幂等
- [x] 2.8 模型 usage 缺失时使用 `vision-fallback-power-amount` 兜底扣费

## 3. 回复链路接入

- [x] 3.1 在 `MtCommentReplyContentServiceImpl` 的评论上下文构建中加入图片摘要
- [x] 3.2 确保图片摘要同时参与 embedding 查询文本和最终文本模型 prompt
- [x] 3.3 确保无图片、识图关闭、API key 为空、识图失败时都能继续走原有回复逻辑
- [x] 3.4 确保同一 `comment_id` 重复回复任务复用已有成功图片摘要
- [x] 3.5 在回复生成结果或日志中记录图片摘要命中状态，便于排查

## 4. 管理后台测试回复

- [x] 4.1 扩展测试回复接口参数，支持 `commentPictures` 图片 URL 数组和 `imageSummaryText`
- [x] 4.2 增加管理后台测试图片上传接口或复用现有 COS 上传能力，返回可访问图片 URL
- [x] 4.3 管理后台“测试回复效果”Tab 增加图片 URL 数组输入、上传多张图片和模拟图片摘要输入
- [x] 4.4 测试结果展示图片识别状态、摘要文本、token usage、扣减算力和失败原因
- [x] 4.5 UI 提示真实图片 URL 或上传图片测试会调用多模态模型并扣算力，模拟摘要不会调用模型

## 5. 测试与验证

- [x] 5.1 为图片 URL 解析、摘要复用、失败降级和幂等扣费补充后端单元测试
- [x] 5.2 为 `MtCommentReplyContentServiceImpl` 补充图片摘要进入上下文的单元测试
- [x] 5.3 为测试回复接口补充参数校验和模拟摘要场景测试
- [x] 5.4 执行后端编译和相关单元测试
- [x] 5.5 执行管理后台 `pnpm typecheck` 和 `pnpm lint`
