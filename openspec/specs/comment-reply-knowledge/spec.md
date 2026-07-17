# comment-reply-knowledge Specification

## Purpose
TBD - created by archiving change add-comment-reply-knowledge-rag. Update Purpose after archive.
## Requirements
### Requirement: 评论类型按四维最低有效分判定
系统 SHALL 先忽略 0 分、空值和不在 1-5 范围内的评分，再取 `mt_comment.product_score`、`mt_comment.packing_score`、`mt_comment.delivery_comment_score` 与 `mt_comment.food_comment_score` 的最低有效分作为评论好中差分类依据；1-2 判定为差评 `BAD`，3 判定为中评 `NEUTRAL`，4-5 判定为好评 `GOOD`。

#### Scenario: 差评分类
- **WHEN** 四个评分字段过滤无效值后的最低分为 1 或 2
- **THEN** 系统将该评价的回复评论类型判定为 `BAD`

#### Scenario: 中评分类
- **WHEN** 四个评分字段过滤无效值后的最低分为 3
- **THEN** 系统将该评价的回复评论类型判定为 `NEUTRAL`

#### Scenario: 好评分类
- **WHEN** 四个评分字段过滤无效值后的最低分为 4 或 5
- **THEN** 系统将该评价的回复评论类型判定为 `GOOD`

#### Scenario: 零分与无效评分不参与评级
- **WHEN** 四个评分字段均为 0、空值或不在 1-5 范围内
- **THEN** 系统 MUST 记录异常日志，并 SHALL 暂不进入回复生成或美团回复调用链路

### Requirement: 无效评价仅保留审计记录
系统 SHALL 保存美团返回的评价 `valid` 字段；仅 `valid=1` 的有效评价可参与自动回复、商家端日志展示、评价统计、差评申诉与 IM 跟进，`valid=0` 或其他非 1 值的评价 MUST NOT 进入上述链路。管理后台 SHALL 保留全部评价并展示其有效性，且 MUST NOT 允许重试无效评价。

#### Scenario: 无效评价落库但不处理
- **WHEN** 拉取到 `valid=0` 的评价
- **THEN** 系统 SHALL 保存评价全量字段，但 MUST NOT 将其加入自动回复候选或商家端日志与统计

#### Scenario: 已入队评价执行前失效
- **WHEN** 自动回复、差评申诉或 IM 跟进任务执行时评价的 `valid` 不为 1
- **THEN** 系统 SHALL 跳过任务且 MUST NOT 调用对应美团外部接口

#### Scenario: 管理后台审计无效评价
- **WHEN** 管理员查看评价处理日志
- **THEN** 系统 SHALL 展示有效与无效评价的有效性标记，并 SHALL 禁止对无效评价发起手动重试

### Requirement: 评论敏感类型控制处理范围
系统 SHALL 仅处理 `comment_type` 为 4、5、10 的评价，其他类型仅落库审计，不得进入自动回复、商家端日志、评价统计、差评申诉或 IM 跟进。类型 4 的评价内容可展示且计入商家评分；类型 5 的评价内容必须在商家端屏蔽但计入商家评分；类型 10 的评价内容可展示但不计入商家评分。

#### Scenario: 可展示且计分评价
- **WHEN** 评价 `comment_type=4`
- **THEN** 系统 SHALL 允许展示评价内容、自动处理并计入评价统计

#### Scenario: 敏感内容屏蔽但计分
- **WHEN** 评价 `comment_type=5`
- **THEN** 系统 SHALL 允许自动处理并计入评价统计，但商家端 MUST NOT 返回评价原文

#### Scenario: 展示但不计分
- **WHEN** 评价 `comment_type=10`
- **THEN** 系统 SHALL 允许展示评价内容和自动处理，但 MUST NOT 计入评价统计

#### Scenario: 其他评论敏感类型
- **WHEN** 评价 `comment_type` 不为 4、5、10
- **THEN** 系统 SHALL 仅保留管理后台审计记录，并 MUST NOT 允许自动处理或手动重试

### Requirement: 官方回复知识管理
系统 SHALL 支持后台管理官方回复知识，每条官方知识 MUST 包含标题、评论类型、回复内容和启用状态，并可用于无商户自有知识或智能生成失败时的随机回复。

#### Scenario: 新增官方回复文案
- **WHEN** 后台管理用户提交标题、评论类型和回复内容
- **THEN** 系统创建一条启用的官方回复知识

