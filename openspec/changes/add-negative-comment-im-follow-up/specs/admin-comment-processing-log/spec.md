## ADDED Requirements

### Requirement: 全量评价处理日志列表

系统 SHALL 提供独立的管理后台评价处理日志列表，以所有 `mt_comment` 记录为主数据源，统一展示评价、自动回复、差评申诉和 IM 群聊跟进的处理摘要。

#### Scenario: 展示所有评价

- **WHEN** 有权限的后台用户查询评价处理日志
- **THEN** 系统 SHALL 分页返回其数据范围内的所有评价，包括好评、中评、差评以及尚未触发或处理失败的评价

#### Scenario: 未触发后置处理的评价

- **WHEN** 一条评价没有申诉或 IM 跟进记录
- **THEN** 系统 SHALL 仍返回该评价，并 SHALL 将对应链路标记为“未触发”或“不适用”，而不是丢失记录或标记失败

#### Scenario: 默认排序与分页保护

- **WHEN** 请求未指定排序或请求过大的 pageSize
- **THEN** 系统 SHALL 默认按评价时间倒序，并 SHALL 应用服务端 pageSize 上限

### Requirement: 多维筛选与状态摘要

系统 SHALL 支持按评价和处理链路筛选，并 SHALL 在列表中返回足以快速定位异常的状态与关键时间摘要。

#### Scenario: 按评价和门店筛选

- **WHEN** 用户提交评价时间范围、门店 ID/名称/ePoiId、评价 ID、评价内容关键词或情感类型
- **THEN** 系统 SHALL 组合这些条件筛选评价记录

#### Scenario: 按处理状态筛选

- **WHEN** 用户指定自动回复状态、差评申诉状态或 IM 跟进状态
- **THEN** 系统 SHALL 返回对应处理链路满足条件的评价记录

#### Scenario: 列表展示处理摘要

- **WHEN** 系统返回列表行
- **THEN** 每行 SHALL 包含评价与门店摘要、情感和评价时间，以及自动回复、差评申诉、IM 跟进的状态、关键处理时间和安全错误摘要

### Requirement: 评价处理聚合详情

系统 SHALL 提供评价级只读详情，将评价原文和三个处理链路的审计信息聚合返回，便于复盘与排障。

#### Scenario: 查看评价与自动回复详情

- **WHEN** 用户打开某条评价详情
- **THEN** 系统 SHALL 返回门店、评价/追评、评分、图片、情感和时间，以及自动回复状态、内容、来源、模型、prompt 版本、token/算力、处理时间、耗时和错误摘要

#### Scenario: 查看差评申诉详情

- **WHEN** 用户打开某条差评详情
- **THEN** 系统 SHALL 返回申诉是否适用、状态、类型、内容、算力、处理时间、耗时和错误摘要

#### Scenario: 查看 IM 跟进详情

- **WHEN** 用户打开包含 IM 跟进记录的评价详情
- **THEN** 系统 SHALL 返回总体状态、消息来源和明文、兜底原因、模型/prompt、token/算力、建群与发送 attempt、`group_id`、`msg_id`、阶段时间、失败阶段和安全错误摘要

#### Scenario: 详情不存在或超出数据范围

- **WHEN** 评价不存在或不在当前用户可访问的数据范围
- **THEN** 系统 SHALL 返回未找到或拒绝访问，且 MUST NOT 泄露评价是否存在

### Requirement: 独立后台页面与只读交互

管理后台 SHALL 新增独立“评价处理日志”页面，使用筛选区、分页表格和详情抽屉展示全链路信息，且该页面 MUST 为只读。

#### Scenario: 从菜单进入页面

- **WHEN** 用户拥有评价处理日志菜单和列表权限
- **THEN** 管理后台 SHALL 显示“评价处理日志”入口并加载独立路由 `/comment-processing-logs`

#### Scenario: 查看处理详情

- **WHEN** 用户点击列表中的详情操作
- **THEN** 页面 SHALL 打开详情抽屉，并按“评价内容 / 自动回复 / 差评申诉 / 群聊跟进”分区展示数据

#### Scenario: 当前版本不提供人工操作

- **WHEN** 用户查看列表或详情
- **THEN** 页面 MUST NOT 提供人工重试、补建群、重发消息、编辑处理结果或导出操作

#### Scenario: 空值与不适用状态可辨认

- **WHEN** 某条处理链路未触发、不适用、处理中、成功或失败
- **THEN** 页面 SHALL 使用明确文案和状态标签区分这些情况，且 MUST NOT 将空值统一显示为失败

### Requirement: 处理日志权限、数据范围与脱敏

系统 SHALL 使用独立权限保护列表和详情，并 MUST 遵守管理后台现有数据范围与敏感信息规则。

#### Scenario: 列表权限

- **WHEN** 用户请求 `GET /api/system/comment-processing-logs`
- **THEN** 系统 SHALL 要求 `comment:processing-log:list` 权限并限制在当前用户数据范围内

#### Scenario: 详情权限

- **WHEN** 用户请求 `GET /api/system/comment-processing-logs/{commentId}`
- **THEN** 系统 SHALL 要求 `comment:processing-log:query` 权限并再次校验该评价的数据范围

#### Scenario: 未授权访问

- **WHEN** 用户未登录或缺少相应权限
- **THEN** 系统 SHALL 返回标准 401 或 403，且 MUST NOT 返回任何评价处理数据

#### Scenario: 禁止返回秘密和完整外部载荷

- **WHEN** 系统构造列表或详情响应
- **THEN** 响应 MUST NOT 包含门店 access token、`imSecret`、完整 `msg_content` 密文、完整 `push_content` 或完整美团请求载荷

### Requirement: 查询性能与一致性

系统 SHALL 使用服务端分页、批量聚合和必要索引查询全量日志，避免按列表行逐条查询处理表，并 SHALL 以持久化状态作为展示事实来源。

#### Scenario: 分页聚合处理链路

- **WHEN** 系统查询一页评价处理日志
- **THEN** 系统 SHALL 使用 join 或按当前页 ID 批量查询自动回复、申诉和 IM 摘要，且 MUST NOT 产生随行数线性增长的 N+1 查询

#### Scenario: 并发处理中读取日志

- **WHEN** 后台读取时某条异步任务正在更新状态
- **THEN** 系统 SHALL 返回当次查询读取到的已提交状态，页面 SHALL 允许用户刷新获取最新结果

#### Scenario: 必要查询索引

- **WHEN** 数据库迁移应用
- **THEN** 系统 SHALL 为评价时间/门店/状态筛选及申诉、IM 的评价关联和状态筛选建立必要索引
