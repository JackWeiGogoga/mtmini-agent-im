## ADDED Requirements

### Requirement: 差评自动投诉触发条件
系统 SHALL 在评价被判定为差评、当前门店自动回复主开关开启、差评处理/申诉开关开启、门店授权 token 有效且门店权益满足时，触发差评自动投诉处理。

#### Scenario: 差评且开关开启触发申诉
- **WHEN** `COMMENT_AUTO_REPLY` 成功处理一条差评，且当前门店自动回复主开关和差评处理/申诉开关均开启
- **THEN** 系统 SHALL 为该评价入队或执行差评自动投诉处理

#### Scenario: 非差评不触发申诉
- **WHEN** `COMMENT_AUTO_REPLY` 成功处理一条好评或中评
- **THEN** 系统 MUST NOT 触发差评自动投诉处理

#### Scenario: 差评处理开关关闭不触发申诉
- **WHEN** 当前门店差评处理/申诉开关关闭
- **THEN** 系统 MUST NOT 调用大模型生成申诉类型或申诉文本，且 MUST NOT 调用美团申诉接口

#### Scenario: 授权 token 缺失不提交申诉
- **WHEN** 差评自动投诉任务执行时无法获取当前门店有效 access token
- **THEN** 系统 MUST 标记申诉失败或等待重试，并 MUST NOT 使用其他门店 token 提交申诉

### Requirement: 申诉类型大模型判断
系统 SHALL 使用大模型基于评价全量字段判断美团申诉类型 `reportTypeCode`，并 MUST 将模型输出限制在系统支持的申诉类型白名单内；无法得到有效类型时 SHALL 默认使用 `47`。

#### Scenario: 模型返回有效申诉类型
- **WHEN** 大模型返回白名单内的 `reportTypeCode`
- **THEN** 系统 SHALL 使用该 `reportTypeCode` 继续生成申诉文本

#### Scenario: 模型返回无效申诉类型
- **WHEN** 大模型返回的 `reportTypeCode` 不在系统支持白名单内
- **THEN** 系统 SHALL 使用默认 `reportTypeCode=47`，并记录类型兜底原因

#### Scenario: 模型类型判断失败
- **WHEN** 申诉类型判断模型调用失败、超时或返回无法解析
- **THEN** 系统 SHALL 使用默认 `reportTypeCode=47`，并记录模型失败原因

#### Scenario: 支持的申诉类型白名单
- **WHEN** 系统校验申诉类型
- **THEN** 系统 SHALL 仅允许 `31`、`32`、`34`、`35`、`36`、`37`、`39`、`40`、`41`、`43`、`44`、`46`、`47` 作为有效 `reportTypeCode`

### Requirement: 申诉文本大模型生成
系统 SHALL 使用大模型基于评价内容、追评、评分、标签、配送超时说明、图片上下文和已确定的 `reportTypeCode` 生成 300 字以内的申诉文本内容。

#### Scenario: 有图评价生成申诉文本
- **WHEN** 差评包含评价图片或图片摘要
- **THEN** 系统 SHALL 在申诉 prompt 中包含图片上下文，并生成 300 字以内的申诉文本

#### Scenario: 无图评价生成申诉文本
- **WHEN** 差评没有评价图片
- **THEN** 系统 SHALL 在申诉 prompt 中明确顾客未上传图片，并生成 300 字以内的申诉文本

#### Scenario: 申诉文本生成失败
- **WHEN** 申诉文本模型调用失败、超时或返回空内容
- **THEN** 系统 MUST NOT 调用美团申诉接口，并 SHALL 记录申诉生成失败状态和错误原因

#### Scenario: 申诉文本合规校验失败
- **WHEN** 申诉文本命中敏感词或违反平台合规边界且无法改写为合规文本
- **THEN** 系统 MUST NOT 调用美团申诉接口，并 SHALL 记录合规校验失败原因

### Requirement: 申诉 prompt 配置
系统 SHALL 使用系统提示词配置管理差评自动投诉 prompt，并 SHALL 将有图/无图差异通过变量动态渲染，而不是维护两份仅文案差异的 prompt。

