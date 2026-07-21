<template>
  <view class="page">
    <app-subpage-header title="回复日志" />

    <view class="content">
      <view class="segment">
        <view class="segment-item" :class="{ active: activeView === 'messages' }" @tap="switchView('messages')">回复记录</view>
        <view class="segment-item" :class="{ active: activeView === 'conversations' }" @tap="switchView('conversations')">会话列表</view>
      </view>

      <view v-if="activeView === 'messages'">
        <view v-if="!messageRows.length && !loading" class="empty-card">暂无自动回复记录</view>
        <view v-for="row in messageRows" :key="row.autoReplyId" class="reply-card" @tap="openConversation(row)">
          <view class="card-head">
            <view>
              <text class="customer">顾客 {{ row.openUserId || '-' }}</text>
              <text class="order">订单 {{ row.orderId || 0 }}</text>
            </view>
            <text class="status" :class="statusClass(row.status)">{{ statusText(row.status) }}</text>
          </view>
          <view class="message-block user-block">
            <text class="role">顾客消息</text>
            <text class="message-text">{{ row.userContent || '（无正文）' }}</text>
            <text class="time">{{ formatTime(row.userMessageTime) }}</text>
          </view>
          <view class="message-block ai-block">
            <view class="role-line">
              <text class="role ai-role">AI 自动回复</text>
              <text v-if="row.ragUsed" class="rag-tag">RAG {{ row.ragChunkCount || 0 }} 条</text>
            </view>
            <text class="message-text">{{ row.aiContent || `（${row.skipReason || '尚未生成'}）` }}</text>
            <text class="time">{{ formatTime(row.aiMessageTime || row.createTime) }}</text>
          </view>
          <view class="card-foot">
            <text>算力 {{ row.powerAmount ?? '-' }}</text>
            <text class="detail-link">查看完整对话 ›</text>
          </view>
        </view>
      </view>

      <view v-else>
        <view v-if="!conversationRows.length && !loading" class="empty-card">暂无会话记录</view>
        <view v-for="row in conversationRows" :key="row.conversationId" class="conversation-card" @tap="openConversation(row)">
          <view class="conversation-head">
            <text class="customer">顾客 {{ row.openUserId }}</text>
            <text class="time">{{ formatTime(row.lastMessageAt) }}</text>
          </view>
          <text class="last-message">{{ row.lastMessageContent || '暂无消息正文' }}</text>
          <view class="conversation-meta">
            <text>消息 {{ row.messageCount || 0 }}</text>
            <text>AI {{ row.aiMessageCount || 0 }}</text>
            <text>历史 Session {{ row.sessionCount || 0 }}</text>
          </view>
        </view>
      </view>

      <view v-if="loading" class="loading-text">加载中...</view>
      <view v-else-if="!hasMore && currentRows.length" class="loading-text">已加载全部</view>
    </view>

    <!-- 弹窗打开时锁住底层页面滚动；对话区域自己 stop 掉 touchmove，不会被这里的 prevent 拦住 -->
    <view v-if="detailVisible" class="detail-mask" @tap="closeDetail" @touchmove.stop.prevent="noop">
      <view class="detail-panel" @tap.stop>
        <view class="detail-head">
          <view>
            <text class="detail-title">顾客 {{ selectedConversation?.openUserId || '-' }}</text>
            <text class="detail-subtitle">完整对话 · 可向上加载历史 Session</text>
          </view>
          <text class="detail-close" @tap="closeDetail">×</text>
        </view>
        <scroll-view
          class="history-scroll"
          scroll-y
          :scroll-into-view="scrollIntoView"
          :upper-threshold="80"
          @scrolltoupper="loadOlderHistory"
          @touchmove.stop
        >
          <view v-if="historyHasMore" class="history-tip">{{ historyLoading ? '正在加载...' : '向上滑动加载更早消息' }}</view>
          <view v-for="(item, index) in historyRows" :id="`msg-${item.id}`" :key="item.id">
            <view v-if="isNewSession(item, index)" class="session-divider">
              <view class="session-line"></view>
              <text>Session {{ item.sessionNo || '-' }} · {{ formatTime(item.sessionStartedAt || item.eventTime) }}</text>
              <view class="session-line"></view>
            </view>
            <view class="bubble-row" :class="{ mine: item.senderRole !== 'USER' }">
              <view class="bubble" :class="bubbleClass(item.senderRole)">
                <text class="bubble-role">{{ roleText(item.senderRole) }}</text>
                <text class="bubble-content">{{ item.content || '（无正文）' }}</text>
                <text class="bubble-time">{{ formatTime(item.eventTime) }}</text>
              </view>
            </view>
          </view>
          <view id="history-bottom" class="history-bottom"></view>
        </scroll-view>
      </view>
    </view>

    <app-tabbar active-tab="logs" />
  </view>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import AppSubpageHeader from '@/components/app-subpage-header.vue'
