## MODIFIED Requirements

### Requirement: H5 评价回复日志分页查询

系统 SHALL 为 H5 当前登录门店提供评价自动回复日志页码分页数据，数据必须按门店隔离，并包含页面渲染所需的评价文本、追评文本、图片列表、AI 回复文案、AI 分析文案、评价类型、星级评分和时间字段。AI 分析文案 SHALL 优先返回已生成并持久化的真实分析结果，缺失时返回明确兜底文案。

#### Scenario: 当前门店查询日志

- **WHEN** 已登录商家打开 H5 日志页并请求第一页日志
- **THEN** 系统 SHALL 仅返回当前登录门店的所有已保存评价记录分页数据

#### Scenario: 不按自动回复状态过滤

- **WHEN** 当前门店存在 `PENDING`、`SUCCESS`、`FAILED` 或 `SKIPPED` 状态的已保存评价记录
- **THEN** 系统 SHALL 将这些记录纳入日志分页结果

#### Scenario: 日志按时间倒序返回

- **WHEN** 当前门店存在多条评价记录
- **THEN** 系统 SHALL 按自动回复时间、评价时间或创建时间中的可用时间倒序返回日志

#### Scenario: 分页加载更多

- **WHEN** H5 使用 `pageNum` 和 `pageSize` 请求下一页日志且仍有更多记录
- **THEN** 系统 SHALL 返回下一页列表和可判断是否还有下一页的页码分页字段

#### Scenario: AI 分析返回真实结果

- **WHEN** 日志记录存在已生成成功的 AI 分析文案
- **THEN** 系统 SHALL 在 `analysisText` 中返回该真实分析文案

#### Scenario: AI 分析缺失时兜底

- **WHEN** 日志记录没有成功生成的 AI 分析文案
- **THEN** 系统 SHALL 返回或展示固定文案“AI 分析结果”

#### Scenario: 星级评分使用商品包装最低分

- **WHEN** 日志记录包含 `product_score` 和 `packing_score`
- **THEN** 系统 SHALL 将两者中的最低有效分作为日志 `score` 返回，且 MUST NOT 使用已废弃的 `food_comment_score`
