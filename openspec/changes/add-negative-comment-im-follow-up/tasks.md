## 1. 配置、数据库与初始化数据

- [x] 1.1 在 `MtProperties`、配置元数据和所有 `application*.yml` 增加开发者级 `imSecret`、建群 dry run、发送 dry run、dry-run 重排时间和兜底算力配置；两个 dry run 在所有 profile 默认 `true`，兜底算力默认 1000，`COMMENT_AUTO_IM_FOLLOW_UP` 的自动重试次数配置为 0。
- [x] 1.2 新增迁移创建 `mt_comment_im_follow_up`，包含唯一业务关联、总体状态/失败阶段、消息来源与生成审计、算力、群信息、唯一 `message_id/cts`、`create_group_attempted_at`、`send_attempted_at`、脱敏响应、错误、耗时和时间字段。
- [x] 1.3 为 IM 表增加 `shop_id + comment_id`、`message_id` 唯一索引，以及门店/评价/状态/更新时间查询索引；为 `mt_comment` 增加 IM 状态、群 ID、消息时间和错误快照及必要筛选索引。
- [x] 1.4 初始化 `COMMENT_IM_FOLLOW_UP_MESSAGE` 提示词、变量说明、资源路径和默认正文，重复迁移不得覆盖运营已编辑内容。
- [x] 1.5 新增“评价处理日志”菜单及 `comment:processing-log:list/query` 权限并授予既有管理员角色，迁移必须幂等且不覆盖运营自定义角色授权。
- [x] 1.6 同步更新 `backend/src/main/resources/db/full_schema.sql` 的表、字段、索引、提示词、菜单和权限数据。

## 2. IM 持久化与状态机

- [x] 2.1 新增 IM 跟进实体、总体状态/失败阶段/消息来源枚举、Mapper 和 XML，支持按门店评价创建或查询、保存生成审计、同步群信息、成功/失败/跳过和评价快照。
- [x] 2.2 实现 `shop_id + comment_id` 并发创建收敛，保证重复任务只使用同一业务记录。
- [x] 2.3 实现建群 attempt 原子占位：仅 `create_group_attempted_at IS NULL` 时更新成功的 worker 可外呼；已有 attempt 且无群 ID 时直接维持终态，不重置、不重试。
- [x] 2.4 实现发送 attempt 原子占位：先持久化唯一 long `msg_id` 和 10 位 `cts`，仅 `send_attempted_at IS NULL` 时更新成功的 worker 可外呼；已有 attempt 且未成功时不得重发。
- [x] 2.5 扩展 `MtComment`、Mapper 和 XML，原子同步 IM 状态快照且不覆盖自动回复、分析或申诉字段。
- [x] 2.6 补充并发创建、attempt 竞争、重复领取、任务超时回收、进程崩溃后重领、成功跳过与终态失败不外呼的持久化测试。

## 3. 提示词、固定兜底、合规与算力

- [x] 3.1 新增 `prompts/comment_im_follow_up_message.prompt` 和 `SystemPromptKeyConstant.COMMENT_IM_FOLLOW_UP_MESSAGE`，采用 design 中禁止补偿、委婉索取订单和手机号的最终模板。
- [x] 3.2 实现首条消息生成服务，通过 `SystemPromptService.render` 注入 `commentContent/addComment`，记录 provider/model、prompt key/version 和 token usage。
- [x] 3.3 实现单段纯文本清理、Unicode code point 150 字限制、变量残留、Markdown/JSON 和 1 至 2 个表情符号检查。
- [x] 3.4 新增 IM 渠道合规服务：允许礼貌索取订单和手机号；拦截任何补偿/退款/赔付/补发/利益、评价诱导、站外导流、高风险个人信息、攻击和违法内容。
- [x] 3.5 实现固定兜底路径，默认文案采用 design 确定版本；模型调用/输出失败时不重试模型，记录 `FALLBACK_STATIC` 和原因并重新执行格式、合规检查。
- [x] 3.6 使用 `COMMENT_IM_FOLLOW_UP:{commentId}` 唯一 requestNo 接入算力；模型成功按 usage、兜底按配置金额扣费，dry run 重排和重复任务不得重复扣费，扣费失败不得建群。
- [x] 3.7 补充后台配置优先/资源兜底、正常生成、模型异常/超时/空输出、静态兜底、兜底不合规、禁止补偿、允许手机号、格式边界和幂等计费测试。

## 4. AES 与美团 IM 客户端

- [x] 4.1 实现 `MtImMessageCryptoService`：校验 `imSecret` 的 UTF-8 编码恰好 16 字节，以相同字节作为 key/IV，使用 `AES/CBC/PKCS5Padding` 和标准 Base64；缺失或非法时返回不泄密的错误。
- [x] 4.2 使用固定开发测试密钥补充 ASCII、中文、表情、块边界、非法长度和可解密性测试，禁止测试或日志输出生产密钥。
- [x] 4.3 使用 `CreateGroupRequest/Response` 调用 `/wmoper/ng/im/createGroup`，固定 `groupType=1` 和当前 `commentId`，成功必须有 `groupId`，保存群字段和脱敏响应。
- [x] 4.4 使用 `SendImMsgWmoperRequest/PushContent` 调用 `/wmoper/ng/im/msg/send`，固定 `bizType=4`、`msgType=0`、`msgSource=1`、`sceneTag=-1`，映射稳定 `msgId/cts`、当前 `commentId/groupId` 和加密正文。
- [x] 4.5 建群和发送客户端对业务失败、缺失群 ID、SDK 异常、超时和未知结果统一返回可落库终态；不得在客户端层自动重试。
- [x] 4.6 补充请求字段映射、成功、各类失败、单次调用和日志脱敏测试，验证 access token、`imSecret`、完整明文、完整密文和完整 `push_content` 不进入日志。

