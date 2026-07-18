<template>
  <view class="page">
    <view class="page-fixed">
      <app-subpage-header title="日志" />

      <view class="legend-bar">
        <text class="legend-title">说明：</text>
        <view class="legend-item">
          <view class="legend-dot legend-dot-red"></view>
          <text class="legend-text">红标差评</text>
        </view>
        <view class="legend-item">
          <view class="legend-dot legend-dot-green"></view>
          <text class="legend-text">绿标好评</text>
        </view>
        <view class="legend-item">
          <view class="legend-dot legend-dot-gray"></view>
          <text class="legend-text">灰色中评/未评级</text>
        </view>
      </view>
    </view>

    <scroll-view class="log-scroll" scroll-y @scrolltolower="handleScrollToLower">
      <view class="content">
        <view v-for="group in logGroups" :key="group.date" class="log-group">
          <text class="log-date">{{ group.date }}</text>

          <view
            v-for="item in group.items"
            :key="item.id || item.commentId"
            class="log-item"
          >
            <view class="log-main-row">
              <view class="log-marker" :class="markerClass(item)"></view>
              <view class="log-meta">
                <text class="log-time">{{ item.displayTime }}</text>
                <text class="log-status" :class="statusClass(item)">{{
                  sentimentText(item)
                }}</text>
                <text class="log-score">{{ scoreText(item) }}</text>
              </view>
              <view class="log-body">
                <view v-if="isLogExpanded(item)" class="log-detail">
                  <view class="log-section">
                    <text class="section-label">顾客消息</text>
                    <text class="section-text">{{ customerMessageText(item) }}</text>
                  </view>

                  <view
                    v-if="addCommentText(item)"
                    class="log-section add-comment-section"
                  >
                    <text class="section-label">顾客追评</text>
                    <text class="section-text">{{ addCommentText(item) }}</text>
                  </view>

                  <scroll-view
                    v-if="item.imageUrls && item.imageUrls.length"
                    class="image-scroll"
                    scroll-x
                  >
                    <view class="image-list">
                      <image
                        v-for="(imageUrl, imageIndex) in item.imageUrls"
                        :key="`${item.id || item.commentId}-${imageIndex}`"
                        class="comment-image"
                        :src="imageUrl"
                        mode="aspectFill"
                        @tap.stop="previewImages(item.imageUrls, imageIndex)"
                      ></image>
                    </view>
                  </scroll-view>

                  <view class="log-section reply-section">
                    <text class="section-label">AI 回复</text>
                    <text class="section-text">{{ aiReplyText(item) }}</text>
                  </view>

                  <view class="log-section analysis-section">
                    <text class="section-label">AI 分析</text>
                    <text class="section-text">{{ analysisText(item) }}</text>
                  </view>
                </view>
                <view v-else class="log-preview">
                  <text class="log-preview-text">{{
                    customerMessageText(item)
                  }}</text>
                </view>

                <view
                  class="log-detail-toggle"
                  @tap.stop="toggleLogExpanded(item)"
                >
                  <text>{{
                    isLogExpanded(item) ? "收起详情" : "展开详情"
                  }}</text>
                  <text
                    class="log-detail-toggle-arrow"
                    :class="{
                      'log-detail-toggle-arrow-expanded': isLogExpanded(item),
                    }"
                    >⌄</text
                  >
                </view>
              </view>
            </view>
          </view>
        </view>

        <view v-if="!isLoading && !logItems.length" class="log-empty">
          <text class="log-empty-title">暂无评价回复记录</text>
          <text class="log-empty-text">有新的评价同步后，会在这里展示处理日志。</text>
        </view>

        <text class="log-footer">{{ footerText }}</text>
      </view>
    </scroll-view>

    <app-tabbar active-tab="log" />
  </view>
</template>

<script>
import AppSubpageHeader from "../../components/app-subpage-header.vue";
import AppTabbar from "../../components/app-tabbar.vue";
import {
  getMtH5CommentReplyLogs,
  reportMtH5BehaviorEvent,
  reportMtH5PageView,
} from "../../utils/mt-auth";
import { showErrorToast } from "../../utils/ui";

