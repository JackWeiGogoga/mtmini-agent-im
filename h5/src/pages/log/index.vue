<template>
  <view class="page" @tap="hidePeakWindowTips">
    <view class="page-fixed">
      <app-subpage-header title="日志" />

      <view class="legend-bar">
        <text class="legend-title">说明：</text>
        <view class="legend-item">
          <view class="legend-dot legend-dot-green"></view>
          <text class="legend-text">减少出价</text>
        </view>
        <view class="legend-item">
          <view class="legend-dot legend-dot-red"></view>
          <text class="legend-text">增加出价</text>
        </view>
        <view class="legend-item">
          <view class="legend-dot legend-dot-gray"></view>
          <text class="legend-text">持平出价</text>
        </view>
        <view
          class="peak-status-pill"
          :class="{ 'peak-status-pill-locked': !autoTimeModeAvailable }"
          @tap.stop="handlePeakStatusTap"
        >
          <text v-if="autoTimeModeAvailable">AI识别:高峰期</text>
          <text v-else-if="unlockingAutoTimeMode">识别中...</text>
          <text v-else>
            <text>AI识别:</text>
            <text class="peak-status-underline">待解锁</text>
          </text>
        </view>
        <view
          v-if="peakWindowTipsVisible"
          class="peak-window-tips"
          @tap.stop
        >
          <view class="peak-window-tips-arrow"></view>
          <text class="peak-window-tips-title">高峰状态</text>
          <view class="peak-window-tips-row">
            <text class="peak-window-tips-label">当前状态</text>
            <text class="peak-window-tips-value">{{ peakMatchedText }}</text>
          </view>
          <view class="peak-window-tips-row">
            <text class="peak-window-tips-label">当前日型</text>
            <text class="peak-window-tips-value">{{ autoTimeModeDayTypeText }}</text>
          </view>
          <view class="peak-window-tips-row peak-window-tips-row-top">
            <text class="peak-window-tips-label">高峰时段</text>
            <view class="peak-window-tips-times">
              <text
                v-for="windowText in displayedPeakWindowTips"
                :key="windowText"
                class="peak-window-tips-time"
                >{{ windowText }}</text
              >
            </view>
          </view>
        </view>
      </view>
    </view>

    <scroll-view
      class="log-scroll"
      scroll-y
      @scrolltolower="handleScrollToLower"
    >
      <view class="content">
        <view v-for="group in logGroups" :key="group.date" class="log-group">
          <text class="log-date">{{ group.date }}</text>
          <view
            v-for="(item, index) in group.items"
            :key="`${group.date}-${index}`"
            class="log-item"
          >
            <view
              class="log-marker"
              :class="{
                'log-marker-increase': item.type === 'increase',
                'log-marker-decrease': item.type === 'decrease',
                'log-marker-steady': item.type === 'steady',
              }"
            ></view>
            <view class="log-meta">
              <text class="log-time">{{ item.time }}</text>
              <text
                class="log-price"
                :class="{
                  'log-price-increase': item.type === 'increase',
                  'log-price-decrease': item.type === 'decrease',
                  'log-price-steady': item.type === 'steady',
                }"
              >
                {{ item.price }}
              </text>
            </view>
            <view class="log-body">
              <app-markdown-preview
                :markdown="item.markdownMessage"
                :fallback-text="item.message"
              />
            </view>
          </view>
        </view>

        <view v-if="!isLoading && !logItems.length" class="log-empty">
          <text class="log-empty-text">最近 7 天暂无调价日志</text>
        </view>

        <text class="log-footer">{{ footerText }}</text>
      </view>
    </scroll-view>

    <app-tabbar active-tab="log" />
  </view>
</template>

<script>
import AppMarkdownPreview from "../../components/app-markdown-preview.vue";
import AppTabbar from "../../components/app-tabbar.vue";
import AppSubpageHeader from "../../components/app-subpage-header.vue";
import {
  getMtH5PromotionSettings,
  getMtH5PromotionLogs,
  markMtH5PromotionLogsRead,
  unlockMtH5AutoTimeMode,
} from "../../utils/mt-auth";
import { showErrorToast } from "../../utils/ui";

