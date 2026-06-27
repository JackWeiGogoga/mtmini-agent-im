## 1. 数据库与默认配置

- [x] 1.1 新增 `system_prompt_config` 表迁移，包含 `prompt_key` 唯一索引、内容、默认资源路径、变量说明、启用状态、版本和审计字段。
- [x] 1.2 初始化评价回复、评价图片识别、评论日志分析相关 prompt key，并为 `COMMENT_LOG_ANALYSIS` 绑定 `prompts/comment_log.prompt`。
- [x] 1.3 为评论分析结果新增持久化字段或独立生成表，保存状态、分析文案、模型、prompt 版本、token、算力、错误和生成时间。
- [x] 1.4 更新 `backend/src/main/resources/db/full_schema.sql`，同步新增表、字段、索引和菜单权限初始化数据。
- [x] 1.5 新增管理后台菜单与权限 SQL，配置 `system:prompt:list`、`system:prompt:query`、`system:prompt:edit`。

## 2. 后端系统提示词配置

- [x] 2.1 新增系统提示词实体、Mapper、XML、参数对象和 VO，覆盖列表、详情、更新、启停、恢复默认所需字段。
- [x] 2.2 实现 `SystemPromptService`，支持按 `prompt_key` 读取启用配置、回退资源文件、代码兜底和 `{{variableName}}` 渲染。
- [x] 2.3 新增 `/api/system/prompts` 管理接口，并使用 `@RequiresPermission` 保护列表、详情和编辑操作。
- [x] 2.4 将评价回复内容生成和图片识别 prompt 构建改为通过 `SystemPromptService` 获取，保留原代码默认内容作为兜底。
- [x] 2.5 为 prompt 解析和变量渲染补充单元测试，覆盖数据库配置、资源文件兜底、停用配置和未知变量日志场景。

## 3. 评论日志分析生成

- [x] 3.1 优化 `backend/src/main/resources/prompts/comment_log.prompt`，明确商家运营分析角色、输入变量、500 字以内输出、表情数量和禁止承诺边界。
- [x] 3.2 新增评论日志分析 service，组装评价评分、标签、配送时效、首评、追评、图片摘要、AI 回复、评论类型等变量并调用大模型。
- [x] 3.3 保存评论日志分析结果，成功时记录分析文案、模型、prompt key/version、token usage、算力消耗和生成时间。
- [x] 3.4 处理分析失败状态，记录错误原因且不影响 `auto_reply_status=SUCCESS` 的自动回复主链路。
- [x] 3.5 在自动回复成功落库后触发分析生成，确保已有成功分析的评价不会重复生成或重复扣算力。
- [x] 3.6 调整 `MtH5CommentReplyLogService`，`analysisText` 优先返回真实分析文案，缺失时保留“AI 分析结果”兜底。

## 4. 管理后台页面

- [x] 4.1 新增 `frontend/src/services/system-prompt-api.ts` 和对应类型定义，封装列表、详情、更新、启停、恢复默认接口。
- [x] 4.2 新增系统提示词配置页面，使用表格展示 prompt key、名称、状态、版本、更新时间和备注。
- [x] 4.3 实现编辑弹窗或抽屉，支持修改内容、备注、启用状态，并展示变量说明和默认资源路径。
- [x] 4.4 实现恢复默认操作，提交前二次确认，成功后刷新列表并提示结果。
- [x] 4.5 在 `frontend/src/router/index.tsx` 增加路由和权限包裹，并确认菜单路径、权限字符串与后端初始化数据一致。

## 5. 验证

- [x] 5.1 运行后端单元测试或最接近的 prompt/评论分析服务测试。
- [x] 5.2 运行后端编译：`/Users/jackwei/data/apache-maven-3.9.14/bin/mvn -f backend/pom.xml compile`。
- [x] 5.3 运行前端 `pnpm lint` 和 `pnpm typecheck`。
- [ ] 5.4 手工验证管理后台系统提示词页面的列表、编辑、启停、恢复默认和无权限访问。
- [ ] 5.5 手工验证新增评价自动回复成功后生成分析文案，H5 日志页展示真实 `analysisText`。
- [ ] 5.6 手工验证重复处理同一评价不会重复生成成功分析或重复扣算力。
- [x] 5.7 核对本次没有新增或修改美团外部接口调用；如实现中实际改动美团调用，则同步更新 `docs/美团接口调用索引.md`。
