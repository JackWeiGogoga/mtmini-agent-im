## Context

当前代码同时存在两套方向相反的 IM 能力：一套以 `mt_comment` 为核心，通过评价查询、自动回复、差评申诉和评价匿名群聊主动联系顾客；另一套仅在 `/api/callbacks/meituan/imMsg` 解密并打印两方会话消息。新的产品方向只处理商家与顾客的 IM 两方会话，评价业务不再存在。

项目将使用全新数据库，不需要迁移、兼容或备份旧评论数据。代码仍需保留门店授权、access token、关闭订单推送、门店权益、算力、支付、异步任务、LLM 基础设施、系统提示词、对象存储、Embedding、向量检索和商家知识库。评价业务对这些通用能力存在命名和依赖污染，不能按文件名前缀直接整批删除。

美团官方协议要求：

- 门店 IM 状态通过 `/wmoper/ng/im/setPoiIMStatus` 设置，`imStatus=1` 表示开启。
- 智能回复机器人通过 `/wmoper/ng/im/autosend/setAutoSendConfigInfo` 设置，`allStatus=[{"type":0,"switchStatus":0}]` 表示关闭所有开关。
- 两方会话消息通过 `application/x-www-form-urlencoded` 推送，外层包含统一回调字段，`message` 中的 `push_content` 可能表现为 JSON 对象或 JSON 字符串。
- 机器人开关查询接口为付费接口，本变更禁止为授权后验证而调用该查询接口。

## Goals / Non-Goals

**Goals:**

- 建立完全不含评价表、评价任务、评价页面和评价配置的新项目基线。
- 保留并去评价化通用知识库、算力、授权、权益、支付和任务基础设施。
- 授权成功后异步、幂等地开启门店 IM、关闭美团机器人并开启本地 IM 自动回复开关。
- 对所有 IM 两方会话消息完成验签、解析、门店映射、解密状态记录和幂等落库。
- 保留一个不与评价实体耦合的 `biz_type=1` 两方会话发送客户端，为后续回复任务提供基础调用能力。
- 保留并去评价化既有敏感词分类词库、文本归一化、检测与知识内容拦截能力，作为未来 IM 回复生成与发送的通用合规底座。
- 让回调快速、可靠响应，并为以后按门店和用户重建会话上下文提供数据索引。
- 明确敏感消息内容、access token、签名密钥和 IM 密钥的日志边界。

**Non-Goals:**

- 不迁移、备份、归档或兼容旧评论数据库和评论业务数据。
- 不调用付费的机器人开关查询接口，不对设置成功结果增加远端复核。
- 不在本变更生成 IM 回复、不扣除消息回复算力、不自动触发两方会话发送，也不实现发送任务、回复状态机或重试编排。
- 不新增会话表、自动回复记录表、回复状态机、人工审核、消息重发或消息日志页面。
- 不恢复旧 OpenSpec 规格或变更历史。

## Decisions

### 1. 以全新数据库基线替代旧评论迁移

`backend/src/main/resources/db/full_schema.sql` 直接定义新项目需要的最终结构，不包含 `mt_comment`、`mt_comment_reply_generation`、`mt_comment_image_summary`、`mt_comment_complaint`、`mt_comment_im_follow_up`、旧评论设置表或其索引。评论专属迁移脚本从当前项目移除，避免新环境误执行。

保留通用基础表，并新增：

- `mt_im_auto_reply_setting`
- `mt_im_auto_reply_setting_history`
- `mt_im_message`

不编写旧表 `ALTER/RENAME/COPY/DROP` 数据迁移，因为部署目标只有全新数据库。与保留能力相关的表允许同步更名和精简，代码与新 schema 一次性切换。

替代方案是保留评论表和字段以降低修改量，但这会继续污染实体、查询、权限和页面语义，且新库没有兼容价值，因此不采用。

### 2. 通过依赖边界清理评价业务，而不是按名称机械删除

