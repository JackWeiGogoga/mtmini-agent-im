<template>
  <view class="page">
    <view class="header">
      <text class="title">评价自动回复</text>
      <text class="subtitle">{{ shopName }}</text>
    </view>

    <view class="section">
      <view class="status-row">
        <text class="status-label">服务状态</text>
        <text class="status-value" :class="{ warn: reauthRequired }">
          {{ reauthRequired ? "需重新授权" : "运行中" }}
        </text>
      </view>
      <text class="desc">
        系统每天早中晚自动查询近 7 天未回复评价，并使用固定文案回复。
      </text>
    </view>

    <view class="stats">
      <view class="stat">
        <text class="stat-label">近7天实收</text>
        <text class="stat-value">¥{{ incomeText }}</text>
      </view>
      <view class="stat">
        <text class="stat-label">算力余额</text>
        <text class="stat-value">{{ powerBalance }}</text>
      </view>
    </view>

    <app-tabbar active-tab="home" />
  </view>
</template>

<script>
import AppTabbar from "../../components/app-tabbar.vue";
import { getMtH5HomeDashboard } from "../../utils/mt-auth";

export default {
  components: { AppTabbar },
  data() {
    return {
      shopName: "美团门店",
      reauthRequired: false,
      incomeText: "0.00",
      powerBalance: 0,
    };
  },
  mounted() {
    this.loadDashboard();
  },
  methods: {
    async loadDashboard() {
      try {
        const dashboard = await getMtH5HomeDashboard("LAST_7_DAYS");
        this.shopName = dashboard?.shop?.shopName || "美团门店";
        this.reauthRequired = Boolean(dashboard?.authorization?.reauthRequired);
        this.incomeText = Number(
          dashboard?.marketingStats?.currentIncome || 0,
        ).toFixed(2);
        this.powerBalance = dashboard?.powerStats?.balance || 0;
      } catch (error) {
        uni.showToast({ title: error.message || "加载失败", icon: "none" });
      }
    },
  },
};
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 56rpx 28rpx 160rpx;
  box-sizing: border-box;
  background: #f6f7f9;
}
.header {
  margin-bottom: 28rpx;
}
.title {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: #15181f;
}
.subtitle {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  color: #667085;
}
.section,
.stat {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx;
  box-shadow: 0 10rpx 30rpx rgba(15, 23, 42, 0.06);
}
.status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.status-label,
.stat-label {
  font-size: 26rpx;
  color: #667085;
}
.status-value {
  font-size: 30rpx;
  font-weight: 700;
  color: #16803c;
}
.status-value.warn {
  color: #d92d20;
}
.desc {
  display: block;
  margin-top: 22rpx;
  font-size: 28rpx;
  line-height: 1.55;
  color: #344054;
}
.stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20rpx;
  margin-top: 24rpx;
}
.stat-value {
  display: block;
  margin-top: 16rpx;
  font-size: 38rpx;
  font-weight: 700;
  color: #15181f;
}
</style>
