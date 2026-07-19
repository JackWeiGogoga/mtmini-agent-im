## ADDED Requirements

### Requirement: 系统必须按统一消息协议接收 IM 两方会话消息
系统 SHALL 通过 `POST /api/callbacks/meituan/imMsg` 接收 `application/x-www-form-urlencoded` 回调，并读取美团统一消息公共参数和 JSON 格式的 `message`。

#### Scenario: 接收规范回调
- **WHEN** 美团发送包含 `msgType`、`timestamp`、`sign`、`developerId`、`businessId`、`msgId`、`opBizCode`、`ePoiId` 和 `message` 的表单请求
- **THEN** 系统读取外层公共字段并进入验签与持久化流程

#### Scenario: 不支持的 Content-Type
- **WHEN** 请求未使用约定的表单 Content-Type
- **THEN** 系统拒绝将其作为有效 IM 业务消息处理

### Requirement: 所有业务处理前必须完成外层验签
系统 MUST 使用配置的 `signKey`，对除 `sign` 外所有非空外层表单参数按字典序拼接并计算 SHA1 签名，同时校验 `developerId` 和 `businessId`。

#### Scenario: 验签成功
- **WHEN** 计算签名与外层 `sign` 一致且开发者、业务 ID 匹配配置
- **THEN** 系统继续解析并保存消息

#### Scenario: 验签失败
- **WHEN** 签名缺失、不匹配或开发者、业务 ID 不匹配
- **THEN** 系统不保存业务消息并返回非 0 结果

#### Scenario: message 内包含 sig
- **WHEN** 业务 `message` 内还包含名为 `sig` 的字段
- **THEN** 系统可将其作为原始业务字段保存
- **AND** 系统不得使用该字段替代外层 `sign` 验签

### Requirement: 消息解析必须兼容对象和字符串 push_content
系统 SHALL 解析 `message.biz_type`、`message.extended_field` 和 `message.push_content`，并兼容 `push_content` 为 JSON 对象或包含转义 JSON 的字符串。

#### Scenario: push_content 为对象
- **WHEN** `message.push_content` 是 JSON 对象
- **THEN** 系统直接读取其中的消息字段

#### Scenario: push_content 为字符串
- **WHEN** `message.push_content` 是合法 JSON 字符串
- **THEN** 系统进行第二次 JSON 解析并读取同一组消息字段

#### Scenario: message 包含历史样例扩展字段
- **WHEN** `message` 内包含 `sig`、`app_id`、`app_poi_code`、`timestamp` 或其他未知字段
- **THEN** 系统继续解析已知字段并将未知字段保留在安全原始 JSON 中

### Requirement: 系统必须保存所有两方会话消息
对于 `biz_type=1`，系统 SHALL 保存商家和用户发送的文本、图片、其他和卡片消息，不以 `msg_source`、`msg_type`、`has_reply` 或 `order_id` 是否为零过滤入库。

#### Scenario: 保存用户未回复文本消息
- **WHEN** 消息为 `msg_source=2`、`msg_type=1`、`has_reply=1`
- **THEN** 系统保存完整消息事实

#### Scenario: 保存商家消息
- **WHEN** 消息为 `msg_source=1`
- **THEN** 系统同样保存消息，以便未来还原完整会话

#### Scenario: 保存非文本消息
- **WHEN** `msg_type` 为图片、其他或卡片
- **THEN** 系统保存消息类型、密文、场景和原始 payload

### Requirement: IM 消息表必须保存完整业务字段和处理状态
系统 SHALL 在 `mt_im_message` 中保存外层回调标识、门店与开发者字段、内部消息 ID、消息类型与来源、时间、用户、订单、场景、回复标记、密文、可用明文、hash、原始 JSON、解析状态、解密状态和安全错误摘要。

