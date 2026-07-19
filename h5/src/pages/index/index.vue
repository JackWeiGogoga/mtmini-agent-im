<template>
  <view class="page">
    <view class="page-shell">
      <app-top-notice
        message="登录 PC 端: https://aicadmin.waimaidashi.com"
        copy-text="https://aicadmin.waimaidashi.com"
        :show-icon="false"
        safe-top
      />

      <view class="header">
        <view>
          <text class="shop-name">{{ shopDisplayName }}</text>
          <text class="membership">{{ membershipText }}</text>
        </view>
        <text class="guide-link" @tap="goToSupport">使用指导 ></text>
      </view>

      <view class="content">
        <view class="service-card">
          <view class="service-head">
            <view>
              <text class="service-title">IM 消息自动回复</text>
              <text class="service-desc">接收顾客两方会话消息，自动回复能力准备中</text>
            </view>
            <text class="status" :class="{ off: !autoReplyEnabled }">
              {{ autoReplyEnabled ? '运行中' : '已关闭' }}
            </text>
          </view>

          <view class="switch-row">
            <view>
              <text class="switch-title">自动回复总开关</text>
              <text class="switch-help">授权初始化成功后默认开启，也可在此手动调整</text>
            </view>
            <switch
              color="#f2c94c"
              :checked="autoReplyEnabled"
              :disabled="operationSettingsSubmitting || reauthRequired"
              @change="toggleAutoReply"
            />
          </view>

          <view v-if="reauthRequired" class="warning">
            门店授权已失效，请重新授权后使用 IM 自动回复。
          </view>
        </view>

        <view class="summary-card">
          <view class="summary-item">
            <text class="summary-label">算力余额</text>
            <text class="summary-value">{{ powerBalanceText }}</text>
          </view>
          <view class="summary-item">
            <text class="summary-label">授权状态</text>
            <text class="summary-value">{{ reauthRequired ? '需重新授权' : '有效' }}</text>
          </view>
        </view>

        <view class="entry-card" @tap="goToKnowledgePage">
          <view>
            <text class="entry-title">门店知识库</text>
            <text class="entry-desc">维护通用问答、文档和知识切片</text>
          </view>
          <text class="entry-action">配置 ></text>
        </view>

        <view class="entry-card" @tap="goToPowerPage">
          <view>
            <text class="entry-title">算力管理</text>
            <text class="entry-desc">查看余额、充值和兑换</text>
          </view>
          <text class="entry-action">查看 ></text>
        </view>

        <view class="entry-card" @tap="goToSupport">
          <view>
            <text class="entry-title">客服与反馈</text>
            <text class="entry-desc">获取使用指导或联系人工客服</text>
          </view>
          <text class="entry-action">联系 ></text>
        </view>
      </view>
    </view>

    <app-tabbar active-tab="home" />
  </view>
</template>

<script>
import AppTabbar from '../../components/app-tabbar.vue'
import AppTopNotice from '../../components/app-top-notice.vue'
import {
  getMtH5HomeDashboard,
  getShopInfo,
  reportMtH5PageView,
  updateMtH5OperationSettings,
} from '../../utils/mt-auth'