export default {
  components: {
    AppMarkdownPreview,
    AppTabbar,
    AppSubpageHeader,
  },
  data() {
    return {
      logItems: [],
      isLoading: false,
      isLoadingMore: false,
      pageNum: 1,
      pageSize: 20,
      hasNextPage: true,
      autoTimeModeAvailable: false,
      autoTimeModeHintText: "正常营业5天后解锁",
      autoTimeModePeakWindows: [],
      autoTimeModeDayTypeText: "工作日",
      autoTimeModePeakMatched: false,
      peakWindowTipsVisible: false,
      unlockingAutoTimeMode: false,
    };
  },
  computed: {
    peakStatusText() {
      if (this.autoTimeModeAvailable) {
        return "AI识别:高峰期";
      }
      return this.unlockingAutoTimeMode ? "识别中..." : "AI识别:待解锁";
    },
    displayedPeakWindowTips() {
      return this.autoTimeModePeakWindows.length
        ? this.autoTimeModePeakWindows
        : ["暂无高峰时段"];
    },
    peakMatchedText() {
      return this.autoTimeModePeakMatched ? "高峰期" : "非高峰期";
    },
    logGroups() {
      const groups = [];
      const groupMap = {};
      this.logItems.forEach((item) => {
        const date = this.formatDateText(item.slotTime);
        if (!groupMap[date]) {
          groupMap[date] = {
            date,
            items: [],
          };
          groups.push(groupMap[date]);
        }
        groupMap[date].items.push({
          ...item,
          time: this.formatTimeText(item.slotTime),
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
        return "日志保留7天内信息";
      }
      if (this.hasNextPage) {
        return "上滑加载更多日志";
      }
      return "日志保留7天内信息";
    },
  },
  onShow() {
    this.markLogsRead();
    this.loadPeakStatus();
    this.loadLogs(true);
  },
  onPullDownRefresh() {
    this.loadLogs(true).finally(() => {
      uni.stopPullDownRefresh();
    });
  },
  methods: {
    async markLogsRead() {
      try {
        await markMtH5PromotionLogsRead();
      } catch (error) {
        // 已读状态失败不阻断日志浏览。
      }
    },
    async loadPeakStatus() {
      try {
        const settings = await getMtH5PromotionSettings();
        this.applyPeakSettings(settings);
      } catch (error) {
        // 高峰期状态失败不阻断日志浏览。
      }
    },
    async handlePeakStatusTap() {
      if (this.autoTimeModeAvailable) {
        if (!this.autoTimeModePeakWindows.length) {
          uni.showLoading({
            title: "加载中...",
            mask: true,
          });
          try {
            const settings = await getMtH5PromotionSettings();
            this.applyPeakSettings(settings);
          } catch (error) {
            showErrorToast(error, "高峰期加载失败");
            return;
          } finally {
            uni.hideLoading();
          }
        }
        this.peakWindowTipsVisible = !this.peakWindowTipsVisible;
        return;
      }
      if (this.unlockingAutoTimeMode) {
        return;
      }
      this.unlockingAutoTimeMode = true;
      uni.showLoading({
        title: "AI正在识别高峰期...",
        mask: true,
      });
      try {
        const settings = await unlockMtH5AutoTimeMode();
        this.applyPeakSettings(settings);
        const message =
          settings?.autoTimeModeUnlockMessage ||
          (this.autoTimeModeAvailable
            ? "AI识别高峰期已解锁"
            : "数据不足，功能开启5天后解锁！");
        uni.showToast({ title: message, icon: "none" });
      } catch (error) {
        uni.showToast({
          title: "数据不足，功能开启5天后解锁！",
          icon: "none",
        });
      } finally {
        uni.hideLoading();
        this.unlockingAutoTimeMode = false;
      }
    },
    applyPeakSettings(settings = {}) {
      this.autoTimeModeAvailable = Boolean(settings?.autoTimeModeAvailable);
      this.autoTimeModeHintText =
        settings?.autoTimeModeHintText || "正常营业5天后解锁";
      this.autoTimeModePeakWindows = Array.isArray(
        settings?.autoTimeModePeakWindows,
      )
        ? settings.autoTimeModePeakWindows.filter(Boolean)
        : [];
      this.autoTimeModeDayTypeText = settings?.autoTimeModeDayTypeText || "工作日";
      this.autoTimeModePeakMatched = Boolean(settings?.autoTimeModePeakMatched);
    },
    hidePeakWindowTips() {
      this.peakWindowTipsVisible = false;
    },
    async loadLogs(reset = false) {
      if (this.isLoading || this.isLoadingMore) {
        return;
      }

      const nextPageNum = reset ? 1 : this.pageNum;
      if (!reset && !this.hasNextPage) {
        return;
      }

      if (reset) {
        this.isLoading = true;
      } else {
        this.isLoadingMore = true;
      }

      try {
        const page = await getMtH5PromotionLogs(nextPageNum, this.pageSize);
        const nextList = Array.isArray(page?.list) ? page.list : [];
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
    formatDateText(slotTime) {
      const text = String(slotTime || "");
      const datePart = text.slice(0, 10);
      return datePart ? datePart.replace(/-/g, ".") : "--";
    },
    formatTimeText(slotTime) {
      const text = String(slotTime || "");
      return text.length >= 16 ? text.slice(11, 16) : "--:--";
    },
  },
};
</script>

<style>
page {
  background: #f6f6f8;
}
</style>

<style scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-sizing: border-box;
}

.content {
  padding-bottom: calc(env(safe-area-inset-bottom) + 132rpx);
  background: #f6f6f8;
}

.legend-bar {
  position: relative;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16rpx;
  padding: 22rpx 28rpx 18rpx;
  background: #ffffff;
  flex-shrink: 0;
}

.legend-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #3c3d42;
}

.legend-item {
  display: flex;
  align-items: center;
}

.legend-dot {
  width: 10rpx;
  height: 28rpx;
  margin-right: 8rpx;
  border-radius: 999rpx;
}

.legend-dot-green {
  background: #77c940;
}

.legend-dot-red {
  background: #ff2a2a;
}

.legend-dot-gray {
  background: #a9a9ab;
}

.legend-text {
  font-size: 26rpx;
  color: #6b6c72;
}

.peak-status-pill {
  margin-left: auto;
  height: 42rpx;
  padding: 0 14rpx;
  border-radius: 8rpx;
  background: #ffe8e5;
  color: #d13c32;
  font-size: 22rpx;
  font-weight: 700;
  line-height: 42rpx;
  white-space: nowrap;
}

.peak-status-pill-locked {
  background: transparent;
  color: #2f2f33;
}

.peak-status-underline {
  text-decoration: underline;
}

.peak-window-tips {
  position: absolute;
  top: 76rpx;
  right: 28rpx;
  z-index: 5;
  width: 340rpx;
  padding: 20rpx 22rpx 18rpx;
  border-radius: 10rpx;
  background: rgba(16, 33, 48, 0.94);
  box-shadow: 0 12rpx 28rpx rgba(20, 28, 38, 0.18);
  box-sizing: border-box;
}

.peak-window-tips-arrow {
  position: absolute;
  top: -14rpx;
  right: 70rpx;
  width: 0;
  height: 0;
  border-right: 14rpx solid transparent;
  border-bottom: 14rpx solid rgba(16, 33, 48, 0.94);
  border-left: 14rpx solid transparent;
}

.peak-window-tips-title {
  display: block;
  margin-bottom: 14rpx;
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 800;
  line-height: 1.2;
}

.peak-window-tips-row {
  display: flex;
  gap: 18rpx;
  margin-top: 10rpx;
}

.peak-window-tips-row-top {
  align-items: flex-start;
}

.peak-window-tips-label {
  width: 108rpx;
  flex-shrink: 0;
  color: rgba(255, 255, 255, 0.62);
  font-size: 22rpx;
  line-height: 1.25;
}

.peak-window-tips-value,
.peak-window-tips-time {
  color: #ffffff;
  font-size: 22rpx;
  font-weight: 600;
  line-height: 1.25;
}

.peak-window-tips-times {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 8rpx;
}

.peak-window-tips-time {
  display: block;
}

.log-group {
  padding: 8rpx 20rpx 0;
}

.log-scroll {
  flex: 1;
  width: 100%;
  height: 0;
  min-height: 0;
}

.log-date {
  display: block;
  padding: 22rpx 0 12rpx;
  text-align: center;
  font-size: 28rpx;
  color: #b1b1b4;
}

.log-item {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  margin-bottom: 18rpx;
  padding: 22rpx 20rpx;
  border-radius: 24rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 24rpx rgba(31, 35, 41, 0.05);
}

.log-marker {
  width: 8rpx;
  height: 72rpx;
  margin-top: 2rpx;
  border-radius: 999rpx;
  flex-shrink: 0;
}

.log-marker-increase {
  background: #ff2a2a;
}

.log-marker-decrease {
  background: #77c940;
}

.log-marker-steady {
  background: #a9a9ab;
}

.log-meta {
  width: 100rpx;
  flex-shrink: 0;
}

.log-time {
  display: block;
  padding-top: 4rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #4f5054;
}

.log-price {
  display: block;
  margin-top: 8rpx;
  font-size: 26rpx;
  font-weight: 700;
}

.log-price-increase {
  color: #ff3f3f;
}

.log-price-decrease {
  color: #52bc62;
}

.log-price-steady {
  color: #9b9ba0;
}

.log-body {
  flex: 1;
  min-width: 0;
  padding-right: 12rpx;
}

.log-footer {
  display: block;
  padding: 26rpx 0 0;
  text-align: center;
  font-size: 26rpx;
  color: #b8b8b8;
}

.log-empty {
  padding: 80rpx 32rpx 24rpx;
  text-align: center;
}

.log-empty-text {
  font-size: 28rpx;
  color: #a6a7ad;
}
</style>
