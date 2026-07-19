## ADDED Requirements

### Requirement: 每个门店必须只有一个 IM 自动回复主开关
系统 SHALL 在 `mt_im_auto_reply_setting` 中为每个门店维护唯一的 `auto_reply_enabled`，且不得包含差评处理、差评申诉或其他评论专属开关。

#### Scenario: 门店没有设置记录
- **WHEN** 系统查询尚未初始化设置的门店
- **THEN** 系统将 IM 自动回复解释为关闭

#### Scenario: 创建门店设置
- **WHEN** 系统首次保存某门店的开关
- **THEN** 数据库只创建一条该 `shop_id` 的设置记录
- **AND** 重复创建收敛到同一记录

### Requirement: 授权初始化成功后必须开启本地开关
系统 SHALL 在授权后两个美团 IM 设置调用都明确成功后，以 `AUTH` 来源将本地 `auto_reply_enabled` 设置为开启。

#### Scenario: 首次授权初始化成功
- **WHEN** 门店授权成功且远端 IM 开启与机器人关闭均成功
- **THEN** 系统创建或更新本地设置为开启
- **AND** 设置历史记录来源为 `AUTH`

#### Scenario: 重新授权此前关闭的门店
- **WHEN** 一个本地开关已关闭的门店重新授权且 IM 初始化成功
- **THEN** 系统重新将本地开关设置为开启

### Requirement: H5 和管理员必须能够更新主开关
系统 SHALL 允许有权限的当前门店商家和管理员读取与修改 `auto_reply_enabled`，并继续应用登录态、门店访问范围和门店权益约束。

#### Scenario: 商家关闭自动回复
- **WHEN** 当前门店商家在 H5 关闭 IM 自动回复
- **THEN** 系统保存本地开关为关闭并记录 `H5` 来源历史
- **AND** 系统不关闭远端门店 IM、不打开美团机器人且不停止消息入库

#### Scenario: 管理员修改开关
- **WHEN** 有目标门店数据权限的管理员修改开关
- **THEN** 系统保存设置并记录 `ADMIN` 来源和操作人

#### Scenario: 越权修改开关
- **WHEN** 用户尝试修改无权访问门店的开关
- **THEN** 系统拒绝修改

### Requirement: 所有开关变化必须记录通用历史
系统 SHALL 在 `mt_im_auto_reply_setting_history` 中记录递增版本、门店、开关快照、来源、操作人、原因和前后状态。

#### Scenario: 开关状态发生变化
- **WHEN** `AUTH`、`H5`、`ADMIN` 或 `SYSTEM` 更新主开关
- **THEN** 系统新增一条不可覆盖的历史记录
- **AND** 历史 JSON 不包含 `negativeCommentAppealEnabled` 或其他评论字段

### Requirement: 算力不足必须只关闭本地自动回复
系统 SHALL 保留算力不足自动关停能力，并以 `SYSTEM` 来源和 `POWER_BALANCE_NOT_ENOUGH` 原因关闭本地 IM 自动回复开关。

#### Scenario: 后续回复任务发现算力不足
- **WHEN** 通用算力消费逻辑确认门店余额不足并请求关停服务
- **THEN** 系统在独立事务中关闭本地 `auto_reply_enabled`
- **AND** 系统不调用任何美团 IM 设置或查询接口