import AppTabbar from '@/components/app-tabbar.vue'
import {
  getMtH5ImAutoReplyLogs,
  getMtH5ImConversationHistory,
  getMtH5ImConversations,
  reportMtH5PageView,
} from '@/utils/mt-auth'
import { showErrorToast } from '@/utils/ui'

const activeView = ref('messages')
const messageRows = ref([])
const conversationRows = ref([])
const pageNum = ref(1)
const pageSize = 10
const hasMore = ref(true)
const loading = ref(false)
const detailVisible = ref(false)
const selectedConversation = ref(null)
const historyRows = ref([])
const historyHasMore = ref(false)
const historyLoading = ref(false)
const historyCursor = ref({})
const scrollIntoView = ref('')

const currentRows = computed(() => activeView.value === 'messages' ? messageRows.value : conversationRows.value)

onMounted(() => {
  reportMtH5PageView('IM_REPLY_LOG', 'TAB')
  refresh()
})

// 弹窗打开时底层列表不应再翻页/刷新：遮罩已拦住 touchmove，这里兜住各端页面事件的差异
onPullDownRefresh(async () => {
  if (!detailVisible.value) await refresh()
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (!detailVisible.value && hasMore.value && !loading.value) loadMore()
})

async function switchView(value) {
  if (activeView.value === value) return
  activeView.value = value
  await refresh()
}

async function refresh() {
  pageNum.value = 1
  hasMore.value = true
  if (activeView.value === 'messages') messageRows.value = []
  else conversationRows.value = []
  await loadMore()
}

async function loadMore() {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const page = activeView.value === 'messages'
      ? await getMtH5ImAutoReplyLogs({ pageNum: pageNum.value, pageSize })
      : await getMtH5ImConversations({ pageNum: pageNum.value, pageSize })
    const rows = page?.list || []
    if (activeView.value === 'messages') messageRows.value.push(...rows)
    else conversationRows.value.push(...rows)
    hasMore.value = pageNum.value < (page?.pages || 0)
    pageNum.value += 1
  } catch (error) {
    showErrorToast(error, '加载回复日志失败')
  } finally {
    loading.value = false
  }
}

async function openConversation(row) {
  selectedConversation.value = row
  detailVisible.value = true
  historyRows.value = []
  historyCursor.value = {}
  historyHasMore.value = false
  historyLoading.value = true
  try {
    const page = await getMtH5ImConversationHistory(row.conversationId, { pageSize: 30 })
    historyRows.value = page?.list || []
    historyHasMore.value = Boolean(page?.hasMore)
    historyCursor.value = { cursorTime: page?.nextCursorTime, cursorId: page?.nextCursorId }
    await nextTick()
    scrollIntoView.value = 'history-bottom'
  } catch (error) {
    showErrorToast(error, '加载完整对话失败')
  } finally {
    historyLoading.value = false
  }
}

async function loadOlderHistory() {
  if (!selectedConversation.value || historyLoading.value || !historyHasMore.value || !historyCursor.value.cursorId) return
  const anchorId = historyRows.value[0]?.id
  historyLoading.value = true
  try {
    const page = await getMtH5ImConversationHistory(selectedConversation.value.conversationId, {
      ...historyCursor.value,
      pageSize: 30,
    })
    historyRows.value = [...(page?.list || []), ...historyRows.value]
    historyHasMore.value = Boolean(page?.hasMore)
    historyCursor.value = { cursorTime: page?.nextCursorTime, cursorId: page?.nextCursorId }
    await nextTick()
    if (anchorId) scrollIntoView.value = `msg-${anchorId}`
  } catch (error) {
    showErrorToast(error, '加载历史消息失败')
  } finally {
    historyLoading.value = false
  }
}

function closeDetail() {
  detailVisible.value = false
  selectedConversation.value = null
  historyRows.value = []
  scrollIntoView.value = ''
}

// 挂在遮罩 touchmove 上，只为拿到 .prevent 修饰符去锁底层页面滚动
function noop() {}

function isNewSession(item, index) {
  return index === 0 || historyRows.value[index - 1]?.sessionId !== item.sessionId
}

function roleText(role) {
  if (role === 'USER') return '顾客'
  if (role === 'AI') return 'AI 自动回复'
  return '商家人工'
}

function bubbleClass(role) {
  return role === 'USER' ? 'user-bubble' : role === 'AI' ? 'ai-bubble' : 'merchant-bubble'
}

