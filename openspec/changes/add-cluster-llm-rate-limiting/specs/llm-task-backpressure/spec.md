## ADDED Requirements

### Requirement: LLM 任务使用有界执行 lane
系统 SHALL 将模型相关数据库异步任务与授权、权益等普通任务分开执行；LLM lane 每节点默认并发为 2，且 MUST 将未获得执行槽位的任务保留在 MySQL `PENDING` 状态而不是预先排入 JVM 大队列。

#### Scenario: 大量 LLM 任务积压
- **WHEN** 可执行 LLM 任务数量超过当前执行槽位
- **THEN** 系统 SHALL 只领取可立即执行的任务，其余任务 SHALL 保持在数据库等待

#### Scenario: 普通任务与模型任务并存
- **WHEN** 模型任务形成积压且同时存在授权或权益任务
- **THEN** 普通任务 SHALL 使用独立执行 lane，MUST NOT 被模型等待线程耗尽执行资源

#### Scenario: 领取后提交失败
- **WHEN** worker 已原子领取任务但无法提交到 LLM 执行器
- **THEN** 系统 SHALL 立即无损重排该任务且 MUST NOT 等待超时回收

### Requirement: LLM 任务按类型加权公平调度
系统 SHALL 对 LLM 任务类型使用可配置加权轮询，默认权重 SHALL 为自动回复 5、IM 跟进 2、差评申诉 2、日志分析 1、知识解析 1。

#### Scenario: 多类任务同时积压
- **WHEN** 自动回复、IM、申诉、日志分析和知识解析均有待执行任务
- **THEN** 系统 SHALL 按配置权重分配执行机会，同时保证低权重任务最终可执行

### Requirement: 同类任务按门店公平选择
系统 SHALL 使用 `scope_key` 作为门店公平维度，在同一任务类型内优先选择不同门店各自最早的一条任务，再选择同一门店后续任务。

#### Scenario: 单个门店产生大量评价
- **WHEN** 一个门店有大量待处理任务且其他门店也有待处理任务
- **THEN** 系统 MUST NOT 让该门店长期占满全部执行机会，其他门店 SHALL 获得处理机会

#### Scenario: 双节点选择相同任务
- **WHEN** 两个节点公平查询时同时看到相同待处理 ID
- **THEN** 系统 SHALL 通过数据库原子领取保证最多一个节点实际执行

### Requirement: 容量等待无损重排
系统 SHALL 将超过最大等待时间的模型容量等待任务从 `PROCESSING` 恢复为 `PENDING`，设置下一次 `available_at` 和随机抖动，并保持原 `retry_count`。

#### Scenario: 任务等待超过上限
- **WHEN** LLM 任务收到容量等待信号且不能在当前执行窗口内继续
- **THEN** 系统 SHALL 无损重排任务，并 MUST NOT 将任务标记为 `DEAD` 或普通失败

### Requirement: 评论日志分析独立异步执行
系统 SHALL 在评价自动回复成功后幂等入队 `COMMENT_LOG_ANALYSIS`，不得在自动回复主任务内同步调用模型生成分析；分析任务 SHALL 重新校验门店归属、自动回复状态和已有成功分析。

#### Scenario: 自动回复成功
- **WHEN** 美团商家回复成功并完成本地状态落库
- **THEN** 系统 SHALL 入队独立评论日志分析任务，自动回复主任务无需等待分析完成

#### Scenario: 重复入队日志分析
- **WHEN** 同一评价重复触发分析任务入队
- **THEN** 系统 SHALL 通过稳定 bizKey 收敛为同一任务且 MUST NOT 重复扣减算力

#### Scenario: 分析容量等待
- **WHEN** 评论日志分析任务暂时无法获得模型额度
- **THEN** 系统 SHALL 无损等待且 MUST NOT 将 `analysis_status` 更新为 `FAILED`

#### Scenario: 已成功分析再次执行
- **WHEN** 评论日志分析任务发现评价已有 `analysis_status=SUCCESS`
- **THEN** 系统 MUST 跳过模型调用和算力扣减