export default {
  components: {
    AppSubpageHeader,
    AppTabbar,
  },
  data() {
    return {
      logItems: [],
      isLoading: false,
      isLoadingMore: false,
      pageNum: 1,
      pageSize: 20,
      hasNextPage: true,
      expandedLogKeys: {},
      pageEntrySource: "DIRECT",
    };
  },
  computed: {
    logGroups() {
      const groups = [];
      const groupMap = {};
      this.logItems.forEach((item) => {
        const timeText = this.resolveTimeText(item);
        const date = this.formatDateText(timeText);
        if (!groupMap[date]) {
          groupMap[date] = {
            date,
            items: [],
          };
          groups.push(groupMap[date]);
        }
        groupMap[date].items.push({
          ...item,
          displayTime: this.formatTimeText(timeText),
        });
      });
      return groups;
    },
    footerText() {
      if (this.isLoading) {
        return "日志加载中...";
      }
      if (this.isLoadingMore) {
        return "加载更多中...";
      }
      if (!this.logItems.length) {
        return "日志保留已同步评价记录";
      }
      if (this.hasNextPage) {
        return "上滑加载更多日志";
      }
      return "已展示全部评价记录";
    },
  },
  onLoad(options) {
    this.pageEntrySource = options?.entrySource || "DIRECT";
  },
  onShow() {
    reportMtH5PageView("LOG", this.pageEntrySource);
    this.loadLogs(true);
  },
  onPullDownRefresh() {
    this.loadLogs(true).finally(() => {
      uni.stopPullDownRefresh();
    });
  },
  methods: {
    async loadLogs(reset = false) {
      if (this.isLoading || this.isLoadingMore) {
        return;
      }
      if (!reset && !this.hasNextPage) {
        return;
      }

      const nextPageNum = reset ? 1 : this.pageNum;
      if (reset) {
        this.isLoading = true;
      } else {
        this.isLoadingMore = true;
      }

      try {
        const page = await getMtH5CommentReplyLogs(nextPageNum, this.pageSize);
        const nextList = Array.isArray(page?.list) ? page.list : [];
        if (reset) {
          this.expandedLogKeys = {};
        }
        this.logItems = reset ? nextList : [...this.logItems, ...nextList];
        this.pageNum = Number(page?.pageNum || nextPageNum) + 1;
        this.hasNextPage = Boolean(page?.hasNextPage);
      } catch (error) {
        showErrorToast(error, "日志加载失败");
      } finally {
        this.isLoading = false;
        this.isLoadingMore = false;
      }
    },
    handleScrollToLower() {
      this.loadLogs(false);
    },
    logItemKey(item) {
      return String(
        item?.id ||
          item?.commentId ||
          `${this.resolveTimeText(item)}-${item?.customerMessage || ""}`,
      );
    },
    isLogExpanded(item) {
      return Boolean(this.expandedLogKeys[this.logItemKey(item)]);
    },
    toggleLogExpanded(item) {
      const key = this.logItemKey(item);
      const willExpand = !this.expandedLogKeys[key];
      this.expandedLogKeys = {
        ...this.expandedLogKeys,
        [key]: willExpand,
      };
      if (willExpand) {
        reportMtH5BehaviorEvent("H5_LOG_DETAIL_EXPAND", {
          sentiment: this.normalizedSentiment(item),
          hasImages: Boolean(item?.imageUrls?.length),
        });
      }
    },
    previewImages(imageUrls, imageIndex) {
      if (!Array.isArray(imageUrls) || !imageUrls.length) {
        return;
      }
      uni.previewImage({
        urls: imageUrls,
        current: imageUrls[imageIndex] || imageUrls[0],
      });
    },
    resolveTimeText(item) {
      return item?.commentTime || item?.replyTime || "";
    },
    formatDateText(timeText) {
      const text = String(timeText || "");
      if (text.length >= 10) {
        return text.slice(0, 10);
      }
      return "--";
    },
    formatTimeText(timeText) {
      const text = String(timeText || "");
      if (text.length >= 10) {
        return text.slice(0, 10);
      }
      return "--";
    },
    normalizedSentiment(item) {
      const sentiment = String(item?.sentiment || "").toUpperCase();
      if (sentiment === "GOOD" || sentiment === "BAD" || sentiment === "NEUTRAL" || sentiment === "UNKNOWN") {
        return sentiment;
      }
      const score = Number(item?.score);
      if (score >= 1 && score <= 2) {
        return "BAD";
      }
      if (score === 3) {
        return "NEUTRAL";
      }
      if (score >= 4 && score <= 5) {
        return "GOOD";
      }
      return "UNKNOWN";
    },
    sentimentText(item) {
      const sentiment = this.normalizedSentiment(item);
      if (sentiment === "BAD") {
        return "差评";
      }
      if (sentiment === "GOOD") {
        return "好评";
      }
      if (sentiment === "NEUTRAL") {
        return "中评";
      }
      return "未评级";
    },
    scoreText(item) {
      const rawScore = item?.score;
      const score = Number(item?.score);
      if (rawScore === null || rawScore === undefined || !Number.isFinite(score) || score < 1 || score > 5) {
        return "--星";
      }
      return `${"★".repeat(score)}${"☆".repeat(5 - score)}`;
    },
    markerClass(item) {
      return {
        "log-marker-bad": this.normalizedSentiment(item) === "BAD",
        "log-marker-good": this.normalizedSentiment(item) === "GOOD",
        "log-marker-neutral": ["NEUTRAL", "UNKNOWN"].includes(this.normalizedSentiment(item)),
      };
    },
    statusClass(item) {
      return {
        "log-status-bad": this.normalizedSentiment(item) === "BAD",
        "log-status-good": this.normalizedSentiment(item) === "GOOD",
        "log-status-neutral": ["NEUTRAL", "UNKNOWN"].includes(this.normalizedSentiment(item)),
      };
    },
    customerMessageText(item) {
      return item?.customerMessage || "暂无顾客消息";
    },
    addCommentText(item) {
      return item?.addComment || "";
    },
    aiReplyText(item) {
      return item?.aiReplyText || this.replyStatusText(item?.autoReplyStatus);
    },
    analysisText(item) {
      return item?.analysisText || "AI 分析结果";
    },
    replyStatusText(status) {
      const normalizedStatus = String(status || "").toUpperCase();
      if (normalizedStatus === "PENDING") {
        return "暂无 AI 回复，等待处理";
      }
      if (normalizedStatus === "FAILED") {
        return "暂无 AI 回复，处理失败";
      }
      if (normalizedStatus === "SKIPPED") {
        return "暂无 AI 回复，已跳过";
      }
      return "暂无 AI 回复";
    },
  },
};
</script>