function statusText(status) {
  const map = { SENT: '已发送', SENDING: '发送中', PENDING: '等待中', GENERATING: '生成中', GENERATED: '已生成', BILLED: '已计费', SKIPPED: '已跳过', SUPERSEDED: '已合并', DEAD: '失败' }
  return map[status] || status || '-'
}

function statusClass(status) {
  return status === 'SENT' ? 'success' : status === 'DEAD' ? 'failed' : status === 'SKIPPED' || status === 'SUPERSEDED' ? 'muted' : 'pending'
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}
</script>

<style scoped>
.page { min-height: 100vh; background: #f6f6f6; padding-bottom: 150rpx; box-sizing: border-box; }
.content { padding: 24rpx; }
.segment { display: flex; padding: 8rpx; margin-bottom: 24rpx; border-radius: 20rpx; background: #fff; }
.segment-item { flex: 1; padding: 18rpx 0; text-align: center; color: #888; font-size: 28rpx; border-radius: 16rpx; }
.segment-item.active { color: #1f1f1f; font-weight: 700; background: #f6cf55; }
.empty-card, .loading-text { padding: 70rpx 20rpx; text-align: center; color: #999; font-size: 26rpx; }
.reply-card, .conversation-card { margin-bottom: 22rpx; padding: 24rpx; border-radius: 22rpx; background: #fff; box-shadow: 0 8rpx 28rpx rgba(0,0,0,.04); }
.card-head, .conversation-head, .card-foot, .role-line, .conversation-meta { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; }
.customer { display: block; color: #242424; font-size: 28rpx; font-weight: 700; }
.order, .time { display: block; margin-top: 4rpx; color: #9a9a9a; font-size: 21rpx; }
.status { padding: 7rpx 14rpx; border-radius: 999rpx; font-size: 21rpx; }
.status.success { color: #198754; background: #eaf8f0; }.status.failed { color: #d9363e; background: #fff0f0; }.status.pending { color: #9a6b00; background: #fff7dc; }.status.muted { color: #777; background: #f0f0f0; }
.message-block { margin-top: 18rpx; padding: 20rpx; border-radius: 16rpx; }.user-block { background: #f3f7ff; }.ai-block { background: #faf7ff; }
.role { color: #356ac3; font-size: 22rpx; font-weight: 700; }.ai-role { color: #7651b5; }.rag-tag { padding: 4rpx 10rpx; border-radius: 999rpx; color: #356ac3; background: #eaf2ff; font-size: 19rpx; }
.message-text { display: block; margin-top: 10rpx; color: #333; font-size: 27rpx; line-height: 1.55; white-space: pre-wrap; }
.card-foot { margin-top: 18rpx; color: #999; font-size: 21rpx; }.detail-link { color: #9a7200; font-weight: 600; }
.last-message { display: -webkit-box; margin: 16rpx 0; overflow: hidden; color: #555; font-size: 26rpx; line-height: 1.5; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.conversation-meta { justify-content: flex-start; color: #888; font-size: 21rpx; }
.detail-mask { position: fixed; inset: 0; z-index: 99; display: flex; align-items: flex-end; background: rgba(0,0,0,.45); }
.detail-panel { width: 100%; height: 88vh; border-radius: 28rpx 28rpx 0 0; background: #fff; overflow: hidden; }
.detail-head { display: flex; align-items: center; justify-content: space-between; padding: 28rpx; border-bottom: 2rpx solid #f0f0f0; }
.detail-title { display: block; color: #222; font-size: 30rpx; font-weight: 700; }.detail-subtitle { display: block; margin-top: 6rpx; color: #999; font-size: 21rpx; }.detail-close { padding: 10rpx; color: #777; font-size: 44rpx; }
.history-scroll { height: calc(88vh - 110rpx); padding: 0 24rpx 40rpx; box-sizing: border-box; }
.history-tip { padding: 20rpx; text-align: center; color: #999; font-size: 21rpx; }
.session-divider { display: flex; align-items: center; gap: 12rpx; padding: 22rpx 0; color: #aaa; font-size: 19rpx; }.session-line { flex: 1; height: 2rpx; background: #eee; }
.bubble-row { display: flex; justify-content: flex-start; margin-bottom: 18rpx; }.bubble-row.mine { justify-content: flex-end; }
.bubble { max-width: 78%; padding: 16rpx 20rpx; border-radius: 18rpx; }.user-bubble { background: #eef4ff; }.ai-bubble { background: #f4efff; }.merchant-bubble { background: #edf8ef; }
.bubble-role, .bubble-time { display: block; color: #888; font-size: 19rpx; }.bubble-content { display: block; margin: 8rpx 0; color: #333; font-size: 26rpx; line-height: 1.5; white-space: pre-wrap; }.bubble-time { text-align: right; }
.history-bottom { height: 24rpx; }
</style>
