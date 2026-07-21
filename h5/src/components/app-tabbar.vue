<template>
  <view class="tabbar">
    <view
      v-for="item in tabItems"
      :key="item.key"
      class="tab-item"
      @tap="handleTabClick(item)"
    >
      <view class="tab-icon-wrap">
        <image class="tab-icon" :src="item.currentIcon" mode="aspectFit"></image>
        <view v-if="item.dot" class="tab-dot"></view>
      </view>
      <text class="tab-label" :class="{ 'tab-label-active': item.active }">{{
        item.label
      }}</text>
      <text v-if="item.badgeText" class="tab-badge">{{ item.badgeText }}</text>
    </view>
  </view>
</template>

<script>
import { buildTabbarItems } from "../constants/tabbar";
import {
  getMtH5TabStatus,
  reportMtH5BehaviorEvent,
} from "../utils/mt-auth";
import { openMtExternalPage } from "../utils/ui";

export default {
  props: {
    activeTab: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      tabStatus: {
        powerInsufficient: false,
        powerStatusText: "",
      },
    };
  },
  computed: {
    tabItems() {
      return buildTabbarItems(this.activeTab).map((item) => ({
        ...item,
        dot: false,
        badgeText:
          item.key === "power" && this.tabStatus.powerInsufficient
            ? this.tabStatus.powerStatusText || "不足"
            : "",
      }));
    },
  },
  mounted() {
    this.refreshTabStatus();
  },
  watch: {
    activeTab() {
      this.refreshTabStatus();
    },
  },
  methods: {
    async refreshTabStatus() {
      try {
        const status = await getMtH5TabStatus();
        this.tabStatus = {
          powerInsufficient: Boolean(status?.powerInsufficient),
          powerStatusText: status?.powerStatusText || "",
        };
      } catch (error) {
        // Tab 状态是轻量提醒，失败时不阻断页面主流程。
      }
    },
    async handleTabClick(item) {
      if (item.key === "support") {
        reportMtH5BehaviorEvent("H5_SUPPORT_ENTRY_CLICK", {
          entrySource: "TAB",
        });
      }
      if (item.externalUrl) {
        reportMtH5BehaviorEvent("H5_EXTERNAL_LINK_CLICK", {
          targetCode: "MORE_ACTIVITY",
          entrySource: "TAB",
        });
        openMtExternalPage(item.externalUrl);
        return;
      }
      if (!item.path || item.active) {
        return;
      }
      const joiner = item.path.includes("?") ? "&" : "?";
      uni.redirectTo({
        url: `${item.path}${joiner}entrySource=TAB`,
      });
    },
  },
};
</script>

<style scoped>
.tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 16rpx 18rpx calc(env(safe-area-inset-bottom) + 12rpx);
  background: rgba(255, 255, 255, 0.96);
  border-top: 2rpx solid rgba(236, 236, 236, 0.95);
  backdrop-filter: blur(12rpx);
  box-sizing: border-box;
}

.tab-item {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  min-width: 0;
}

.tab-icon {
  width: 46rpx;
  height: 46rpx;
}

.tab-icon-wrap {
  position: relative;
  width: 46rpx;
  height: 46rpx;
  margin-bottom: 8rpx;
}

.tab-dot {
  position: absolute;
  right: -4rpx;
  top: -4rpx;
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #ff2a2a;
  border: 3rpx solid #ffffff;
  box-sizing: border-box;
}

.tab-label {
  font-size: 22rpx;
  color: #303030;
  font-weight: 600;
}

.tab-label-active {
  color: #efc548;
}

.tab-badge {
  position: absolute;
  top: 0;
  right: 24rpx;
  padding: 2rpx 8rpx;
  border-radius: 999rpx;
  background: #ff4a4a;
  color: #ffffff;
  font-size: 18rpx;
  line-height: 1.2;
  font-weight: 700;
}
</style>
