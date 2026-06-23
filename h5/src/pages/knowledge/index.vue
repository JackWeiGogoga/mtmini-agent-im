<template>
  <view class="page">
    <app-subpage-header title="回复知识库" />

    <view class="section">
      <view class="section-header">
        <text class="section-title">手工问答</text>
        <button class="small-btn" @click="openQaForm()">新增</button>
      </view>
      <view v-if="qaItems.length === 0" class="empty">暂无手工问答</view>
      <view v-for="item in qaItems" :key="item.id" class="item">
        <view class="item-top">
          <text class="item-title">{{ item.title }}</text>
          <text class="tag">{{ sentimentText(item.sentiment) }}</text>
        </view>
        <text class="muted">问：{{ item.question }}</text>
        <text class="answer">答：{{ item.answer }}</text>
        <view class="actions">
          <button class="text-btn" @click="openQaForm(item)">编辑</button>
          <button class="text-btn danger" @click="removeQa(item.id)">删除</button>
        </view>
      </view>
    </view>

    <view class="section">
      <view class="section-header">
        <text class="section-title">文档知识</text>
        <button class="small-btn" @click="chooseDocument">上传</button>
      </view>
      <view v-if="documents.length === 0" class="empty">暂无上传文档</view>
      <view v-for="doc in documents" :key="doc.id" class="item">
        <view class="item-top">
          <text class="item-title">{{ doc.fileName }}</text>
          <text class="tag">{{ doc.parseStatus }}</text>
        </view>
        <text class="muted">片段数：{{ doc.chunkCount || 0 }}</text>
        <text v-if="doc.parseError" class="error">{{ doc.parseError }}</text>
        <view class="actions">
          <button class="text-btn" @click="loadChunks(doc.id)">查看片段</button>
          <button class="text-btn danger" @click="removeDocument(doc.id)">删除</button>
        </view>
      </view>
    </view>

    <view v-if="chunks.length > 0" class="section">
      <view class="section-header">
        <text class="section-title">解析片段</text>
        <button class="small-btn ghost" @click="chunks = []">收起</button>
      </view>
      <view v-for="chunk in chunks" :key="chunk.id" class="chunk">
        <view class="item-top">
          <text class="tag">{{ sentimentText(chunk.sentiment) }}</text>
          <text class="muted">{{ chunk.enabled ? '启用' : '停用' }}</text>
        </view>
        <text class="chunk-text">{{ chunk.chunkText }}</text>
        <view class="actions">
          <button class="text-btn" @click="changeChunkSentiment(chunk)">改类型</button>
          <button class="text-btn" @click="toggleChunk(chunk)">{{ chunk.enabled ? '停用' : '启用' }}</button>
          <button class="text-btn danger" @click="removeChunk(chunk.id)">删除</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import {
  createCommentReplyQaItem,
  deleteCommentReplyDocument,
  deleteCommentReplyQaItem,
  deleteCommentReplyChunk,
  getCommentReplyDocumentChunks,
  getCommentReplyDocuments,
  getCommentReplyQaItems,
  updateCommentReplyQaItem,
  updateCommentReplyChunkEnabled,
  updateCommentReplyChunkSentiment,
  uploadCommentReplyDocument,
} from '@/utils/mt-auth'
import AppSubpageHeader from '@/components/app-subpage-header.vue'

const qaItems = ref([])
const documents = ref([])
const chunks = ref([])

onMounted(() => {
  refresh()
})

function sentimentText(value) {
  return {
    GOOD: '好评',
    NEUTRAL: '中评',
    BAD: '差评',
    UNKNOWN: '通用',
  }[value] || value
}

async function refresh() {
  try {
    qaItems.value = await getCommentReplyQaItems()
    documents.value = await getCommentReplyDocuments()
  } catch (error) {
    uni.showToast({ title: error.message || '加载失败', icon: 'none' })
  }
}

function openQaForm(item) {
  const isEdit = Boolean(item)
  uni.showModal({
    title: isEdit ? '编辑问答' : '新增问答',
    editable: true,
    placeholderText: '按顺序输入：标题｜类型GOOD/NEUTRAL/BAD/UNKNOWN｜问题｜回答',
    content: item ? `${item.title}｜${item.sentiment}｜${item.question}｜${item.answer}` : '',
    success: async (res) => {
      if (!res.confirm || !res.content) {
        return
      }
      const [title, sentiment, question, answer] = res.content.split('｜')
      if (!title || !sentiment || !question || !answer) {
        uni.showToast({ title: '请按格式填写完整', icon: 'none' })
        return
      }
      const payload = { title, sentiment, question, answer, enabled: true }
      try {
        if (isEdit) {
          await updateCommentReplyQaItem(item.id, payload)
        } else {
          await createCommentReplyQaItem(payload)
        }
        await refresh()
      } catch (error) {
        uni.showToast({ title: error.message || '保存失败', icon: 'none' })
      }
    },
  })
}

async function removeQa(id) {
  await deleteCommentReplyQaItem(id)
  await refresh()
}

function chooseDocument() {
  uni.chooseFile({
    count: 1,
    extension: ['.txt', '.docx', '.xlsx'],
    success: async (res) => {
      const file = res.tempFiles?.[0]
      if (!file?.path) {
        return
      }
      try {
        await uploadCommentReplyDocument(file.path)
        await refresh()
      } catch (error) {
        uni.showToast({ title: error.message || '上传失败', icon: 'none' })
      }
    },
  })
}

async function removeDocument(id) {
  await deleteCommentReplyDocument(id)
  await refresh()
}

async function loadChunks(id) {
  chunks.value = await getCommentReplyDocumentChunks(id)
}

function changeChunkSentiment(chunk) {
  const next = chunk.sentiment === 'GOOD' ? 'NEUTRAL' : chunk.sentiment === 'NEUTRAL' ? 'BAD' : 'GOOD'
  updateCommentReplyChunkSentiment(chunk.id, next).then(() => loadChunks(chunk.documentId))
}

function toggleChunk(chunk) {
  updateCommentReplyChunkEnabled(chunk.id, !chunk.enabled).then(() => loadChunks(chunk.documentId))
}

function removeChunk(id) {
  deleteCommentReplyChunk(id).then(() => {
    chunks.value = chunks.value.filter((item) => item.id !== id)
  })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx;
  background: #f6f7fb;
}

.section {
  margin-bottom: 24rpx;
  padding: 24rpx;
  background: #fff;
  border-radius: 16rpx;
}

.section-header,
.item-top,
.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.section-title {
  font-size: 34rpx;
  font-weight: 700;
}

.small-btn {
  margin: 0;
  padding: 0 24rpx;
  height: 60rpx;
  line-height: 60rpx;
  border-radius: 999rpx;
  color: #fff;
  background: #1677ff;
  font-size: 26rpx;
}

.ghost {
  color: #1677ff;
  background: #eef5ff;
}

.item,
.chunk {
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #eef0f4;
}

.item-title {
  flex: 1;
  min-width: 0;
  font-size: 30rpx;
  font-weight: 600;
}

.tag {
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: #eef5ff;
  color: #1677ff;
  font-size: 24rpx;
}

.muted,
.answer,
.chunk-text,
.empty,
.error {
  display: block;
  margin-top: 12rpx;
  color: #667085;
  font-size: 26rpx;
  line-height: 1.6;
}

.answer,
.chunk-text {
  color: #1f2937;
}

.error,
.danger {
  color: #d92d20;
}

.text-btn {
  margin: 12rpx 0 0;
  padding: 0;
  color: #1677ff;
  background: transparent;
  font-size: 26rpx;
}
</style>
