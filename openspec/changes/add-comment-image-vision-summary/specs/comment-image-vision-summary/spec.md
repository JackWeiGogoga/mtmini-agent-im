## ADDED Requirements

### Requirement: 评价图片摘要识别

系统 SHALL 在评价包含 `comment_pictures` 且图片识别能力启用时，调用 TokenHub OpenAI 兼容接口，使用默认模型 `qwen3.5-flash` 对评价图片生成结构化摘要。

#### Scenario: 包含图片的评价触发识别

- **WHEN** 自动回复处理一条包含有效 `comment_pictures` 的评价
- **THEN** 系统 SHALL 最多取配置数量内的图片 URL 调用多模态模型生成图片摘要

#### Scenario: 无图片评价不触发识别

- **WHEN** 自动回复处理一条 `comment_pictures` 为空的评价
- **THEN** 系统 MUST NOT 调用多模态模型

#### Scenario: 图片数量超过限制

- **WHEN** 评价包含的图片数量超过 `vision-max-image-count`
- **THEN** 系统 SHALL 只识别前 `vision-max-image-count` 张图片

### Requirement: 图片摘要持久化与幂等复用

系统 SHALL 按评价保存图片识别结果，包括状态、图片数量、图片 URL、模型、摘要文本、结构化 JSON、失败原因和时间信息，并在重复处理同一评价时复用已有成功结果。

#### Scenario: 识别成功保存摘要

- **WHEN** 多模态模型成功返回可用图片摘要
- **THEN** 系统 SHALL 保存成功状态、摘要文本、结构化 JSON、模型信息、图片 URL 列表、token usage 和扣减算力

#### Scenario: 重复处理复用成功摘要

- **WHEN** 同一 `comment_id` 已存在成功图片摘要
- **THEN** 系统 SHALL 复用已有摘要且 MUST NOT 重复调用多模态模型

#### Scenario: 识别失败保存失败原因

- **WHEN** 多模态模型调用失败、超时或返回无法解析的结果
- **THEN** 系统 SHALL 保存失败状态和失败原因

### Requirement: 图片识别按 token 扣算力

系统 SHALL 在图片识别成功后，根据模型返回的 token usage 按 RAG 相同规则扣减门店算力，并保证同一评价图片识别不会重复扣费。

#### Scenario: 成功识别按 usage 扣费

- **WHEN** 多模态模型成功返回 `usage.total_tokens`
- **THEN** 系统 SHALL 按 token usage 折算算力、扣减门店余额并保存扣减值

#### Scenario: usage 缺失使用兜底扣费

- **WHEN** 多模态模型识别成功但没有返回可用 usage
- **THEN** 系统 SHALL 使用配置的 `vision-fallback-power-amount` 扣减算力

#### Scenario: 识别失败不扣费

- **WHEN** 图片识别失败或降级
- **THEN** 系统 MUST NOT 扣减图片识别算力

#### Scenario: 重复执行不重复扣费

- **WHEN** 同一 `comment_id` 已存在成功图片摘要和扣费记录
- **THEN** 系统 MUST NOT 再次扣减图片识别算力

### Requirement: 图片摘要进入回复上下文

系统 SHALL 将图片摘要加入现有评论回复上下文，使 RAG embedding 检索和文本模型回复生成都能使用图片识别结果。

#### Scenario: 成功摘要参与 RAG

- **WHEN** 评价图片识别成功且系统生成 RAG 回复
- **THEN** embedding 查询文本和文本模型 prompt SHALL 包含图片摘要文本

#### Scenario: 识别失败降级参与上下文

- **WHEN** 评价包含图片但图片识别失败
- **THEN** 回复上下文 SHALL 包含“顾客上传了 N 张图片，但系统未能识别图片内容”的降级描述

#### Scenario: 官方随机回退保持可用

- **WHEN** RAG 因余额不足、无命中或模型异常回退官方随机文案
- **THEN** 系统 SHALL 继续生成官方回复且 MUST NOT 因图片识别失败阻塞回复

### Requirement: 多模态模型不直接生成最终回复

系统 MUST 仅使用多模态模型生成图片摘要，最终商家回复 SHALL 继续由现有评论回复 service 统一生成。

#### Scenario: 图片摘要和最终回复分离

- **WHEN** 评价同时包含文本和图片
- **THEN** 系统 SHALL 先生成或复用图片摘要，再通过现有 RAG 或官方回退逻辑生成最终回复

#### Scenario: 测试回复复用真实生成逻辑

- **WHEN** 管理后台用户在知识库管理页测试回复效果
- **THEN** 系统 SHALL 复用真实回复 service，并支持使用图片 URL 数组、上传多张图片或模拟图片摘要作为回复上下文

### Requirement: 图片识别配置可控

系统 SHALL 通过配置项控制图片识别能力、模型、base URL、API key、最大图片数量和超时。

#### Scenario: 图片识别关闭

- **WHEN** `vision-enabled` 为 false
- **THEN** 系统 MUST NOT 调用多模态模型，且自动回复 SHALL 继续按现有文本逻辑执行

#### Scenario: API key 未配置

- **WHEN** 图片识别启用但 API key 为空
- **THEN** 系统 SHALL 记录可观测日志并按图片识别失败降级处理