export default {
  components: { AppTabbar, AppTopNotice },
  data() {
    return {
      shopName: '加载中...',
      shopPoiId: '',
      membership: { active: false, expireAt: null },
      reauthRequired: false,
      autoReplyEnabled: false,
      operationSettingsSubmitting: false,
      powerBalance: null,
      pageEntrySource: 'APP',
    }
  },
  computed: {
    shopDisplayName() {
      return this.shopPoiId ? `${this.shopName}（门店 ID：${this.shopPoiId}）` : this.shopName
    },
    membershipText() {
      const expireAt = String(this.membership?.expireAt || '')
      if (!this.membership?.active) return '服务权益未开通'
      return expireAt.length >= 10 ? `服务有效期至 ${expireAt.slice(0, 10)}` : '服务权益有效'
    },
    powerBalanceText() {
      return this.powerBalance == null ? '--' : String(this.powerBalance)
    },
  },
  onLoad(options) {
    this.pageEntrySource = options?.entrySource || 'APP'
  },
  onShow() {
    reportMtH5PageView('HOME', this.pageEntrySource)
    this.loadDashboard()
  },
  methods: {
    async loadDashboard() {
      const cachedShop = getShopInfo()
      this.shopName = cachedShop?.shopName || cachedShop?.poiName || this.shopName
      this.shopPoiId = cachedShop?.poiId || ''
      try {
        const dashboard = await getMtH5HomeDashboard()
        this.shopName = dashboard?.shop?.shopName || this.shopName
        this.shopPoiId = dashboard?.shop?.poiId || this.shopPoiId
        this.membership = dashboard?.membership || this.membership
        this.reauthRequired = Boolean(dashboard?.authorization?.reauthRequired)
        this.autoReplyEnabled = Boolean(dashboard?.operationSettings?.autoReplyEnabled)
        this.powerBalance = dashboard?.powerStats?.balance ?? null
      } catch (error) {
        uni.showToast({ title: error.message || '首页加载失败', icon: 'none' })
      }
    },
    async toggleAutoReply(event) {
      if (this.operationSettingsSubmitting) return
      const desired = Boolean(event?.detail?.value)
      this.operationSettingsSubmitting = true
      try {
        const settings = await updateMtH5OperationSettings({ autoReplyEnabled: desired })
        this.autoReplyEnabled = Boolean(settings?.autoReplyEnabled)
        uni.showToast({ title: this.autoReplyEnabled ? '已开启' : '已关闭', icon: 'success' })
      } catch (error) {
        uni.showToast({ title: error.message || '设置失败', icon: 'none' })
      } finally {
        this.operationSettingsSubmitting = false
      }
    },
    goToKnowledgePage() {
      uni.navigateTo({ url: '/pages/knowledge/index' })
    },
    goToPowerPage() {
      uni.navigateTo({ url: '/pages/power/index' })
    },
    goToSupport() {
      uni.navigateTo({ url: '/pages/support/index?entrySource=HOME' })
    },
  },
}
</script>

<style scoped>
.page { min-height: 100vh; background: #f5f6f8; color: #222; }
.page-shell { padding-bottom: 150rpx; }
.header { display: flex; justify-content: space-between; align-items: center; padding: 30rpx 28rpx; background: #fff; }
.shop-name, .membership, .service-title, .service-desc, .switch-title, .switch-help, .summary-label, .summary-value, .entry-title, .entry-desc { display: block; }
.shop-name { max-width: 520rpx; font-size: 30rpx; font-weight: 700; }
.membership { margin-top: 8rpx; color: #8a8f99; font-size: 23rpx; }
.guide-link, .entry-action { color: #a77a00; font-size: 24rpx; }
.content { padding: 22rpx; }
.service-card, .summary-card, .entry-card { margin-bottom: 20rpx; padding: 28rpx; border-radius: 22rpx; background: #fff; box-shadow: 0 8rpx 28rpx rgba(35, 42, 55, .05); }
.service-head, .switch-row, .entry-card { display: flex; justify-content: space-between; align-items: center; gap: 24rpx; }
.service-title { font-size: 34rpx; font-weight: 750; }
.service-desc, .switch-help, .entry-desc { margin-top: 10rpx; color: #8a8f99; font-size: 23rpx; line-height: 1.5; }
.status { flex: none; padding: 8rpx 16rpx; border-radius: 999rpx; background: #e8f7ed; color: #16833e; font-size: 22rpx; }
.status.off { background: #f0f1f3; color: #737982; }
.switch-row { margin-top: 32rpx; padding-top: 26rpx; border-top: 1rpx solid #eceef1; }
.switch-title, .entry-title { font-size: 28rpx; font-weight: 650; }
.warning { margin-top: 22rpx; padding: 18rpx; border-radius: 12rpx; background: #fff2ed; color: #c34b24; font-size: 23rpx; }
.summary-card { display: grid; grid-template-columns: 1fr 1fr; gap: 16rpx; }
.summary-item { padding: 18rpx; border-radius: 16rpx; background: #f7f8fa; }
.summary-label { color: #8a8f99; font-size: 22rpx; }
.summary-value { margin-top: 10rpx; font-size: 28rpx; font-weight: 700; }
</style>
