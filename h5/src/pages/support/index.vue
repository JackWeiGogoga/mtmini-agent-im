<template>
  <view class="page">
    <app-subpage-header title="客服" />

    <view class="content">
      <text class="title">联系客服了解更多功能</text>
      <view class="qrcode-frame">
        <image
          class="qrcode-image"
          src="/static/images/qrcode.jpg"
          mode="aspectFit"
        ></image>
      </view>
      <text class="subtitle">扫码添加客服/进群咨询</text>
      <text class="footer-copy">连锁店/多店铺/代运营商家*专人对接</text>
    </view>

    <app-tabbar active-tab="support" />
  </view>
</template>

<script>
import AppTabbar from "../../components/app-tabbar.vue";
import AppSubpageHeader from "../../components/app-subpage-header.vue";
import { reportMtH5PageView } from "../../utils/mt-auth";

export default {
  components: {
    AppTabbar,
    AppSubpageHeader,
  },
  data() {
    return {
      pageEntrySource: "DIRECT",
    };
  },
  onLoad(options) {
    this.pageEntrySource = options?.entrySource || "DIRECT";
  },
  onShow() {
    reportMtH5PageView("SUPPORT", this.pageEntrySource);
  },
};
</script>

<style>
page {
  background: #f7e6a2;
}
</style>

<style scoped>
.page {
  min-height: 100vh;
  padding-bottom: calc(env(safe-area-inset-bottom) + 136rpx);
  box-sizing: border-box;
  background: #f7e6a2;
}

.content {
  padding: 92rpx 28rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.title {
  font-size: 38rpx;
  font-weight: 700;
  line-height: 1.35;
  color: #2b220f;
}

.qrcode-frame {
  margin-top: 56rpx;
  padding: 22rpx;
  border-radius: 18rpx;
  background: #e2b62a;
}

.qrcode-image {
  width: 360rpx;
  height: 360rpx;
  display: block;
  background: #ffffff;
}

.subtitle {
  margin-top: 28rpx;
  font-size: 28rpx;
  color: #4d3d19;
}

.footer-copy {
  margin-top: 84rpx;
  font-size: 32rpx;
  font-weight: 700;
  line-height: 1.4;
  color: #231c0d;
}
</style>
