## ADDED Requirements

### Requirement: 系统必须保留两方会话发送基础能力
系统 SHALL 提供独立于评价业务的美团 IM 两方会话发送客户端，使用 `SendImMsgWmoperRequest` 调用 `/wmoper/ng/im/msg/send`，并固定 `biz_type=1`。

#### Scenario: 后续回复流程发送两方会话消息
- **WHEN** 调用方提供有效门店 token、唯一三方消息 ID、消息内容和两方会话上下文
- **THEN** 客户端构造 `biz_type=1` 的发送请求
- **AND** 客户端不要求 `comment_id`、`group_id` 或评价实体

### Requirement: 两方会话发送必须固定商家来源和会话字段
系统 MUST 固定 `msg_source=1`，并发送调用方提供的 `msg_id`、10 位 `cts`、`order_id`、`open_user_id`、`scene_tag` 和 `scene_name`；回复标记固定为已回复语义。

#### Scenario: 构造商家回复请求
- **WHEN** 调用方发送一条普通两方会话回复
- **THEN** 请求包含 `msg_source=1`、`scene_tag=-1` 和 `has_reply=2`
- **AND** 请求保留原会话的 `order_id` 与 `open_user_id`

#### Scenario: 任务重试发送同一回复
- **WHEN** 后续发送任务因结果未知而重试
- **THEN** 调用方复用同一个三方 `msg_id`
- **AND** 客户端不在内部生成新的消息 ID 或自动重试

### Requirement: 消息类型必须遵守两方会话官方范围
系统 SHALL 将接收类型定义为 `1` 文本、`2` 图片、`3` 其他、`17` 卡片，并只允许发送接口声明的 `1` 文本、`2` 图片和 `3` 其他。

#### Scenario: 接收图片或卡片消息
- **WHEN** `/imMsg` 收到 `msg_type=2` 或 `msg_type=17`
- **THEN** 系统保存准确类型和解密后的不透明内容
- **AND** 系统不因媒体类型丢弃消息

#### Scenario: 尝试发送卡片或未知类型
- **WHEN** 调用方请求发送 `msg_type=17` 或其他未声明数值
- **THEN** 客户端在调用美团前拒绝请求

#### Scenario: 处理视频内容
- **WHEN** 上游把视频内容作为 `msg_type=3` 其他消息推送
- **THEN** 系统可按其他消息原样入库
- **AND** 系统不宣称已实现视频识别、下载、播放或独立视频发送类型

### Requirement: 发送正文必须使用通用 IM 密钥加密
系统 MUST 使用 `mt.im.secret` 对待发送正文执行 AES/CBC/PKCS5Padding 加密，密码和 IV 均使用 16 字节 IM 密钥，输出 Base64 后写入 `msg_content`。

#### Scenario: 发送文本或媒体描述内容
- **WHEN** 调用方提供非空明文内容
- **THEN** 美团请求中的 `msg_content` 为可由同一服务解密的 Base64 密文
- **AND** 请求不包含原始明文

### Requirement: 两方会话文本发送前必须执行通用敏感词检测
系统 MUST 保留去评价化的敏感词分类词库、NFKC 归一化、空白与标点规避检测、命中类别和最高严重级别计算，并在 `msg_type=1` 文本加密与外部调用前使用该能力。

#### Scenario: 待发送文本命中敏感词
- **WHEN** 两方会话文本在归一化后命中启用词条
- **THEN** 客户端返回 `SENSITIVE_WORD_HIT` 安全失败
- **AND** 安全摘要只包含命中类别、命中数和最高严重级别，不包含消息正文或具体词条
- **AND** 客户端不加密正文且不调用美团发送接口

#### Scenario: 敏感词检测被显式关闭
- **WHEN** `mt.sensitive-word.enabled=false`
- **THEN** 检测返回未命中并允许其他合法发送校验继续执行

#### Scenario: 发送图片或其他不透明载荷
- **WHEN** 调用方发送 `msg_type=2` 图片或 `msg_type=3` 其他消息
- **THEN** 客户端不把协议 URL、JSON 或 Base64 载荷作为自然语言执行敏感词匹配
- **AND** 调用方仍须保证媒体内容本身合规

### Requirement: 敏感词能力必须继续保护通用知识库
系统 SHALL 在通用门店 QA 保存以及知识文档切片向量化前复用同一个敏感词服务，不得因删除评价回复业务而移除该基础能力。

#### Scenario: QA 或文档命中敏感词
- **WHEN** 门店知识文本命中启用词条
- **THEN** 系统在持久化 QA 或生成文档向量前拒绝对应内容
- **AND** 错误和日志只记录门店、安全类别与命中数，不记录完整正文

### Requirement: 发送客户端必须单次调用并脱敏
客户端 MUST NOT 自动重试，也 MUST NOT 在普通日志、异常或返回摘要中输出 access token、IM 密钥、消息明文或完整密文。

#### Scenario: 美团发送接口返回失败
- **WHEN** 写接口返回业务失败、空响应或 SDK 异常
- **THEN** 客户端返回包含阶段、错误码和 traceId 的安全结果
- **AND** 客户端只调用一次美团写接口且不执行任何状态查询

### Requirement: 接收回调不得自动触发发送
发送客户端的存在 MUST NOT 改变 `/imMsg` 的纯入库边界。

#### Scenario: 收到用户未回复消息
- **WHEN** `/imMsg` 保存 `biz_type=1`、`msg_source=2`、`has_reply=1` 的消息
- **THEN** 回调只完成验签、解析、落库和响应
- **AND** 回调不调用两方会话发送客户端