#### Scenario: 按评论类型随机选择官方回复
- **WHEN** 系统需要为 `GOOD`、`NEUTRAL` 或 `BAD` 类型评价使用官方回复
- **THEN** 系统 SHALL 仅从相同评论类型且启用的官方回复知识中随机选择一条

#### Scenario: 官方文案无可用数据
- **WHEN** 当前评论类型没有启用的官方回复知识
- **THEN** 系统 SHALL 使用配置中的默认回复文案作为最后兜底

### Requirement: 商户手工 QA 知识管理
系统 SHALL 支持商户在 H5 端维护手工 QA 知识，每条知识 MUST 包含标题、评论类型、问题、回答和启用状态，且每个商户最多可维护 500 条。

#### Scenario: 新增手工 QA
- **WHEN** 商户提交标题、评论类型、问题和回答，且当前商户手工 QA 数量未达到 500 条
- **THEN** 系统 SHALL 在当前 `shop_id` 下创建该 QA 知识

#### Scenario: 手工 QA 达到上限
- **WHEN** 商户已有 500 条手工 QA 知识并继续新增
- **THEN** 系统 MUST 拒绝新增并返回明确的业务错误

#### Scenario: 编辑手工 QA
- **WHEN** 商户编辑自己门店下的手工 QA
- **THEN** 系统 SHALL 更新 QA 内容并重建对应检索 chunk 和 embedding

#### Scenario: 删除手工 QA
- **WHEN** 商户删除自己门店下的手工 QA
- **THEN** 系统 SHALL 使该 QA 及其对应检索 chunk 不再参与后续回复检索

### Requirement: 商户文档知识上传
系统 SHALL 支持商户上传 `txt`、`docx`、`xlsx` 文档作为自有知识来源，每个商户最多 10 个文档，单文档最大 20M，生产环境文档文件 SHALL 保存到 COS。

#### Scenario: 上传支持格式文档
- **WHEN** 商户上传大小不超过 20M 的 `txt`、`docx` 或 `xlsx` 文件，且当前商户文档数量未达到 10 个
- **THEN** 系统 SHALL 保存文件并创建解析状态为 `PENDING` 的文档记录

#### Scenario: 文档保存到 COS
- **WHEN** 商户在生产环境上传知识库文档
- **THEN** 系统 SHALL 将原始文件保存到 COS，并在文档记录中保存对象存储信息

#### Scenario: 文档数量达到上限
- **WHEN** 商户已有 10 个可用文档并继续上传
- **THEN** 系统 MUST 拒绝上传并返回明确的业务错误

#### Scenario: 文档超过大小限制
- **WHEN** 商户上传的文档大小超过 20M
- **THEN** 系统 MUST 拒绝上传并返回明确的业务错误

#### Scenario: 上传不支持格式
- **WHEN** 商户上传非 `txt`、`docx`、`xlsx` 格式文件
- **THEN** 系统 MUST 拒绝上传并提示支持的文件格式

### Requirement: 文档异步解析与切片
系统 SHALL 对上传文档进行异步解析、文本清洗、切片、评论类型识别和 embedding 生成，并将解析状态保存到文档记录。

#### Scenario: 文档解析成功
- **WHEN** 异步解析任务成功抽取文档文本并生成至少一个 chunk
- **THEN** 系统 SHALL 保存 chunk、embedding 和来源信息，并将文档状态更新为 `SUCCESS`

#### Scenario: 文档解析失败
- **WHEN** 异步解析任务无法读取、抽取或向量化文档内容
- **THEN** 系统 SHALL 将文档状态更新为 `FAILED` 并记录失败原因

#### Scenario: 无模板文档处理
- **WHEN** 文档内容没有明确标题、星级分类、QA 字段或表头
- **THEN** 系统 SHALL 按普通文本进行清洗和切片，并将无法识别分类的 chunk 标记为 `UNKNOWN`

#### Scenario: Excel 表头识别
- **WHEN** `xlsx` 文件中存在标题、评论类型、问题、回答等近似表头
- **THEN** 系统 SHALL 优先按结构化 QA 解析对应行内容

### Requirement: 文档解析结果纠偏
系统 SHALL 支持商户查看文档解析后的 chunk，并允许对解析结果进行基础纠偏。

#### Scenario: 查看解析结果
- **WHEN** 商户查看自己门店下已解析成功的文档
- **THEN** 系统 SHALL 展示解析 chunk 数量、评论类型分布和 chunk 列表

