## ADDED Requirements

### Requirement: 评论日志分析生成

系统 SHALL 基于评价全量字段、自动回复结果和可用图片摘要生成 AI 分析日志文案，并将结果持久化到当前评价可查询的数据中。

#### Scenario: 自动回复成功后生成分析

- **WHEN** 评价自动回复成功且当前评价尚无成功的分析日志文案
- **THEN** 系统 SHALL 调用大模型生成 AI 分析日志文案并保存到本地

#### Scenario: 分析生成不影响美团回复结果

- **WHEN** 美团商家回复已经成功但 AI 分析日志生成失败
- **THEN** 系统 MUST 保持自动回复成功状态，并 SHALL 单独记录分析失败状态和原因

#### Scenario: 跳过已有成功分析

- **WHEN** 当前评价已经存在成功的 AI 分析日志文案
- **THEN** 系统 MUST 跳过重复生成

#### Scenario: 分析文案长度限制

- **WHEN** 大模型返回 AI 分析日志文案
- **THEN** 系统 SHALL 将可展示文案限制在 500 个中文字符以内

### Requirement: 评论日志分析提示词

系统 SHALL 使用 `COMMENT_LOG_ANALYSIS` 系统提示词生成评论日志分析，默认模板来自 `backend/src/main/resources/prompts/comment_log.prompt`，并支持管理后台动态覆盖。

#### Scenario: 使用后台配置 prompt

- **WHEN** `COMMENT_LOG_ANALYSIS` 存在启用的后台配置
- **THEN** 评论日志分析 SHALL 使用后台配置的 prompt 模板

#### Scenario: 使用资源文件默认 prompt

- **WHEN** `COMMENT_LOG_ANALYSIS` 不存在启用的后台配置
- **THEN** 评论日志分析 SHALL 使用 `comment_log.prompt` 资源文件模板

#### Scenario: prompt 输入包含评价上下文

- **WHEN** 系统渲染评论日志分析 prompt
- **THEN** prompt SHALL 包含口味评分、配送评分、商品评分、包装评分、评价标签、配送时效、首次评价、追评、图片摘要和评论类型中的可用字段

#### Scenario: prompt 约束输出边界

- **WHEN** 系统请求大模型生成 AI 分析日志
- **THEN** prompt MUST 约束模型输出商家可读分析，不得承诺退款、补发、赔偿或私下联系顾客

### Requirement: 评论日志分析可观测与计费

系统 SHALL 保存评论日志分析的大模型调用状态、模型、token usage、算力消耗、提示词版本、生成时间和错误信息。

#### Scenario: 成功记录模型调用信息

- **WHEN** 评论日志分析生成成功
- **THEN** 系统 SHALL 保存分析状态、分析文案、模型名称、token usage、算力消耗、提示词版本和生成时间

#### Scenario: usage 缺失兜底计费

- **WHEN** 大模型生成成功但没有返回可用 token usage
- **THEN** 系统 SHALL 使用配置的兜底算力值记录本次分析消耗

#### Scenario: 生成失败不扣费

- **WHEN** 评论日志分析调用失败、超时或返回空内容
- **THEN** 系统 MUST 不扣减分析算力，并 SHALL 保存失败原因

#### Scenario: 重试失败分析

- **WHEN** 某条评价的分析状态为失败且自动回复链路再次安全触发分析生成
- **THEN** 系统 SHALL 允许重新生成并覆盖失败状态