完整删除评价查询、回复生成、图片理解、日志分析、差评申诉、评价匿名群聊、评价处理日志和对应任务分支。`MtCommentImClient` 固定使用 `bizType=4`、`commentId/groupId`，属于评价匿名群聊，不能作为未来两方会话客户端保留。

保留下列通用能力并完成去评价化：

- `mt_async_task`、worker 领取/重试/回收和按门店公平调度。
- 算力账户、充值、消费明细和支付回调；只删除评论任务的计费入口，不删除通用账务能力。
- `system_prompt_config` 表和管理能力；只删除评论 prompt key、资源文件和初始化行。
- COS、文档解析、Embedding、向量存储和门店知识检索。
- 敏感词分类词库、NFKC 归一化检测、严重级别计算和知识内容拦截；类名、配置前缀和资源文件改为通用语义。
- 门店、授权 token、权益、订单推送控制和订阅订单能力。

`COMMENT_*` 与 `AUTH_COMMENT_BACKFILL` 等已废弃任务类型从枚举、配置、worker 和初始化数据中移除。由于使用全新数据库，不需要处理旧任务行。

### 3. 将商家知识库重建为通用门店知识能力

新 schema 使用通用的知识条目、文档和切片表，例如 `mt_knowledge_item`、`mt_knowledge_document`、`mt_knowledge_chunk`。只保留门店级 QA 与文档来源，删除 `OFFICIAL/TEMPLATE` 评价回复模板、`GOOD/NEUTRAL/BAD` 评论情感字段和评价专属图片测试字段。

知识检索继续按 `shop_id`、启用状态和余弦相似度过滤，现阶段不增加消息场景分类。文档解析任务继续使用通用 `KNOWLEDGE_DOCUMENT_PARSE`，Embedding 和向量二进制结构保持兼容。

管理后台和 H5 保留知识库 CRUD、上传、解析状态、切片管理与门店隔离；删除评价回复测试、差评申诉测试及其对 `MtComment` 的构造依赖。权限改用通用 `knowledge:*` 命名。

### 4. 本地 IM 自动回复设置使用新表和显式状态

`mt_im_auto_reply_setting` 每个门店唯一，仅包含 `auto_reply_enabled` 及创建、更新时间；字段默认值为 `0`。缺少设置行时同样解释为关闭，只有授权后 IM 初始化成功或用户/管理员显式开启才变为开启。

`mt_im_auto_reply_setting_history` 保存版本号、开关快照、来源、操作人、原因和前后 JSON。来源至少包含 `AUTH`、`H5`、`ADMIN`、`SYSTEM`。快照中不得再出现差评处理字段。

算力不足时保留现有自动关停能力，但重命名为 IM 自动回复关停，只修改本地开关并记录 `POWER_BALANCE_NOT_ENOUGH`。关闭本地开关不关闭远端门店 IM、不重新打开美团机器人，也不停止消息入库。

商家重新授权时按照需求重新开启本地开关，即使此前由 H5 或管理员关闭。

### 5. 授权后使用一个有序的 `AUTH_CONFIGURE_IM` 异步任务

门店授权核心状态和 token 保存成功后，继续分别入队关闭订单推送、补齐 `poiId`、同步权益等现有任务，并用授权事件键入队 `AUTH_CONFIGURE_IM`。删除授权后评价回补任务。

`AUTH_CONFIGURE_IM` 按固定顺序执行：

1. 使用当前门店 access token 调用 `SetPoiImStatusRequest`，设置 `imStatus=1`。
2. 调用 `SetAutoSendConfigInfoRequest`，设置单个 `ImTypeSwitchStatusParam(type=0, switchStatus=0)`。
3. 两次调用均明确成功后，以 `AUTH` 来源 upsert 本地 `auto_reply_enabled=1`。

