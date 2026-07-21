## ADDED Requirements

### Requirement: 授权成功后必须异步初始化门店 IM
系统 SHALL 在门店授权核心状态与 access token 保存成功后入队 `AUTH_CONFIGURE_IM`，且不得在授权回调事务中同步等待美团 IM 设置。

#### Scenario: 门店授权保存成功
- **WHEN** 系统成功保存门店和当前有效 access token
- **THEN** 系统使用当前授权事件键幂等入队 `AUTH_CONFIGURE_IM`
- **AND** 系统继续保留关闭订单推送、补齐 `poiId` 和同步权益等现有授权后任务
- **AND** 系统不再入队授权后评价回补任务

### Requirement: 授权任务必须按顺序执行两个设置接口
`AUTH_CONFIGURE_IM` MUST 先开启门店 IM，再关闭美团智能回复机器人，最后开启本地自动回复开关。

#### Scenario: 完成授权后 IM 设置
- **WHEN** worker 执行有效门店的 `AUTH_CONFIGURE_IM`
- **THEN** 系统调用 `SetPoiImStatusRequest` 并设置 `imStatus=1`
- **AND** 第一次调用成功后，系统调用 `SetAutoSendConfigInfoRequest`
- **AND** `allStatus` 只包含一项 `type=0`、`switchStatus=0`
- **AND** 两次设置都成功后系统才开启本地开关

#### Scenario: 开启门店 IM 失败
- **WHEN** 第一个设置接口返回失败、空响应或 SDK 异常
- **THEN** 系统不调用机器人设置接口
- **AND** 系统不开启本地开关并按任务策略记录失败

#### Scenario: 关闭机器人失败
- **WHEN** 门店 IM 已成功开启但机器人设置接口失败
- **THEN** 系统不开启本地开关并按任务策略记录失败

### Requirement: 授权初始化不得调用付费查询接口
系统 MUST NOT 在授权后任务或其客户端中调用机器人开关查询接口或其他用于复核设置结果的付费接口。

#### Scenario: 两个设置接口返回成功
- **WHEN** 授权后 IM 设置完成
- **THEN** 系统直接依据两个设置响应更新本地开关
- **AND** 系统不调用 `GetAutoSendConfigInfoRequest`

#### Scenario: 设置接口返回部分权限提示
- **WHEN** 机器人设置响应包含可记录的未生效类型或权限信息
- **THEN** 系统保存或记录脱敏结果摘要
- **AND** 系统不发起额外查询验证

### Requirement: 授权任务必须幂等且允许重试
系统 SHALL 以门店和授权事件标识保证同一授权事件只创建一个任务，并允许对固定设置值安全重试。

#### Scenario: 同一授权回调重复到达
- **WHEN** 系统为同一门店和同一授权 token 重复入队
- **THEN** 数据库只保留一个 `AUTH_CONFIGURE_IM` 任务

#### Scenario: 第二步失败后任务重试
- **WHEN** 首次执行已开启门店 IM但关闭机器人失败
- **THEN** 重试可以再次设置 `imStatus=1` 后继续关闭机器人
- **AND** 重试不会创建重复本地设置或重复历史版本冲突

#### Scenario: 重新授权
- **WHEN** 同一门店获得新的授权 token
- **THEN** 系统创建新的授权初始化事件并重新执行固定设置流程

### Requirement: 授权初始化失败不得回滚授权
系统 SHALL 将 IM 初始化作为授权后的独立任务，任何设置失败不得使已经保存的门店授权、token 或权益状态回滚。

#### Scenario: 美团设置接口持续失败
- **WHEN** `AUTH_CONFIGURE_IM` 达到最大重试次数
- **THEN** 任务进入可观测的终态失败
- **AND** 门店授权与 access token 仍保持有效
- **AND** 本地 IM 自动回复开关保持关闭

### Requirement: 授权任务必须按门店隔离并脱敏
系统 MUST 使用 payload 中的 `shopId` 重新读取当前门店和有效 token，确保任务、门店和 token 作用域一致，并禁止持久化或记录 access token、签名密钥和完整请求。

#### Scenario: 任务门店与 token 不一致
- **WHEN** worker 无法为 payload 门店取得对应有效 token
- **THEN** 系统终止本次外部调用并记录安全错误摘要

#### Scenario: 记录设置调用结果
- **WHEN** 任一美团设置调用完成
- **THEN** 日志最多包含 `shopId`、任务 ID、阶段、code、traceId、耗时和脱敏错误
- **AND** 日志不包含 access token、signKey 或完整外部请求载荷
