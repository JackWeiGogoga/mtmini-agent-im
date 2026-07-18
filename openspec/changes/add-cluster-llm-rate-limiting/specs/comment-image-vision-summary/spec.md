## MODIFIED Requirements

### Requirement: 图片摘要持久化与幂等复用
系统 SHALL 按评价保存图片识别结果，包括状态、图片数量、图片 URL、模型、摘要文本、结构化 JSON、失败原因和时间信息，并在重复处理同一评价时复用已有成功结果；内部限流等待和供应商容量冷却 MUST NOT 写入图片识别失败状态。

#### Scenario: 识别成功保存摘要
- **WHEN** 多模态模型成功返回可用图片摘要
- **THEN** 系统 SHALL 保存成功状态、摘要文本、结构化 JSON、模型信息、图片 URL 列表、token usage 和扣减算力

#### Scenario: 重复处理复用成功摘要
- **WHEN** 同一 `comment_id` 已存在成功图片摘要
- **THEN** 系统 SHALL 复用已有摘要且 MUST NOT 重复调用多模态模型

#### Scenario: 识别失败保存失败原因
- **WHEN** 多模态模型发生非容量调用失败、超时或返回无法解析的结果
- **THEN** 系统 SHALL 保存失败状态和失败原因

#### Scenario: 图片识别容量等待
- **WHEN** 图片识别因内部限流或供应商 429 冷却暂时无法调用模型
- **THEN** 系统 SHALL 等待许可或无损重排所属任务，并 MUST NOT 保存图片识别失败状态
