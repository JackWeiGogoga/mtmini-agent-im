<template>
  <view class="subpage-header">
    <view class="header">
      <view class="header-bar">
        <view class="header-side" @tap="goHome">
          <view class="back-arrow" aria-hidden="true"></view>
        </view>
        <text class="header-title">{{ title }}</text>
        <view class="header-side header-placeholder"></view>
      </view>
    </view>
  </view>
</template>

<script>
export default {
  props: {
    title: {
      type: String,
      required: true,
    },
  },
  methods: {
    goHome() {
      uni.redirectTo({
        url: "/pages/index/index",
      });
    },
  },
};
</script>

<style scoped>
.subpage-header {
  flex-shrink: 0;
}

.header {
  background: linear-gradient(180deg, #efba4f 0%, #f2c860 100%);
  padding: calc(env(safe-area-inset-top) + 20rpx) 20rpx 22rpx;
}

.header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 72rpx;
}

.header-side {
  width: 120rpx;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding-left: 14rpx;
  box-sizing: border-box;
}

.header-placeholder {
  justify-content: flex-end;
}

.back-arrow {
  width: 22rpx;
  height: 22rpx;
  border-left: 4rpx solid #342a14;
  border-bottom: 4rpx solid #342a14;
  transform: rotate(45deg);
  box-sizing: border-box;
}

.header-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #222222;
}
</style>