任务不调用 `GetAutoSendConfigInfoRequest` 或其他付费查询接口。对设置接口的明确业务失败、空响应和 SDK 异常按任务策略重试；授权和 token 不回滚。若第一步已经成功、第二步失败，重试时允许再次执行第一步，因为相同设置值是幂等操作。

任务 payload 只保存 `shopId`，执行时重新读取门店和当前有效 token，并校验作用域，避免持久化 access token 和跨店调用。授权事件 biz key 包含门店和本次 token 指纹，使同一授权事件幂等、重新授权可再次初始化。

替代方案是拆成三个互不相关任务，但会产生本地开关先开启、远端状态未完成的竞态，因此不采用。

### 6. IM 配置客户端只封装两个免费设置调用

新增通用门店 IM 配置客户端，使用 SDK typed request：

- `com.meituan.sdk.model.wmoperNg.im.setPoiImStatus.SetPoiImStatusRequest`
- `com.meituan.sdk.model.wmoperNg.im.setAutoSendConfigInfo.SetAutoSendConfigInfoRequest`
- `com.meituan.sdk.model.wmoperNg.im.setAutoSendConfigInfo.ImTypeSwitchStatusParam`

客户端只根据设置接口返回的 `MeituanResponse.isSuccess()`、业务 code、data 和异常判断本次调用结果，返回脱敏摘要供任务日志使用。它不得自动重试、不得查询当前开关、不得输出 access token 或完整外部请求。

这条限制仅针对授权配置客户端。两方会话发送使用独立客户端，不得混入授权初始化流程，也不得调用任何状态查询接口。

### 7. `/imMsg` 采用统一回调信封并兼容两种 `push_content` 表现

Controller 继续暴露 `POST /api/callbacks/meituan/imMsg`，只接收 `application/x-www-form-urlencoded`。处理流程为：

1. 读取全部单值公共参数，使用 `SignerUtil.getSign(signKey, paramsWithoutSign)` 计算 SHA1 签名。
2. 校验 `sign`、`developerId` 和 `businessId`；消息内部样例中的 `sig` 只作为业务字段保存，不能替代外层 `sign`。
3. 将 `message` 解析为 JSON；支持 `push_content` 为对象或经过转义的 JSON 字符串。
4. 先按外层 `callback_msg_id` 幂等创建记录，再解析并补齐内部消息字段。
5. 使用通用 `mt.im.secret` 解密 `msg_content`，保存原始密文、可用的明文、内容 hash 和解密状态；应用日志不输出正文。
6. 通过内部 `app_poi_code` 优先匹配 `mt_shop.epoi_id`，外层 `ePoiId`、`opBizCode` 作为辅助信息。
7. 提交数据库事务后返回规范 JSON。

兼容用户提供的历史样例：`message` 内可能额外包含 `sig`、`app_id`、`app_poi_code` 和 `timestamp`，且 `push_content` 为字符串。未知字段保存在安全原始 JSON 中，不阻塞已知字段解析。

### 8. `mt_im_message` 同时承担回调 inbox 与消息事实表

第一阶段不额外创建 conversation 或 reply 表。`mt_im_message` 主要字段为：

- 关联：`id`、可空 `shop_id`。
- 外层回调：`callback_msg_id`、`callback_msg_type`、`developer_id`、`business_id`、`op_biz_code`、`epoi_id`、`callback_timestamp`、`sign_verified`。
- 业务信封：`biz_type`、`extended_field`、内部 `sig`、内部 `timestamp`。
- 消息：`platform_msg_id`、`app_id`、`app_poi_code`、`msg_type`、`msg_source`、`message_cts`、`order_id`、`open_user_id`、`scene_tag`、`scene_name`、`has_reply`。
- 内容：`msg_content_ciphertext`、`msg_content_plaintext`、`msg_content_hash`。
- 可观测：`parse_status`、`decrypt_status`、安全错误摘要、`raw_message_json`、`raw_push_content_json`、接收与创建更新时间。

外层时间戳按 `bigint` 原值保存，不强制假设秒或毫秒；内部 `message_cts` 按官方 10 位时间戳语义保存。