#### Scenario: 使用合并后的申诉 prompt
- **WHEN** 系统生成申诉文本
- **THEN** 系统 SHALL 通过同一个申诉内容 prompt 模板渲染 `pictureContext` 或等价变量表达有图/无图差异

#### Scenario: 数据库 prompt 启用
- **WHEN** `system_prompt_config` 中存在启用的申诉 prompt 配置
- **THEN** 系统 SHALL 优先使用数据库中的 prompt 内容和版本

#### Scenario: prompt 配置缺失
- **WHEN** 数据库申诉 prompt 缺失或停用
- **THEN** 系统 SHALL 回退到资源文件或代码内默认 prompt

### Requirement: 美团评价申诉提交
系统 SHALL 使用当前门店授权 token 调用美团评价申诉接口提交差评申诉，接口地址对应 `https://developer.meituan.com/docs/api/wmoper-ng-comment-complain-report`，请求字段以官方文档或 SDK typed request 为准。

#### Scenario: 申诉提交成功
- **WHEN** 美团申诉接口返回成功
- **THEN** 系统 SHALL 标记该评价申诉成功，并保存申诉类型、申诉文本、提交时间和美团响应摘要

#### Scenario: 申诉提交失败
- **WHEN** 美团申诉接口返回失败、超时或 SDK 抛出异常
- **THEN** 系统 SHALL 标记申诉失败，保存错误摘要，并允许后续按异步任务策略重试

#### Scenario: 申诉 dry run
- **WHEN** 差评自动投诉 dry run 配置开启
- **THEN** 系统 MUST NOT 调用美团申诉接口，并 SHALL 保留任务重排或跳过记录，避免误标记真实成功

#### Scenario: 接口索引更新
- **WHEN** 实现新增美团评价申诉调用
- **THEN** 系统文档 MUST 在 `docs/美团接口调用索引.md` 记录接口文档、地址、SDK request、本地入口、dry run、请求参数和幂等规则

### Requirement: 申诉状态持久化与幂等
系统 SHALL 按评价持久化差评自动投诉状态，并 MUST 保证同一评价成功申诉后不会重复生成、重复扣费或重复提交美团。

#### Scenario: 首次申诉保存状态
- **WHEN** 系统首次处理某条差评申诉
- **THEN** 系统 SHALL 保存申诉状态、`reportTypeCode`、申诉文本、模型、prompt 版本、token usage、算力消耗、错误和时间字段

#### Scenario: 已成功申诉跳过
- **WHEN** 同一 `comment_id` 已存在申诉成功记录
- **THEN** 系统 MUST 跳过申诉类型判断、申诉文本生成和美团申诉提交

#### Scenario: 重复任务不重复扣费
- **WHEN** 同一评价的差评自动投诉任务被重复领取或重试
- **THEN** 系统 MUST 使用本地成功状态、唯一索引或扣费 requestNo 防止重复扣减算力

#### Scenario: 申诉失败可重试
- **WHEN** 同一评价存在申诉失败记录且未达到异步任务最大重试次数
- **THEN** 系统 SHALL 允许重试申诉处理，并更新最近一次失败或成功结果

### Requirement: 申诉可观测性与门店隔离
系统 SHALL 记录差评自动投诉关键日志和状态，并 MUST 保证申诉生成、提交、查询和重试均按 `shop_id` 隔离。

#### Scenario: 记录关键日志
- **WHEN** 差评自动投诉任务执行
- **THEN** 系统 SHALL 记录 `shopId`、`commentId`、申诉状态、`reportTypeCode`、耗时和失败阶段，且不得输出完整 access token

#### Scenario: 门店隔离提交
- **WHEN** 系统为某条评价提交申诉
- **THEN** 系统 MUST 仅使用该评价所属 `shop_id` 对应的门店、`epoiId` 和 access token

#### Scenario: 评价与任务门店不一致
- **WHEN** 异步任务 payload 的 `shopId` 与本地评价所属 `shop_id` 不一致
- **THEN** 系统 MUST 拒绝执行申诉并记录异常
