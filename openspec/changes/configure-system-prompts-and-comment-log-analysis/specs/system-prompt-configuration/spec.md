## ADDED Requirements

### Requirement: 系统提示词配置存储

系统 SHALL 使用通用配置模型按 `prompt_key` 存储系统提示词，每条配置 MUST 包含名称、内容、启用状态、版本、默认资源路径、变量说明和审计时间信息。

#### Scenario: 初始化系统提示词配置

- **WHEN** 数据库迁移初始化系统提示词配置
- **THEN** 系统 SHALL 为评价回复、评价图片识别和评论日志分析创建可识别的 `prompt_key`

#### Scenario: prompt key 唯一

- **WHEN** 后台用户保存系统提示词配置
- **THEN** 系统 MUST 保证 `prompt_key` 在系统内唯一

#### Scenario: 保存版本递增

- **WHEN** 后台用户修改提示词内容、启用状态或备注
- **THEN** 系统 SHALL 更新审计字段并递增该提示词版本

### Requirement: 系统提示词运行时解析

系统 SHALL 为大模型业务提供统一提示词解析服务，业务调用 MUST 优先使用启用的数据库配置，并在配置不可用时回退到默认资源文件或代码兜底。

#### Scenario: 使用启用的后台配置

- **WHEN** 某个 `prompt_key` 存在启用的数据库配置且内容非空
- **THEN** 运行时解析服务 SHALL 返回该配置内容和当前版本

#### Scenario: 停用配置回退默认资源

- **WHEN** 某个 `prompt_key` 的数据库配置被停用
- **THEN** 运行时解析服务 SHALL 使用该配置声明的默认资源文件内容

#### Scenario: 默认资源缺失时兜底

- **WHEN** 数据库配置不可用且默认资源文件不存在或为空
- **THEN** 运行时解析服务 MUST 返回业务提供的代码兜底内容并记录可观测日志

#### Scenario: 变量渲染

- **WHEN** 业务传入变量并请求渲染提示词模板
- **THEN** 系统 SHALL 使用 `{{variableName}}` 占位符替换变量，空值按“无”处理

#### Scenario: 未知变量可观测

- **WHEN** 模板渲染后仍存在未识别变量占位符
- **THEN** 系统 SHALL 记录包含 `prompt_key` 和变量名的日志

### Requirement: 管理后台提示词配置页面

管理后台 SHALL 提供系统提示词配置页面，允许有权限的后台用户查看、编辑、启停和恢复默认提示词配置。

#### Scenario: 查看提示词列表

- **WHEN** 拥有 `system:prompt:list` 权限的用户打开提示词配置页面
- **THEN** 页面 SHALL 展示 `prompt_key`、名称、启用状态、版本、更新时间和备注

#### Scenario: 编辑提示词内容

- **WHEN** 拥有 `system:prompt:edit` 权限的用户提交新的提示词内容
- **THEN** 系统 SHALL 保存内容、更新版本并返回最新配置

#### Scenario: 启停提示词配置

- **WHEN** 拥有 `system:prompt:edit` 权限的用户切换提示词启用状态
- **THEN** 系统 SHALL 更新启用状态并影响后续运行时解析结果

#### Scenario: 恢复默认提示词

- **WHEN** 拥有 `system:prompt:edit` 权限的用户对某个提示词执行恢复默认
- **THEN** 系统 SHALL 将该提示词内容恢复为默认资源文件内容并递增版本

#### Scenario: 无权限访问

- **WHEN** 未拥有提示词管理权限的用户访问提示词接口或页面
- **THEN** 系统 MUST 拒绝访问

### Requirement: 系统提示词审计与业务追踪

系统 SHALL 在使用提示词执行大模型业务时记录可追踪信息，至少包含 `prompt_key`、提示词版本、模型名称和失败原因。

#### Scenario: 业务记录 prompt 版本

- **WHEN** 系统使用动态提示词调用大模型并保存业务结果
- **THEN** 对应业务记录 SHALL 保存使用的 `prompt_key` 和提示词版本

#### Scenario: 大模型调用失败

- **WHEN** 使用动态提示词的大模型调用失败
- **THEN** 系统 SHALL 记录失败阶段、失败原因和对应 `prompt_key`