#### Scenario: 文本消息解析和解密成功
- **WHEN** 有效文本消息能够使用 `mt.im.secret` 解密
- **THEN** 系统保存原始密文、解密明文、内容 hash 和 `decrypt_status=SUCCESS`
- **AND** 系统不在普通应用日志输出消息正文

#### Scenario: 解密失败
- **WHEN** 密钥缺失、长度非法或密文无法解密
- **THEN** 系统仍保存已验签回调、原始密文和其他可解析字段
- **AND** 系统标记解密失败并保存不含密钥和正文的错误摘要

### Requirement: 系统必须使用双重幂等约束
系统 MUST 使用外层 `callback_msg_id` 对回调重投去重，并使用 `(app_id, platform_msg_id)` 对同一 IM 业务消息去重。

#### Scenario: 相同回调重复推送
- **WHEN** 相同外层 `msgId` 再次到达
- **THEN** 系统不创建第二条记录并按成功处理

#### Scenario: 不同回调包装同一业务消息
- **WHEN** 外层回调 ID 不同但 `app_id` 和内部 `msg_id` 相同
- **THEN** 系统不创建第二条业务消息并按成功处理

#### Scenario: 内部字段无法解析
- **WHEN** 系统能取得外层回调 ID 但不能取得内部消息 ID
- **THEN** 系统仍可按外层 ID 唯一保存解析失败记录

### Requirement: 消息必须按门店映射和隔离
系统 SHALL 优先使用内部 `app_poi_code` 匹配 `mt_shop.epoi_id`，并保留外层 `ePoiId` 与 `opBizCode` 作为辅助标识；消息查询必须始终应用 `shop_id` 数据范围。

#### Scenario: 成功匹配门店
- **WHEN** `app_poi_code` 对应一个已授权门店
- **THEN** 系统将消息关联到该门店 `shop_id`

#### Scenario: 暂时无法匹配门店
- **WHEN** 合法消息的门店标识尚未对应本地门店
- **THEN** 系统以空 `shop_id` 和 `SHOP_UNMATCHED` 状态保存消息
- **AND** 系统返回成功以避免消息丢失和无效重推

#### Scenario: 查询会话消息
- **WHEN** 后续业务按门店和 `open_user_id` 查询消息
- **THEN** 数据库使用 `(shop_id, open_user_id, message_cts)` 索引返回该门店会话
- **AND** 不返回其他门店消息

### Requirement: 回调响应必须以持久化结果为边界
系统 SHALL 仅在消息已持久化或确认幂等重复后返回 `{ "code":0,"message":"success" }`。

#### Scenario: 新消息持久化成功
- **WHEN** 数据库事务成功提交消息
- **THEN** 回调返回 code 0 和 success

#### Scenario: 数据库瞬时失败
- **WHEN** 系统无法保存消息且无法确认已存在
- **THEN** 回调返回非 0 结果，使美团可以重试

#### Scenario: 已保存但解析不完整
- **WHEN** 合法回调已保存，但解析、门店映射或解密失败
- **THEN** 系统保存失败状态并返回成功

### Requirement: 消息接收不得执行自动回复业务
回调线程和本变更的入库服务 MUST NOT 调用模型、算力扣减或消息发送接口。

#### Scenario: 收到自动回复候选消息
- **WHEN** 收到用户未回复文本消息
- **THEN** 系统仅完成验签、解析、落库和响应
- **AND** 不入队自动回复任务

### Requirement: 消息日志和响应必须脱敏
系统 MUST NOT 在普通日志、错误摘要或回调响应中输出 `signKey`、`mt.im.secret`、access token、完整密文、完整原始 payload 或解密明文。

#### Scenario: 记录成功回调
- **WHEN** 消息成功入库
- **THEN** 日志最多记录本地记录 ID、shopId、callbackMsgId、platformMsgId、消息类型、处理状态和耗时

#### Scenario: 记录失败回调
- **WHEN** 验签、解析或解密失败
- **THEN** 日志只记录安全错误码与可关联标识
- **AND** 不记录秘密或消息正文