<style>
page {
  background: #f6f7f9;
}
</style>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-sizing: border-box;
  background: #f6f7f9;
}

.page-fixed {
  flex-shrink: 0;
}

.content {
  padding-bottom: calc(env(safe-area-inset-bottom) + 132rpx);
  background: #f6f7f9;
}

.legend-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16rpx;
  padding: 22rpx 28rpx 18rpx;
  background: #ffffff;
  border-bottom: 2rpx solid rgba(230, 232, 236, 0.7);
  box-sizing: border-box;
}

.legend-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #30323a;
}

.legend-item {
  display: flex;
  align-items: center;
  min-width: 0;
}

.legend-dot {
  width: 10rpx;
  height: 28rpx;
  margin-right: 8rpx;
  border-radius: 999rpx;
}

.legend-dot-red {
  background: #e54d42;
}

.legend-dot-green {
  background: #28a65a;
}

.legend-dot-gray {
  background: #9a9da5;
}

.legend-text {
  font-size: 26rpx;
  color: #646873;
  white-space: nowrap;
}

.log-scroll {
  flex: 1;
  width: 100%;
  height: 0;
  min-height: 0;
}

.log-group {
  padding: 8rpx 22rpx 0;
}

.log-date {
  display: block;
  padding: 22rpx 0 12rpx;
  text-align: center;
  font-size: 26rpx;
  color: #9da1aa;
}

