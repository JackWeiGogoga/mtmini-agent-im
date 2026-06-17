<template>
  <view class="page">
    <view v-if="showError" class="card">
      <text class="title">{{ title }}</text>
      <text class="desc">{{ description }}</text>
    </view>
  </view>
</template>

<script>
import {
  clearAuthPayload,
  loginWithMtH5Params,
  saveAuthPayload,
} from '../../../utils/mt-auth'

export default {
  data() {
    return {
      title: '正在完成门店登录...',
      description: '正在校验门店身份',
      handled: false,
      showError: false,
    }
  },
  onLoad(options) {
    if (this.handled) {
      return
    }
    this.handled = true
    this.handleCallback(options || {})
  },
  methods: {
    async handleCallback(options) {
      const { wmPoiId, bizToken, platform, appKey, error, message } = options

      clearAuthPayload()

      if (error) {
        this.showError = true
        this.title = '门店登录失败'
        this.description = message || error
        return
      }

      if (!wmPoiId) {
        this.showError = true
        this.title = '门店登录失败'
        this.description = '缺少门店标识，请重新从美团商家中心打开应用'
        return
      }

      try {
        const result = await loginWithMtH5Params({
          wmPoiId,
          bizToken,
          platform,
          appKey,
        })
        saveAuthPayload(result)
        uni.reLaunch({
          url: '/pages/index/index',
        })
      } catch (err) {
        this.showError = true
        this.title = '门店登录失败'
        this.description = err?.message || '换取登录态失败'
      }
    },
  },
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx;
  box-sizing: border-box;
  background: linear-gradient(180deg, #f3d27d 0%, #f8f3e9 100%);
}

.card {
  width: 100%;
  padding: 56rpx 40rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 24rpx 64rpx rgba(114, 86, 32, 0.12);
  box-sizing: border-box;
}

.title {
  display: block;
  text-align: center;
  font-size: 38rpx;
  font-weight: 700;
  color: #2f2618;
}

.desc {
  display: block;
  margin-top: 22rpx;
  text-align: center;
  font-size: 28rpx;
  line-height: 1.7;
  color: #77644e;
}
</style>
