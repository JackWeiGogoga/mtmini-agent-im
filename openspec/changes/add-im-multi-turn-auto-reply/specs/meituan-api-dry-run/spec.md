## ADDED Requirements

### Requirement: 系统默认阻断全部美团 SDK 出站调用

系统 SHALL 通过统一客户端边界控制全部美团 SDK 出站调用，并且所有环境的 dry-run 默认值 SHALL 为开启。

#### Scenario: 默认配置下调用任意美团 SDK 方法

- **GIVEN** 未显式配置 `MT_API_DRY_RUN=false`
- **WHEN** 业务尝试执行 OAuth、Token 刷新、带 Token或不带 Token 的 typed API、分页 API 或 common API
- **THEN** 系统 SHALL 在调用真实 SDK delegate 前阻断请求
- **AND** 系统 SHALL 返回稳定的 dry-run 信号而不是伪造美团成功响应

#### Scenario: 显式关闭 dry-run

- **GIVEN** `MT_API_DRY_RUN=false`
- **WHEN** 业务执行美团 SDK 调用
- **THEN** 统一客户端 SHALL 将调用及原始参数委托给真实 SDK 客户端
- **AND** 原有响应及异常语义 SHALL 保持不变

### Requirement: 异步任务 dry-run 不丢失且不消耗重试

系统 SHALL 将美团 API dry-run 信号接入现有数据库异步任务无损重排机制。

#### Scenario: 异步任务到达美团调用边界

- **GIVEN** 全局 dry-run 已开启
- **WHEN** 授权后处理或 IM 自动回复任务准备调用美团接口
- **THEN** 系统 SHALL 不标记任务成功或远端操作成功
- **AND** worker SHALL 按配置的秒数重新安排任务
- **AND** 任务重试次数 SHALL 不增加

### Requirement: dry-run 日志不得泄露敏感请求信息

系统 SHALL 仅记录识别出站操作所需的安全元数据。

#### Scenario: dry-run 命中并记录日志

- **WHEN** 任意美团调用被 dry-run 阻断
- **THEN** 日志 MAY 包含 SDK 方法名和 Request 类型
- **AND** 日志 SHALL NOT 包含 access token、refresh token、OAuth code、完整请求正文、消息正文或密文

### Requirement: 本地门店登录不依赖真实美团调用

系统 SHALL 提供固定且可重复创建的本地门店测试夹具。

#### Scenario: 使用固定 wmPoiId 模拟 H5 登录

- **GIVEN** 已执行本地测试数据脚本
- **WHEN** 浏览器访问 `http://localhost:5173/?wmPoiId=TEST_EPOI_REPLY_001&platform=local-test`
- **THEN** H5 SHALL 能通过本地门店查询和登录流程进入应用
- **AND** 该过程 SHALL 不要求关闭美团 API dry-run

#### Scenario: 查看预置回复历史

- **GIVEN** 已执行本地测试数据脚本
- **WHEN** 管理员或该门店用户打开回复日志和会话历史
- **THEN** 系统 SHALL 提供用户消息、AI 回复、不同回复状态和多个历史 Session
- **AND** 会话历史 SHALL 足以验证跨 Session 滚动加载
