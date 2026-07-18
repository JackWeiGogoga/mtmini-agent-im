## ADDED Requirements

### Requirement: 系统使用现行业务事件目录
系统 MUST 仅接受与评价自动回复现行业务相关且来源匹配的行为事件，不得继续接受旧推广策略、手动出价、AI 高峰期和旧推广配置事件。

#### Scenario: H5 上报合法事件
- **WHEN** 已登录门店上报受支持的 H5 事件
- **THEN** 系统保存门店、来源、事件类型、会话、事件唯一标识、时间和合法上下文

#### Scenario: 来源不匹配或事件已弃用
- **WHEN** 客户端上报来源不匹配或旧推广业务事件
- **THEN** 系统拒绝该事件且不写入行为明细

### Requirement: 客户端行为支持会话识别和幂等去重
H5 与管理后台 MUST 为行为事件提供当前应用会话标识和事件唯一标识，系统 MUST 保证同一事件唯一标识最多保存一次。

#### Scenario: 同一客户端事件重复上报
- **WHEN** 同一 `clientEventId` 被重复提交
- **THEN** 系统仅保留一条行为明细并按成功响应处理重复请求

#### Scenario: H5 应用会话开始
- **WHEN** H5 在一个应用会话中首次成功发起页面行为上报
- **THEN** 客户端最多上报一次 `H5_APP_OPEN` 并让后续事件复用同一 `sessionId`

### Requirement: H5 记录关键页面与入口行为
H5 MUST 记录页面访问、客服入口和外部链接入口，并通过受控上下文区分页面、入口与目标。

#### Scenario: 页面展示
- **WHEN** 首页、日志、算力、知识库或客服页面展示
- **THEN** H5 上报 `H5_PAGE_VIEW`，上下文包含对应 `pageCode` 和 `entrySource`

#### Scenario: 商户打开客服入口
- **WHEN** 商户通过使用指导、建议反馈或客服 Tab 打开客服入口
- **THEN** H5 上报 `H5_SUPPORT_ENTRY_CLICK` 并区分入口来源

#### Scenario: 商户打开外部页面
- **WHEN** 商户点击立即续费、活动弹窗或更多活动入口
- **THEN** H5 在跳转前上报 `H5_EXTERNAL_LINK_CLICK`，上下文仅包含目标编码和入口来源

### Requirement: H5 记录可解释的充值行为
H5 MUST 只把校验通过的支付提交记录为充值提交，并 MUST 以脱敏结果事件记录兑换码应用结果。

#### Scenario: 有效充值提交
- **WHEN** 商户输入通过校验并提交充值
- **THEN** H5 上报 `H5_RECHARGE_SUBMIT_CLICK`，包含算力数量、是否选择推荐套餐和是否已应用兑换码

#### Scenario: 无效充值点击
- **WHEN** 充值金额或页面状态未通过前端校验
- **THEN** H5 不上报充值提交事件

#### Scenario: 兑换码校验完成
- **WHEN** 兑换码自动应用成功或失败
- **THEN** H5 上报 `H5_REDEMPTION_APPLY_RESULT` 和结果编码，且不得上报兑换码原文

### Requirement: H5 记录日志和知识库关键行为
H5 MUST 记录日志详情展开和知识库关键路径，但不得采集评价正文、文件正文或问答正文。

#### Scenario: 展开回复日志详情
- **WHEN** 商户从收起状态展开一条回复日志
- **THEN** H5 上报 `H5_LOG_DETAIL_EXPAND`，只包含评论类型、是否有图片等非敏感状态

#### Scenario: 使用知识库
- **WHEN** 商户选择文档、完成预览、确认上传或打开 QA 新增编辑表单
- **THEN** H5 上报 `H5_KNOWLEDGE_ACTION`，通过 `actionCode`、结果和文件类型等非敏感字段区分动作

### Requirement: 管理后台记录按门店的有效查看行为
管理后台 MUST 记录总控台中的回复日志和知识库查看入口，并由服务端校验操作人对目标门店可见。

#### Scenario: 打开回复日志
- **WHEN** 已登录用户在总控台打开某门店回复日志
- **THEN** 管理后台上报 `PC_COMMENT_LOG_VIEW`，系统记录后台用户、门店和当前门户类型

#### Scenario: 打开门店知识库
- **WHEN** 已登录用户从总控台进入某门店知识库
- **THEN** 管理后台上报 `PC_KNOWLEDGE_VIEW`，系统记录后台用户、门店和当前门户类型

#### Scenario: 无统计读取权限但门店可见
- **WHEN** 已登录用户没有行为统计读取权限但对目标门店可见
- **THEN** 系统允许写入合法 PC 行为事件

### Requirement: 行为统计联合行为事件与开关成功变更事实
行为统计 MUST 以当前用户可见门店为基础，联合行为事件与 `mt_comment_reply_setting_history`，并按所选时间范围返回门店级指标。

#### Scenario: 门店没有行为
- **WHEN** 当前用户可见门店在所选时间范围没有行为事件和开关变更
- **THEN** 统计结果仍包含该门店且所有指标为零

#### Scenario: H5 成功变更自动回复开关
- **WHEN** `change_source=H5` 的历史记录中自动回复开关前后值不同
- **THEN** 该门店 H5 自动回复开关成功变更数增加一次

#### Scenario: H5 成功变更差评处理开关
- **WHEN** `change_source=H5` 的历史记录中差评处理开关前后值不同
- **THEN** 该门店 H5 差评处理开关成功变更数增加一次

#### Scenario: PC 成功变更开关
- **WHEN** `change_source=ADMIN` 的历史记录中任一开关前后值不同
- **THEN** 对应门店和对应字段的 PC 成功变更数增加一次

#### Scenario: 系统自动关停
- **WHEN** `change_source=SYSTEM` 且变更原因为算力不足
- **THEN** 该门店系统自动关停数增加一次且不计入商户操作数

### Requirement: 事件上下文受校验且不影响主流程
系统 MUST 仅接受为空或有效 JSON 对象且长度受限的事件上下文；客户端上报失败 MUST 不影响原业务操作。

#### Scenario: 非法上下文
- **WHEN** 客户端提交非 JSON、非对象或超过长度限制的上下文
- **THEN** 系统拒绝该事件且不写入明细

#### Scenario: 上报请求失败
- **WHEN** 埋点接口发生网络或服务异常
- **THEN** H5 或管理后台继续执行原页面交互且不向商户暴露埋点异常
