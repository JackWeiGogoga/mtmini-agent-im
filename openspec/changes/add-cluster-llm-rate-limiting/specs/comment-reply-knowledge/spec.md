## MODIFIED Requirements

### Requirement: 智能生成失败回退
系统 SHALL 在 RAG 检索、embedding 生成或大模型发生非容量失败时回退到官方回复文案随机回复，不得让自动回复主链路因内容生成失败而无回复；内部限流等待和供应商容量冷却 MUST NOT 视为智能生成失败，系统 SHALL 等待许可或无损重排任务。

#### Scenario: embedding 生成失败
- **WHEN** 系统因非容量错误无法为评价内容生成 query embedding
- **THEN** 系统 SHALL 记录失败原因并回退到官方回复文案

#### Scenario: RAG 无命中
- **WHEN** 当前商户自有知识检索不到可用 chunk
- **THEN** 系统 SHALL 回退到官方回复文案

#### Scenario: 大模型调用失败
- **WHEN** 大模型接口返回非容量失败、超时或生成内容不可用
- **THEN** 系统 SHALL 记录失败原因并回退到官方回复文案

#### Scenario: 模型容量等待
- **WHEN** embedding 或 chat 模型因内部限流或供应商 429 冷却暂时不可调用
- **THEN** 系统 SHALL 等待许可或无损重排自动回复任务，MUST NOT 回退到官方回复文案

#### Scenario: 评分异常不回退
- **WHEN** 待回复评价的 `food_comment_score` 为空或不在 0-5 范围内
- **THEN** 系统 MUST 仅记录异常日志，并 SHALL 不因评分异常回退官方回复或默认文案
