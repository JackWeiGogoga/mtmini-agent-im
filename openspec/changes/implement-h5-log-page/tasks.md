## 1. 后端日志接口

- [x] 1.1 确认是否已有 H5 可用的评价自动回复日志分页接口；如无，则新增 `GET /api/h5/comment-reply/logs?pageNum=1&pageSize=20`。
- [x] 1.2 新增或复用 service 方法，沿用老项目 PageHelper 页码分页，按当前登录门店 `shop_id` 查询 `mt_comment` 全部已保存评价记录，按自动回复时间、评价时间或创建时间倒序分页。
- [x] 1.3 新增 H5 日志 VO，返回 `id`、`commentId`、`commentTime`、`replyTime`、`sentiment`、`score`、`customerMessage`、`addComment`、`imageUrls`、`aiReplyText`、`analysisText`、`autoReplyStatus`，不按 `autoReplyStatus` 过滤。
- [x] 1.4 在后端规范化评价类型：优先使用 `auto_reply_sentiment`，缺失时按 `food_comment_score` 0-2/3/4-5 映射为 `BAD`/`NEUTRAL`/`GOOD`。
- [x] 1.5 在后端解析 `comment_pictures` 为图片 URL 数组；解析失败或无图片时返回空数组。
- [x] 1.6 当无真实 AI 分析字段时，后端返回固定 `analysisText` 为“AI 分析结果”。

## 2. H5 请求封装

- [x] 2.1 在 `h5/src/utils/mt-auth.js` 新增评价回复日志分页请求方法，继续复用统一 token 与错误处理。
- [x] 2.2 确认底部 tabbar 和首页“查看日志”入口仍指向 `/pages/log/index`，不引入新的页面路径。

## 3. H5 日志页面

- [x] 3.1 将 `h5/src/pages/log/index.vue` 从占位页改为完整日志页，复用老项目日志页的固定顶部、滚动列表、日期分组、footer 和底部 tabbar体验。
- [x] 3.2 顶部说明区展示红标差评、绿标好评、灰色中评，并移除老项目推广出价和高峰期识别语义。
- [x] 3.3 每条日志展示时间、评价类型颜色标识、星级评分、顾客消息文本、追评文本、图片缩略图列表、AI 回复文案和 AI 分析文案。
- [x] 3.4 实现图片预览交互，点击缩略图时调用 `uni.previewImage` 预览当前记录图片。
- [x] 3.5 实现首次加载、下拉刷新、上滑加载更多、空态、加载中、无更多数据和接口失败提示。
- [x] 3.6 对缺失字段提供兜底文案：无顾客消息、无 AI 回复、无 AI 分析或无时间时页面仍保持可读。

## 4. 验证

- [x] 4.1 运行后端编译或最接近的测试，确认新增接口、service、mapper 和 VO 编译通过。
- [x] 4.2 运行 H5 构建或 lint 命令，确认日志页和请求封装无语法错误。
- [ ] 4.3 手动验证 H5 日志页：首页入口、底部 tabbar、分页加载、下拉刷新、空态、图片预览和网络失败提示。
- [x] 4.4 核对本次没有新增或修改美团外部调用；如实现过程中实际改动美团调用，则同步更新 `docs/美团接口调用索引.md`。