唯一约束：

- `callback_msg_id` 唯一，用于美团回调重投幂等。
- `(app_id, platform_msg_id)` 唯一，用于 IM 业务消息幂等；内部字段缺失时依然可依赖外层 ID 保存失败记录。

查询索引至少覆盖 `(shop_id, open_user_id, message_cts)`、`(shop_id, msg_source, has_reply, message_cts)`、`order_id` 和接收时间。所有 `biz_type=1` 的商家、用户消息及文本、图片、其他、卡片类型均保存；是否适合自动回复由后续变更决定。

官方两方会话接收类型为 `1` 文本、`2` 图片、`3` 其他、`17` 卡片。官方未定义独立的视频类型，因此系统不得把某个数值擅自解释为视频；`3` 类型的解密正文按不透明内容保存，媒体识别、下载和播放不属于本变更。

### 9. 保留独立的 `biz_type=1` 两方会话发送客户端

删除固定 `biz_type=4`、`commentId/groupId` 的评价匿名群聊客户端后，新增与评价无关的两方会话发送客户端，使用 SDK `SendImMsgWmoperRequest` 调用 `/wmoper/ng/im/msg/send`：

- 固定 `biz_type=1`、`msg_source=1`，不接受调用方覆盖为评价匿名群聊。
- 支持官方发送接口列出的 `msg_type=1` 文本、`2` 图片、`3` 其他；`17` 卡片只保证接收入库，不在文档未声明时擅自发送。
- 调用方提供稳定且唯一的三方 `msg_id`、10 位 `cts`、`order_id`、`open_user_id` 和场景字段，后续任务重试时复用同一 `msg_id`。
- `msg_content` 由客户端使用 `mt.im.secret`、AES/CBC/PKCS5Padding 加密后发送；普通日志和返回摘要不得包含明文、密文、token 或密钥。
- `msg_type=1` 文本在加密前使用通用敏感词服务检测；命中后返回只含类别、命中数和最高严重级别的安全失败结果，不加密也不调用美团。图片与“其他”类型的协议正文按不透明载荷处理，不把 URL、JSON 或 Base64 当作自然语言强行匹配。
- 客户端只执行一次写调用并返回安全结果，不自动重试、不扣算力、不持久化回复状态，也不被 `/imMsg` 回调直接调用。

敏感词词库延续旧项目已配置的分类与词条，但配置从 `comment-reply-sensitive-words` 改为通用 `sensitive-words`，启停与未来最大改写次数收敛到 `mt.sensitive-word`。检测继续执行 NFKC 归一化、忽略空白/控制字符/标点符号并按 HIGH、MEDIUM、LOW 计算最高严重级别。通用知识 QA 和文档切片仍在写入或向量化前调用同一服务，避免污染未来 IM 回复上下文。

旧服务中“命中后调用评论模型改写并选官方评价模板兜底”的编排依赖 `MtComment`、评论情感和官方评价模板，因此不原样恢复；本阶段由发送客户端硬拦截，后续 IM 回复生成变更再使用保留的 `maxRewriteAttempts` 和安全命中结果实现两方会话改写/兜底。

### 10. 回调确认以“已经持久化”为边界

- 验签失败、开发者或业务 ID 不匹配：不写业务消息，返回非 0。
- 新消息持久化成功：返回 `{ "code": 0, "message": "success" }`。
- 外层或内层唯一键重复：按幂等成功返回，不创建第二条记录。
- 数据库瞬时失败且无法确认持久化：返回非 0，使平台可以重试。
- 合法回调已经保存，但消息 JSON、门店匹配或解密失败：记录对应状态和安全错误摘要后返回成功，避免无效重推；后续可离线补处理。

这一策略优先保证不丢消息和不重复入库，同时避免在 HTTP 回调线程执行模型、算力或发送逻辑。

### 11. 前后端只展示当前阶段能够证明的状态