#### Scenario: 修改 chunk 评论类型
- **WHEN** 商户修改自己门店下文档 chunk 的评论类型
- **THEN** 系统 SHALL 保存新的评论类型，并在后续检索中按新类型使用该 chunk

#### Scenario: 启停或删除 chunk
- **WHEN** 商户停用或删除自己门店下的文档 chunk
- **THEN** 系统 SHALL 使该 chunk 不再参与后续回复检索

### Requirement: 商户自有知识触发 RAG 回复
系统 SHALL 在商户存在任意可用手工 QA 或文档 chunk 时，使用商户自有知识进行 RAG 回复生成；否则使用官方回复文案随机回复。

#### Scenario: 商户无自有知识
- **WHEN** 当前商户没有启用的手工 QA 且没有可用文档 chunk
- **THEN** 系统 SHALL 根据评论类型从官方回复知识中随机选择回复内容

#### Scenario: 商户有手工 QA
- **WHEN** 当前商户存在启用的手工 QA
- **THEN** 系统 SHALL 将该商户视为有自有知识并进入 RAG 回复链路

#### Scenario: 商户有文档 chunk
- **WHEN** 当前商户存在可用文档 chunk
- **THEN** 系统 SHALL 将该商户视为有自有知识并进入 RAG 回复链路

### Requirement: RAG 知识检索
系统 SHALL 基于当前评价内容生成 query embedding，在当前 `shop_id` 下召回候选 chunk，并使用 Java cosine similarity 排序选择 topK 知识片段。

#### Scenario: 按门店和评论类型召回
- **WHEN** 系统为某个商户评价执行 RAG 检索
- **THEN** 系统 MUST 仅查询当前 `shop_id` 下启用的 chunk，并优先召回当前评论类型和 `UNKNOWN` 类型的 chunk

#### Scenario: 手工 QA 权重优先
- **WHEN** 手工 QA chunk 和文档 chunk 的语义相似度接近
- **THEN** 系统 SHALL 优先选择手工 QA chunk 作为大模型上下文

#### Scenario: 候选数量受限
- **WHEN** 当前商户存在大量 chunk
- **THEN** 系统 SHALL 使用配置限制单次候选召回数量，避免无界内存排序

### Requirement: RAG 算力门槛与消费
系统 SHALL 将商户 RAG 回复纳入算力计费，且只有当前门店算力余额大于 10000 时才允许进入 RAG 回复生成链路。

#### Scenario: 算力余额满足门槛
- **WHEN** 当前商户有自有知识，且门店算力余额大于 10000
- **THEN** 系统 SHALL 允许进入 RAG 检索和大模型生成链路

#### Scenario: 算力余额不足门槛
- **WHEN** 当前商户有自有知识，但门店算力余额小于或等于 10000
- **THEN** 系统 MUST 不调用 embedding 或 chat 模型、MUST 不发送官方或默认兜底回复，并 SHALL 将当前评价标记为算力不足跳过
- **AND** 系统 MUST 以系统变更来源关闭当前门店的 AI 回复主开关和差评处理/申诉开关，使后续评价回复、差评申诉及 IM 跟进停止处理

#### Scenario: 生成阶段实际扣费不足
- **WHEN** 门店通过余额门槛预检，但图片识别或回复生成的实际算力扣费失败且原因为余额不足
- **THEN** 系统 MUST 不发送本次评价回复，并 MUST 关闭当前门店的 AI 回复主开关和差评处理/申诉开关

#### Scenario: RAG 生成后记录算力消费
- **WHEN** 系统完成一次 RAG 回复生成
- **THEN** 系统 SHALL 按现有算力体系记录模型调用产生的算力消费明细

### Requirement: 授权成功赠送算力
系统 SHALL 在门店授权成功后幂等赠送 800000 算力。

#### Scenario: 首次授权成功赠送
- **WHEN** 门店首次完成有效授权
- **THEN** 系统 SHALL 为该门店赠送 800000 算力并记录赠送明细

#### Scenario: 重复授权不重复赠送
- **WHEN** 同一门店重复触发授权成功处理
- **THEN** 系统 MUST 不重复赠送授权成功算力

### Requirement: 大模型生成回复
系统 SHALL 使用检索到的商户知识、评价内容和评论类型调用 `deepseek-v4-flash` 生成最终回复，并限制回复内容的安全边界。