.log-item {
  margin-bottom: 18rpx;
  padding: 22rpx 20rpx;
  border-radius: 18rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 24rpx rgba(31, 35, 41, 0.05);
  box-sizing: border-box;
}

.log-main-row {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
}

.log-marker {
  width: 8rpx;
  min-height: 80rpx;
  border-radius: 999rpx;
  align-self: stretch;
  flex-shrink: 0;
}

.log-marker-bad {
  background: #e54d42;
}

.log-marker-good {
  background: #28a65a;
}

.log-marker-neutral {
  background: #9a9da5;
}

.log-meta {
  width: 142rpx;
  flex-shrink: 0;
}

.log-time {
  display: block;
  padding-top: 2rpx;
  font-size: 22rpx;
  font-weight: 700;
  color: #32353d;
  white-space: nowrap;
}

.log-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 68rpx;
  height: 36rpx;
  margin-top: 10rpx;
  padding: 0 10rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 700;
  line-height: 36rpx;
  box-sizing: border-box;
}

.log-status-bad {
  color: #c7352d;
  background: #fff0ee;
}

.log-status-good {
  color: #178145;
  background: #eaf8ef;
}

.log-status-neutral {
  color: #686d78;
  background: #f0f1f3;
}

.log-score {
  display: block;
  margin-top: 10rpx;
  font-size: 19rpx;
  line-height: 1.25;
  color: #c18a16;
  white-space: nowrap;
}

.log-body {
  flex: 1;
  min-width: 0;
}

.log-preview {
  padding: 16rpx 18rpx;
  border-radius: 14rpx;
  background: #f8f9fb;
  box-sizing: border-box;
}

.log-preview-text {
  display: -webkit-box;
  overflow: hidden;
  color: #4a4d57;
  font-size: 27rpx;
  line-height: 1.55;
  text-overflow: ellipsis;
  word-break: break-word;
  white-space: pre-wrap;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.log-detail-toggle {
  display: flex;
  align-items: center;
  width: fit-content;
  gap: 6rpx;
  margin-top: 14rpx;
  color: #1270ff;
  font-size: 24rpx;
  line-height: 1.4;
}

.log-detail-toggle-arrow {
  display: inline-block;
  font-size: 26rpx;
  line-height: 1;
  transition: transform 0.2s ease;
}

.log-detail-toggle-arrow-expanded {
  transform: rotate(180deg);
}

.log-section {
  padding: 16rpx 18rpx;
  border-radius: 14rpx;
  background: #f8f9fb;
  box-sizing: border-box;
}

.log-section + .log-section {
  margin-top: 14rpx;
}

.section-label {
  display: block;
  margin-bottom: 8rpx;
  font-size: 23rpx;
  font-weight: 700;
  color: #6f7480;
}

.section-text {
  display: block;
  font-size: 27rpx;
  line-height: 1.55;
  color: #252932;
  word-break: break-word;
  white-space: pre-wrap;
}

.image-scroll {
  width: 100%;
  margin: 14rpx 0;
  white-space: nowrap;
}

.image-list {
  display: inline-flex;
  gap: 12rpx;
  padding-right: 8rpx;
}

.comment-image {
  width: 132rpx;
  height: 132rpx;
  border-radius: 12rpx;
  background: #eceff3;
  flex-shrink: 0;
}

.reply-section {
  background: #fff8e8;
}

.add-comment-section {
  margin-top: 14rpx;
  background: #fff5f1;
}

.analysis-section {
  background: #eef6ff;
}

.log-footer {
  display: block;
  padding: 26rpx 0 0;
  text-align: center;
  font-size: 26rpx;
  color: #a7abb4;
}

.log-empty {
  padding: 92rpx 32rpx 24rpx;
  text-align: center;
}

.log-empty-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #4b515d;
}

.log-empty-text {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 1.45;
  color: #9499a4;
}
</style>