H5 首页改为 IM 消息自动回复服务语义，保留授权、权益、算力、充值、知识库和客服；删除评价统计、评价日志入口和差评开关。管理后台删除评价处理日志与官方回复文案，移除评价数量、差评率和差评回复率，只保留门店、授权、权益、算力与一个 IM 自动回复开关。

本阶段不新增消息列表或自动回复成功统计，因为系统尚未发送回复。已有用户行为分析若记录开关变化，应只记录 `autoReplyEnabled`，不再解析 `negativeCommentAppealEnabled`。

### 12. 文档、配置和测试同步去评价化

根、backend、frontend、h5 的项目说明更新为 IM 消息自动回复方向。`docs/美团接口调用索引.md` 删除评价 API、评价匿名群聊调用，登记两个设置接口和 `/imMsg` 入站协议、验签、落库与门店隔离规则。评论上线清单、评论提示词和评论配置从项目中移除。

测试覆盖删除边界、授权任务顺序与失败重试、禁止付费查询、SDK 字段映射、两方会话发送固定参数与加密、回调验签、双格式解析、全部消息类型、双重幂等、门店未匹配、解密失败、日志脱敏、知识库门店隔离和前后端静态检查。

## Risks / Trade-offs

- [机器人设置接口可能部分未生效，但禁止调用付费查询复核] → 只依据设置接口明确返回判断成功，保存 code/data/traceId 的脱敏摘要；接受无法额外确认远端最终状态的成本取舍。
- [授权任务第二步失败时第一步已经生效] → 重试完整有序流程；两个操作都是设置固定值，可安全重复，且本地开关只在全部明确成功后开启。
- [官方示例与实际日志信封字段不完全一致] → 固定外层统一回调验签，内部 JSON 采用容错 DTO/树解析，兼容对象和字符串 `push_content`，并保留未知字段。
- [IM 正文属于敏感会话数据] → 禁止正文进入普通日志和错误摘要；后台/H5 本阶段不提供消息查询接口；密钥和签名字段不进入响应。
- [合法消息暂时匹配不到门店] → 允许 `shop_id` 为空并标记 `SHOP_UNMATCHED`，原始门店标识仍持久化，避免授权时序导致丢消息。
- [大范围删除容易误伤知识库和算力底座] → 以依赖边界和能力测试为删除依据，先拆通用服务再删除评论实体，最后通过全量编译和三端检查验证。
- [新旧 schema 不兼容] → 本项目明确只使用全新数据库，不提供旧库滚动升级；部署时必须保证应用与新 schema 同版本上线。

## Migration Plan

1. 先完成通用知识库、IM 设置、IM 密钥和任务基础设施的去评价化，使保留能力不再依赖 `MtComment`。
2. 重写 `full_schema.sql`，仅保留基础表和通用知识库表，新增三个 IM 表；删除评论迁移脚本和评论初始化数据。
3. 删除评价后端链路、任务、配置、提示词、菜单权限及测试，并更新 worker 和算力入口。
4. 实现两个美团设置客户端和 `AUTH_CONFIGURE_IM`，保持其他授权后任务不变。
5. 实现 `/imMsg` 验签、兼容解析、消息入库、幂等与错误状态。
6. 更新 H5、管理后台、项目文档和接口索引，执行后端测试/编译、前端 lint/typecheck 与 H5 关键路径检查。
7. 使用全新数据库初始化环境，联调一次授权流程和多种 IM 回调；确认没有评价表、评价任务、评价页面或评价 API 调用。

本变更不提供回退到评论 schema 的方案。首次生产启用前可以回滚应用并重建同一套新数据库；开始接收正式 IM 消息后若需回滚，只能回滚到兼容 `mt_im_message` 的本变更版本，不能切回旧评论应用。

## Open Questions

无。已确认使用全新数据库、所有两方会话消息均入库、授权后不调用付费机器人查询接口，并直接建立全新的 OpenSpec 需求。
