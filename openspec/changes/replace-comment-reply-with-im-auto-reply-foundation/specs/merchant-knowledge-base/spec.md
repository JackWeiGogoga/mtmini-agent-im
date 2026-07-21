## ADDED Requirements

### Requirement: 门店知识库必须脱离评价语义
系统 SHALL 提供通用的门店知识条目、知识文档和知识切片数据模型，且不得依赖评价实体、评价 ID、评论情感或评价回复模板。

#### Scenario: 初始化知识库结构
- **WHEN** 全新数据库完成初始化
- **THEN** 知识表只包含门店 QA、文档、切片、Embedding 和启停状态所需字段
- **AND** 知识表不包含 `OFFICIAL/TEMPLATE` 评价模板范围或 `GOOD/NEUTRAL/BAD` 评论情感字段

### Requirement: 商家必须能够维护门店 QA
系统 SHALL 允许已登录且有当前门店访问权限的商家或管理员新增、查询、修改和删除门店 QA，并按 `shop_id` 隔离数据。

#### Scenario: 当前门店维护 QA
- **WHEN** 用户为当前门店新增或修改一条问题与答案
- **THEN** 系统将知识条目保存到当前 `shop_id`
- **AND** 后续列表查询能够返回最新内容

#### Scenario: 跨门店访问 QA
- **WHEN** 用户尝试读取或修改无权访问门店的 QA
- **THEN** 系统拒绝访问且不泄露知识内容

### Requirement: 系统必须保留知识文档解析能力
系统 SHALL 保留知识文档上传、COS 存储、异步解析、切片、Embedding、解析状态和失败摘要能力，并继续使用通用 `KNOWLEDGE_DOCUMENT_PARSE` 任务。

#### Scenario: 上传支持的知识文档
- **WHEN** 用户为门店上传受支持且未超过配置限制的文档
- **THEN** 系统保存文档元数据和对象存储引用
- **AND** 系统幂等入队 `KNOWLEDGE_DOCUMENT_PARSE` 任务生成知识切片与向量

#### Scenario: 文档解析失败
- **WHEN** 文档读取、切片或 Embedding 失败
- **THEN** 系统保存失败状态与安全错误摘要
- **AND** 失败不影响该门店其他知识条目和文档

### Requirement: 知识检索必须按门店和相似度隔离
系统 SHALL 根据查询文本生成向量，只在当前门店启用且未删除的知识切片中执行相似度检索，并返回达到配置阈值的候选。

#### Scenario: 检索门店知识
- **WHEN** 调用方使用某门店和查询文本检索知识
- **THEN** 结果只包含该门店启用的知识切片
- **AND** 结果按相似度排序并遵守 topK 与阈值配置

### Requirement: 知识库界面不得保留评价测试入口
管理后台和 H5 SHALL 保留通用知识管理功能，但 MUST 删除评价回复预览、差评申诉测试、评价评分标签和评价图片测试。

#### Scenario: 管理员进入商家知识库
- **WHEN** 管理员打开知识库管理页面
- **THEN** 页面提供 QA、文档、解析状态和切片管理
- **AND** 页面不要求构造评价、选择星级或触发评价回复与申诉测试

### Requirement: 知识库权限必须使用通用命名
系统 MUST 使用不含 `comment` 的知识库菜单和权限标识，并保持后端接口、前端路由与初始化数据一致。

#### Scenario: 初始化管理员权限
- **WHEN** 全新数据库初始化菜单和权限
- **THEN** 商家知识库权限使用 `knowledge:*` 范围
- **AND** 不创建 `comment:reply-knowledge:*` 或 `comment:merchant-knowledge:*` 权限