## 5. 异步触发、dry run 与单次外呼

- [x] 5.1 新增 `COMMENT_AUTO_IM_FOLLOW_UP` 类型和 payload，接入 worker 轮询、分发和 dry-run 重排；显式确保普通失败不进入退避重试。
- [x] 5.2 调整自动回复成功后的差评后置逻辑，分别入队 IM 和申诉，复用现有两个门店开关，入队异常互不阻塞且不改变回复成功状态。
- [x] 5.3 实现执行前校验：评价存在并归属 payload 门店、自动回复成功、仍为 `BAD`、开关开启、权益有效、当前门店 token 有效；不得扫描历史评价。
- [x] 5.4 实现“读取/创建记录 -> 生成或兜底 -> 合规 -> 幂等扣费 -> 建群单次 attempt -> AES -> 发送单次 attempt -> 成功”的状态机，每阶段及时落库。
- [x] 5.5 实现建群 dry run 默认开启：允许生成和扣费，禁止建群、禁止写 attempt/伪造 groupId，保持 `GENERATED` 并无损重排。
- [x] 5.6 实现发送 dry run 默认开启：保留真实 `GROUP_CREATED/groupId`，禁止发送、禁止写 attempt/标记成功，无损重排；关闭后复用已有数据发送一次。
- [x] 5.7 对建群和发送的业务失败、异常、超时、连接中断和未知结果记录终态错误；验证任务 `maxRetryCount=0` 与业务 attempt 双重阻止再次外呼。
- [x] 5.8 补充差评/非差评、开关关闭、权益/token 失效、跨门店、申诉失败不阻塞、IM 失败不回滚、仅新评价、两个默认 dry run 和逐步关闭配置测试。

## 6. 评价处理日志后端

- [x] 6.1 新增列表查询参数、列表 VO、聚合详情 VO 和只读 Controller：`GET /api/system/comment-processing-logs`、`GET /api/system/comment-processing-logs/{commentId}`。
- [x] 6.2 列表以 `mt_comment` 为主表，支持评价时间、门店 ID/名称/ePoiId、评价 ID/关键词、情感、自动回复/申诉/IM 状态筛选，默认评价时间倒序并限制 pageSize。
- [x] 6.3 使用 join 或当前页 ID 批量查询聚合回复、`mt_comment_complaint` 和 `mt_comment_im_follow_up` 摘要，禁止 N+1；未触发链路映射为“未触发/不适用”。
- [x] 6.4 详情返回评价与三条链路完整审计字段，包括生成明文、来源/兜底原因、模型/prompt、token/算力、attempt、群/消息 ID、阶段时间、耗时与错误摘要。
- [x] 6.5 为列表和详情分别应用 `comment:processing-log:list/query` 权限及现有后台数据范围；响应禁止包含 token、`imSecret`、完整密文和完整外部请求载荷。
- [x] 6.6 补充分页上限、组合筛选、全评价覆盖、未触发/不适用映射、聚合详情、数据范围、401/403、无 N+1 和敏感字段缺失测试。

## 7. 评价处理日志管理后台

- [x] 7.1 新增日志类型定义和 API service，覆盖列表查询、分页和详情；沿用统一请求错误处理。
- [x] 7.2 新增 `/comment-processing-logs` 路由和“评价处理日志”菜单页面，按权限控制入口和访问。
- [x] 7.3 实现评价时间、门店、评价 ID/关键词、情感和三条处理状态筛选，以及分页表格、状态标签、关键时间和安全错误摘要。
- [x] 7.4 实现详情抽屉，按“评价内容 / 自动回复 / 差评申诉 / 群聊跟进”分区展示；清楚区分未触发、不适用、处理中、成功和失败。
- [x] 7.5 保持页面只读，不增加人工重试、补建群、重发、编辑或导出；保留总控台原有单店自动回复日志行为。
- [x] 7.6 补充 loading、空数据、接口错误、超长内容、缺省字段和无权限交互检查，并运行 `pnpm lint` 与 `pnpm typecheck`。

## 8. 文档、构建与分阶段联调

- [x] 8.1 更新 `docs/美团接口调用索引.md`，登记两个接口的官方地址、SDK 类、固定参数、默认 dry run、AES 规则、落库、单次真实尝试、计费和门店隔离。
- [x] 8.2 运行提示词/兜底、合规、算力、AES、客户端、状态机、dry run、单次 attempt、跨门店和处理日志相关后端测试。
- [x] 8.3 运行后端编译：`/Users/jackwei/data/apache-maven-3.9.14/bin/mvn -f backend/pom.xml compile`，并运行管理后台 `pnpm lint` 和 `pnpm typecheck`。
- [x] 8.4 在两个 dry run 均为 true 时验证新差评只生成、扣费和落库而不外呼；核对所有评价在新后台页面可检索并能查看三条链路详情。
- [ ] 8.5 先关闭建群 dry run，小范围验证每条评价最多一次建群及 `group_id` 落库；再关闭发送 dry run，验证 AES 解密、群消息内容和每条评价最多一次发送。
- [ ] 8.6 核查生产日志、数据库摘要和后台接口不包含 access token、`imSecret`、完整密文或完整外部请求，记录上线、逐步启用和回滚检查清单。