#### Scenario: 生成好评回复
- **WHEN** 评论类型为 `GOOD` 且 RAG 检索成功
- **THEN** 系统 SHALL 生成感谢、自然、简短且贴合评价内容的商家回复

#### Scenario: 生成中评回复
- **WHEN** 评论类型为 `NEUTRAL` 且 RAG 检索成功
- **THEN** 系统 SHALL 生成感谢反馈、承认不足并表达改进意愿的商家回复

#### Scenario: 生成差评回复
- **WHEN** 评论类型为 `BAD` 且 RAG 检索成功
- **THEN** 系统 SHALL 生成先表达歉意、再回应问题并表达改进意愿的商家回复

#### Scenario: 禁止无依据承诺
- **WHEN** 大模型生成商家回复
- **THEN** 系统 MUST 约束模型不得编造知识库不存在的信息，且不得承诺退款、赔偿、补发或私下联系，除非命中知识明确提供

### Requirement: 智能生成失败回退
系统 SHALL 在 RAG 检索、embedding 生成或大模型生成失败时回退到官方回复文案随机回复，不得让自动回复主链路因内容生成失败而无回复。

#### Scenario: embedding 生成失败
- **WHEN** 系统无法为评价内容生成 query embedding
- **THEN** 系统 SHALL 记录失败原因并回退到官方回复文案

#### Scenario: RAG 无命中
- **WHEN** 当前商户自有知识检索不到可用 chunk
- **THEN** 系统 SHALL 回退到官方回复文案

#### Scenario: 大模型调用失败
- **WHEN** 大模型接口返回失败、超时或生成内容不可用
- **THEN** 系统 SHALL 记录失败原因并回退到官方回复文案

#### Scenario: 评分异常不回退
- **WHEN** 待回复评价的 `food_comment_score` 为空或不在 0-5 范围内
- **THEN** 系统 MUST 仅记录异常日志，并 SHALL 不因评分异常回退官方回复或默认文案

### Requirement: 自动回复链路接入
系统 SHALL 在 `COMMENT_AUTO_REPLY` 异步任务执行时通过新的回复内容生成策略获取回复内容，再调用美团添加商家回复接口，并保持现有幂等判断。

#### Scenario: 待回复评价执行自动回复
- **WHEN** `COMMENT_AUTO_REPLY` 任务处理未回复且未自动回复成功的评价
- **THEN** 系统 SHALL 先生成回复内容，再调用美团添加商家回复接口

#### Scenario: 已回复评价跳过
- **WHEN** 评价 `reply_status=1` 或 `auto_reply_status=SUCCESS`
- **THEN** 系统 MUST 跳过回复生成和美团回复调用

#### Scenario: 自动回复开关关闭
- **WHEN** 当前门店自动回复开关关闭
- **THEN** 系统 MUST 跳过回复生成和美团回复调用

### Requirement: 知识库门店隔离
系统 MUST 保证商户知识库、文档、chunk 和 RAG 检索均按 `shop_id` 隔离，商户不得访问或使用其他门店知识。

#### Scenario: 商户访问自己的知识
- **WHEN** 商户查询、编辑或删除自己 `shop_id` 下的知识
- **THEN** 系统 SHALL 允许该操作在权限通过后执行

#### Scenario: 商户访问其他门店知识
- **WHEN** 商户查询、编辑或删除不属于自己 `shop_id` 的知识
- **THEN** 系统 MUST 拒绝操作

#### Scenario: 自动回复检索隔离
- **WHEN** 系统为某个评价执行 RAG 检索
- **THEN** 系统 MUST 仅使用该评价所属 `shop_id` 下的商户知识

### Requirement: 回复生成可观测
系统 SHALL 记录每次自动回复内容生成的来源、评论类型、命中知识和失败原因，便于排查回复结果。

#### Scenario: 官方随机回复记录
- **WHEN** 系统使用官方回复文案生成回复
- **THEN** 系统 SHALL 记录回复来源为 `OFFICIAL_RANDOM` 或 `FALLBACK_OFFICIAL`

#### Scenario: RAG 回复记录
- **WHEN** 系统使用商户知识 RAG 生成回复
- **THEN** 系统 SHALL 记录回复来源为 `SHOP_RAG`，并记录命中的知识 chunk 标识和相似度信息

#### Scenario: 生成失败记录
- **WHEN** RAG、embedding 或大模型生成发生失败并触发回退
- **THEN** 系统 SHALL 记录失败阶段和失败原因
