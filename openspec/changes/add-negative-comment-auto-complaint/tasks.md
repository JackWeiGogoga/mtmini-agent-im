## 1. 数据库与配置

- [x] 1.1 新增 `mt_comment_complaint` 表迁移，保存 `shop_id`、`mt_comment_id`、`comment_id`、申诉状态、`report_type_code`、申诉文本、模型、prompt、token、算力、请求/响应、错误、耗时和时间字段。
- [x] 1.2 为 `mt_comment_complaint` 增加按评价幂等的唯一索引和按门店/状态查询的普通索引。
- [x] 1.3 如需快速读取当前状态，在 `mt_comment` 增加申诉快照字段：`complaint_status`、`complaint_report_type_code`、`complaint_report_content`、`complaint_at`、`complaint_error`。
- [x] 1.4 更新 `backend/src/main/resources/db/full_schema.sql`，同步新增表、字段和索引。
- [x] 1.5 增加差评自动投诉 dry run、任务重试策略、模型和兜底算力相关配置项，并生成/更新 Spring 配置元数据。

## 2. Prompt 与申诉类型

- [x] 2.1 将 `comment_report_no_pic.prompt` 与 `comment_report_with_pic.prompt` 合并为一个申诉内容 prompt，使用 `pictureContext` 或等价变量表达有图/无图差异。
- [x] 2.2 新增申诉类型判断 prompt 资源文件，要求模型输出可解析 JSON 并限制为白名单类型。
- [x] 2.3 初始化 `COMMENT_REPORT_TYPE` 与 `COMMENT_REPORT_CONTENT` 系统提示词配置，并更新 `SystemPromptKeyConstant`。
- [x] 2.4 新增申诉类型枚举或常量表，包含 31、32、34、35、36、37、39、40、41、43、44、46、47 的编码、名称和类别。
- [x] 2.5 补充 prompt 渲染和申诉类型解析测试，覆盖有效类型、无效类型、模型返回非 JSON 和默认 47 兜底。

## 3. 差评判定修正

- [x] 3.1 调整评论类型判定逻辑，优先使用 `product_score` 与 `packing_score` 的最低有效分，不再优先使用 `food_comment_score`。
- [x] 3.2 确认自动回复、H5 日志、评论分析和差评申诉共用一致的好中差判定逻辑，避免各处重复实现。
- [x] 3.3 补充单元测试覆盖 0-2 差评、3 中评、4-5 好评、单字段有效、评分缺失和 `food_comment_score=0` 误判防护。

## 4. 申诉生成与持久化

- [x] 4.1 新增 `MtCommentComplaint` 实体、Mapper、XML、DTO 和状态枚举，覆盖创建、按 `shop_id/comment_id` 查询、标记生成失败、标记提交成功和标记提交失败。
- [x] 4.2 新增差评自动投诉 service，校验评价归属门店、开关状态、授权 token、评分类型和已有成功申诉状态。
- [x] 4.3 实现申诉类型判断模型调用，保存模型、prompt key/version、token usage、兜底标记和错误摘要。
- [x] 4.4 实现申诉文本生成模型调用，组装评价内容、追评、评分、标签、配送超时说明、图片摘要和图片上下文。
- [x] 4.5 接入现有敏感词/合规处理能力；申诉文本无法合规通过时标记失败且不调用美团。
- [x] 4.6 按现有算力体系记录申诉模型调用消耗，使用 `COMMENT_AUTO_COMPLAINT:{commentId}` 或等价 requestNo 保证幂等扣费。

## 5. 异步任务与美团接口调用

- [x] 5.1 新增 `COMMENT_AUTO_COMPLAINT` 异步任务类型、payload、worker 分发逻辑和执行策略配置。
- [x] 5.2 在自动回复成功且评价类型为 `BAD` 后入队差评自动投诉任务，`bizKey` 使用 `comment-complaint:{commentId}` 或等价幂等键。
- [x] 5.3 使用美团官方文档或当前 SDK 确认申诉接口 request 类、必填字段和响应字段，并实现当前门店 token 调用。
- [x] 5.4 实现申诉接口 dry run：开启时不调用美团，不标记真实成功，并按任务策略重排或记录跳过。
- [x] 5.5 处理美团申诉成功、失败、SDK 异常和超时，分别更新本地申诉状态与错误摘要。
- [x] 5.6 确保申诉失败不会影响 `auto_reply_status=SUCCESS`，重复任务不会重复提交已成功申诉。

## 6. 文档与验证

- [x] 6.1 更新 `docs/美团接口调用索引.md`，新增美团评价申诉接口的文档地址、接口地址、SDK request、本地入口、dry run、请求参数、落库和幂等说明。
- [x] 6.2 运行差评判定、申诉类型解析、申诉 prompt 渲染、申诉 service 幂等和 dry run 相关单元测试。
- [x] 6.3 运行后端编译：`/Users/jackwei/data/apache-maven-3.9.14/bin/mvn -f backend/pom.xml compile`。
- [x] 6.4 手工或集成验证：差评且开关开启会入队并提交申诉，非差评/开关关闭/评分异常不会触发申诉。
- [x] 6.5 手工或集成验证：同一评价重复执行不会重复模型扣费或重复调用美团申诉接口。
