## Context

当前 H5 已经在 `pages.json`、底部 tabbar 和首页“查看日志”入口中保留了 `/pages/log/index`，但页面内容只是“评价记录”占位。老项目 `mtmini-agent/h5/src/pages/log/index.vue` 已有可复用的移动端日志体验：固定顶部说明、时间分组、分页加载、空态、底部 tabbar。

当前仓库的业务语义已经从推广出价变成评价自动回复。后端 `mt_comment` 保存了评价文本、图片字段、评分、自动回复状态、自动回复内容、自动回复时间、自动回复评论类型等字段，适合作为 H5 日志数据来源。H5 请求与登录态仍应收敛在 `src/utils/mt-auth.js`。

## Goals / Non-Goals

**Goals:**

- 在 H5 提供可用的评价自动回复日志页，让商家按时间查看当前门店记录。
- 页面展示顾客消息文本、图片列表、AI 回复文案、AI 分析文案。
- 顶部说明展示红标差评、绿标好评、灰色中评，并在每条日志中使用一致颜色标识。
- 支持分页加载、下拉刷新、空态、错误提示和底部 tabbar。
- 后端接口如需新增，必须按当前登录门店过滤，返回 H5 可直接消费的分页 VO。

**Non-Goals:**

- 不在本次实现真实 AI 分析生成链路，`analysisText` 暂时固定为“AI 分析结果”。
- 不新增或修改美团外部接口调用。
- 不改变自动回复任务的幂等、回复生成、扣算力和美团添加回复逻辑。
- 不引入新的前端状态管理或 UI 组件库。

## Decisions

1. **日志页复用老项目交互骨架，但改为评价回复内容结构。**

   复用固定顶部说明、`scroll-view` 分页、日期分组、空态和 footer 文案，可以降低移动端体验风险。旧页面中的“高峰期识别”和推广价格字段属于旧业务，不能迁移。

2. **后端返回 H5 专用日志 VO，而不是让 H5 直接拼装数据库字段。**

   建议接口形态为 `GET /api/h5/comment-reply/logs?pageNum=1&pageSize=20`，沿用老项目 `PageHelper.startPage(pageNum, pageSize)` 的页码分页方式，不做游标分页。接口返回分页对象及列表字段：
   `id`、`commentId`、`commentTime`、`replyTime`、`sentiment`、`score`、`customerMessage`、`imageUrls`、`aiReplyText`、`analysisText`、`autoReplyStatus`。
   这样 H5 不需要理解 `comment_pictures` 的存储格式，也不用在页面里兜底解析 `raw_payload_json`。

   查询范围为当前门店所有已保存的评价记录，不按 `auto_reply_status` 过滤。`PENDING`、`FAILED`、`SKIPPED` 或缺少回复内容的记录仍可展示，AI 回复区域用状态或兜底文案说明。

3. **评价类型优先使用 `auto_reply_sentiment`，缺失时用 `food_comment_score` 兜底。**

   新链路已经把评论类型保存为 `GOOD`、`NEUTRAL`、`BAD`。历史数据或未生成记录可能缺失该字段，此时按既有规则使用 `food_comment_score`：0-2 为 `BAD`，3 为 `NEUTRAL`，4-5 为 `GOOD`。两者都不可用时按 `NEUTRAL` 灰色展示，并保留记录可见性。

4. **图片列表由后端规范化为数组，H5 负责缩略图展示与预览。**

   `comment_pictures` 可能来自美团原始字段，格式不应泄漏给页面。后端服务负责解析出可访问 URL 数组；H5 使用横向缩略图列表，点击调用 `uni.previewImage`。

5. **AI 分析文案作为接口字段返回，当前固定为“AI 分析结果”。**

   虽然第一版可以在 H5 写死，但放入 VO 字段能为后续真实分析结果接入保留兼容路径。后端无字段时统一返回固定文案，H5 仅做空值兜底。

## Risks / Trade-offs

- [Risk] `comment_pictures` 存储格式可能不稳定或包含不可访问 URL → 后端解析失败时返回空数组，H5 仍展示文本和回复内容。
- [Risk] 历史评价缺少 `auto_reply_content` 或还未自动回复成功 → 页面使用“暂无 AI 回复”兜底，并可展示处理状态，避免空白。
- [Risk] 页码分页在深页查询时可能不如游标分页稳定 → 第一版沿用老项目和当前后台常用的 `PageHelper` 页码分页，默认 `pageSize=20`；后续日志量显著增长时再升级游标分页。
- [Risk] UI 直接照搬旧项目会残留推广语义 → 实现时移除出价、高峰期、推广费用等旧字段，只保留适合评价日志的布局模式。
