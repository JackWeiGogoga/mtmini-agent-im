<template>
  <view class="page">
    <app-subpage-header title="导入文档" />

    <view class="content">
      <view class="upload-card">
        <view class="card-action" :class="{ disabled: uploading }" @tap="chooseDocument">
          {{ uploading ? '上传中...' : '+添加文档' }}
        </view>
        <text class="upload-tip">(*支持WORD/TXT/EXCEL文本)</text>
        <view v-if="documents.length > 0" class="doc-grid">
          <view v-for="doc in documents" :key="doc.id" class="doc-chip">
            <text class="doc-name">{{ shortFileName(doc.fileName) }}</text>
            <text class="doc-close" @tap.stop="confirmRemoveDocument(doc)">×</text>
          </view>
        </view>
        <text v-else class="empty-inline">还没有上传文档</text>
      </view>

      <view class="qa-card">
        <view class="card-action" @tap="openQaForm()">+添加回复内容</view>
        <text class="qa-tip">(输入文字内容)</text>

        <view v-if="knowledgeRows.length === 0" class="empty-state">
          <view class="empty-icon">?</view>
          <text class="empty-title">暂无知识内容</text>
          <text class="empty-desc">可以上传文档，或手工添加顾客问题和回复内容。</text>
        </view>

        <view v-for="row in knowledgeRows" :key="row.rowKey" class="knowledge-row">
          <view class="question-icon">?</view>
          <view class="row-main">
            <view class="row-head">
              <text class="row-title">{{ row.title }}</text>
              <view class="row-actions">
                <text v-if="row.type === 'QA'" class="row-link" @tap="toggleAnswer(row.id)">查看回答</text>
                <text v-if="row.type === 'QA'" class="row-close" @tap="removeQa(row.id)">×</text>
              </view>
            </view>
            <text class="row-question">Q：{{ row.question }}</text>
            <text v-if="row.type === 'QA' && expandedQaId === row.id" class="row-answer">A：{{ row.answer }}</text>
            <text v-if="row.type === 'DOC'" class="row-answer">{{ row.answer }}</text>
            <view class="row-foot">
              <text class="sentiment-badge" :class="sentimentClass(row.sentiment)">{{ sentimentLabel(row.sentiment) }}</text>
              <text v-if="row.statusText" class="status-text">{{ row.statusText }}</text>
              <text v-if="row.type === 'QA'" class="edit-link" @tap="openQaForm(row.raw)">编辑</text>
            </view>
          </view>
        </view>
      </view>

      <view class="clear-card" @tap="confirmClearKnowledge">
        <view class="warning-icon">!</view>
        <text class="clear-text">清除AI已学所有知识</text>
        <text class="clear-action">删除</text>
      </view>
    </view>

    <view v-if="qaFormVisible" class="modal-mask" @tap="closeQaForm">
      <view class="qa-modal" @tap.stop>
        <view class="form-line">
          <text class="form-label">标题：</text>
          <input v-model="qaForm.title" class="form-input" maxlength="10" placeholder="请输入（10字内）" />
        </view>
        <view class="form-line">
          <text class="form-label">评分：</text>
          <picker class="form-picker" :range="sentimentOptions" range-key="label" :value="sentimentIndex" @change="changeSentiment">
            <view class="picker-value">
              <text>{{ sentimentOptions[sentimentIndex].label }}</text>
              <text class="picker-arrow">▼</text>
            </view>
          </picker>
        </view>
        <view class="form-line form-line-top">
          <text class="form-label">问题Q：</text>
          <textarea v-model="qaForm.question" class="form-textarea" maxlength="500" placeholder="请输入（500字内）" />
        </view>
        <view class="form-line form-line-top">
          <text class="form-label">回答A：</text>
          <textarea v-model="qaForm.answer" class="form-textarea answer-textarea" maxlength="300" placeholder="请输入（300字内）" />
        </view>
        <view class="form-actions">
          <button class="confirm-btn" :loading="savingQa" @tap="submitQaForm">确认</button>
          <button class="cancel-btn" @tap="closeQaForm">取消</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AppSubpageHeader from '@/components/app-subpage-header.vue'
import {
  clearCommentReplyKnowledge,
  createCommentReplyQaItem,
  deleteCommentReplyDocument,
  deleteCommentReplyQaItem,
  getCommentReplyDocuments,
  getCommentReplyQaItems,
  updateCommentReplyQaItem,
  uploadCommentReplyDocument,
} from '@/utils/mt-auth'

const sentimentOptions = [
  { label: '好评（4-5星）', value: 'GOOD' },
  { label: '中评（2-3星）', value: 'NEUTRAL' },
  { label: '差评（0-1星）', value: 'BAD' },
]

const qaItems = ref([])
const documents = ref([])
const uploading = ref(false)
const savingQa = ref(false)
const qaFormVisible = ref(false)
const editingQaId = ref(null)
const expandedQaId = ref(null)
const sentimentIndex = ref(0)
const qaForm = reactive({
  title: '',
  sentiment: 'GOOD',
  question: '',
  answer: '',
})

const knowledgeRows = computed(() => {
  const qaRows = qaItems.value.map((item) => ({
    rowKey: `qa-${item.id}`,
    type: 'QA',
    id: item.id,
    title: item.title || '回复内容',
    question: item.question || '',
    answer: item.answer || '',
    sentiment: item.sentiment,
    raw: item,
  }))
  const docRows = documents.value.map((doc) => ({
    rowKey: `doc-${doc.id}`,
    type: 'DOC',
    id: doc.id,
    title: doc.fileName || '文档知识',
    question: `已上传 ${doc.fileType || '文档'}，AI会根据解析后的知识内容回复顾客评论`,
    answer: doc.parseError ? `解析失败：${doc.parseError}` : `片段数：${doc.chunkCount || 0}`,
    sentiment: 'UNKNOWN',
    statusText: parseStatusText(doc.parseStatus),
  }))
  return [...qaRows, ...docRows]
})

onMounted(() => {
  refresh()
})

async function refresh() {
  try {
    const [qa, docs] = await Promise.all([
      getCommentReplyQaItems(),
      getCommentReplyDocuments(),
    ])
    qaItems.value = qa || []
    documents.value = docs || []
  } catch (error) {
    uni.showToast({ title: error.message || '加载失败', icon: 'none' })
  }
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
      uploading.value = true
      try {
        await uploadCommentReplyDocument(file.path)
        uni.showToast({ title: '上传成功', icon: 'success' })
        await refresh()
      } catch (error) {
        uni.showToast({ title: error.message || '上传失败', icon: 'none' })
      } finally {
        uploading.value = false
      }
    },
  })
}

function confirmRemoveDocument(doc) {
  uni.showModal({
    title: '确认删除文档？',
    content: doc.fileName || '删除后 AI 将不再使用该文档知识。',
    confirmText: '确认',
    cancelText: '取消',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        await deleteCommentReplyDocument(doc.id)
        await refresh()
      } catch (error) {
        uni.showToast({ title: error.message || '删除失败', icon: 'none' })
      }
    },
  })
}

function openQaForm(item) {
  editingQaId.value = item?.id || null
  qaForm.title = item?.title || ''
  qaForm.sentiment = item?.sentiment || 'GOOD'
  qaForm.question = item?.question || ''
  qaForm.answer = item?.answer || ''
  sentimentIndex.value = Math.max(0, sentimentOptions.findIndex((option) => option.value === qaForm.sentiment))
  qaFormVisible.value = true
}

function closeQaForm() {
  if (savingQa.value) {
    return
  }
  qaFormVisible.value = false
}

function changeSentiment(event) {
  const index = Number(event.detail.value || 0)
  sentimentIndex.value = index
  qaForm.sentiment = sentimentOptions[index].value
}

async function submitQaForm() {
  const title = qaForm.title.trim()
  const question = qaForm.question.trim()
  const answer = qaForm.answer.trim()
  if (!title || !question || !answer) {
    uni.showToast({ title: '请填写完整内容', icon: 'none' })
    return
  }
  savingQa.value = true
  try {
    const payload = {
      title,
      sentiment: qaForm.sentiment,
      question,
      answer,
      enabled: true,
    }
    if (editingQaId.value) {
      await updateCommentReplyQaItem(editingQaId.value, payload)
    } else {
      await createCommentReplyQaItem(payload)
    }
    qaFormVisible.value = false
    await refresh()
  } catch (error) {
    uni.showToast({ title: error.message || '保存失败', icon: 'none' })
  } finally {
    savingQa.value = false
  }
}

function removeQa(id) {
  uni.showModal({
    title: '确认删除这条知识？',
    content: '删除后 AI 将不再使用这条回复内容。',
    confirmText: '确认',
    cancelText: '取消',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        await deleteCommentReplyQaItem(id)
        await refresh()
      } catch (error) {
        uni.showToast({ title: error.message || '删除失败', icon: 'none' })
      }
    },
  })
}

function confirmClearKnowledge() {
  uni.showModal({
    title: '确认要删除AI所有知识吗？',
    content: '删除后已上传文档和回复内容都会清空。',
    confirmText: '确认',
    cancelText: '取消',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        await clearCommentReplyKnowledge()
        expandedQaId.value = null
        await refresh()
      } catch (error) {
        uni.showToast({ title: error.message || '删除失败', icon: 'none' })
      }
    },
  })
}

function toggleAnswer(id) {
  expandedQaId.value = expandedQaId.value === id ? null : id
}

function sentimentLabel(value) {
  return {
    GOOD: '好评',
    NEUTRAL: '中评',
    BAD: '差评',
    UNKNOWN: '文档',
  }[value] || '文档'
}

function sentimentClass(value) {
  return {
    GOOD: 'sentiment-good',
    NEUTRAL: 'sentiment-neutral',
    BAD: 'sentiment-bad',
  }[value] || 'sentiment-doc'
}

function parseStatusText(value) {
  return {
    PENDING: '待解析',
    PARSING: '解析中',
    SUCCESS: '已学习',
    FAILED: '解析失败',
  }[value] || value || ''
}

function shortFileName(value) {
  const name = value || '文档'
  return name.length > 9 ? `${name.slice(0, 6)}...` : name
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  max-width: 375px;
  margin: 0 auto;
  background: #f6f7fb;
  overflow-x: hidden;
}

.content {
  min-height: calc(100vh - 74px);
  box-sizing: border-box;
  padding: 12px 16px 28px;
}

.upload-card,
.qa-card,
.clear-card {
  background: #ffffff;
  border-radius: 16px;
  box-sizing: border-box;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.02);
}

.upload-card {
  min-height: 132px;
  padding: 20px 16px 18px;
  text-align: center;
}

.card-action {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 26px;
  color: #111111;
  font-size: 16px;
  font-weight: 700;
  line-height: 24px;
}

.disabled {
  opacity: 0.55;
  pointer-events: none;
}

.upload-tip,
.qa-tip,
.empty-inline {
  display: block;
  margin-top: 2px;
  color: #9a9a9a;
  font-size: 13px;
  line-height: 20px;
}

.empty-inline {
  margin-top: 14px;
}

.doc-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 9px 8px;
  margin-top: 18px;
}

.doc-chip {
  display: flex;
  align-items: center;
  max-width: 92px;
  height: 29px;
  padding: 0 8px 0 10px;
  border: 1px solid #111111;
  border-radius: 999px;
  box-sizing: border-box;
  background: #ffffff;
}

.doc-name {
  flex: 1;
  min-width: 0;
  color: #111111;
  font-size: 12px;
  overflow: hidden;
  white-space: nowrap;
}

.doc-close {
  margin-left: 4px;
  color: #111111;
  font-size: 16px;
  line-height: 1;
}

.qa-card {
  margin-top: 12px;
  padding: 20px 16px 16px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 10px 12px;
}

.empty-icon,
.question-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 999rpx;
  background: #1e1e1e;
  color: #ffffff;
  font-weight: 700;
}

.empty-icon {
  width: 40px;
  height: 40px;
  font-size: 26px;
}

.empty-title {
  margin-top: 10px;
  color: #111111;
  font-size: 15px;
  font-weight: 700;
}

.empty-desc {
  margin-top: 6px;
  color: #888888;
  font-size: 13px;
  text-align: center;
  line-height: 19px;
}

.knowledge-row {
  display: flex;
  gap: 10px;
  padding: 15px 0;
  border-bottom: 1px solid #f0f0f0;
}

.knowledge-row:last-child {
  border-bottom: 0;
  padding-bottom: 4px;
}

.question-icon {
  width: 34px;
  height: 34px;
  margin-top: 4px;
  font-size: 23px;
}

.row-main {
  flex: 1;
  min-width: 0;
}

.row-head,
.row-actions,
.row-foot,
.clear-card,
.form-line,
.form-actions,
.picker-value {
  display: flex;
  align-items: center;
}

.row-head {
  justify-content: space-between;
  gap: 8px;
}

.row-title {
  flex: 1;
  min-width: 0;
  color: #111111;
  font-size: 14px;
  font-weight: 700;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.row-actions {
  flex-shrink: 0;
  gap: 9px;
}

.row-link,
.edit-link {
  color: #111111;
  font-size: 13px;
  text-decoration: underline;
}

.row-close {
  color: #111111;
  font-size: 18px;
  line-height: 1;
}

.row-question,
.row-answer {
  display: block;
  margin-top: 5px;
  color: #111111;
  font-size: 13px;
  line-height: 19px;
}

.row-question {
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.row-answer {
  color: #5f5f5f;
}

.row-foot {
  justify-content: flex-start;
  gap: 7px;
  margin-top: 8px;
}

.sentiment-badge {
  padding: 2px 7px;
  border-radius: 999px;
  font-size: 11px;
}

.sentiment-good {
  color: #138a3d;
  background: #eaf8ef;
}

.sentiment-neutral {
  color: #9a6a00;
  background: #fff4d8;
}

.sentiment-bad {
  color: #cf2d23;
  background: #ffecea;
}

.sentiment-doc {
  color: #555555;
  background: #f0f0f0;
}

.status-text {
  color: #888888;
  font-size: 12px;
}

.clear-card {
  justify-content: center;
  gap: 10px;
  min-height: 54px;
  margin-top: 84px;
}

.warning-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: #d7d7d7;
  color: #ffffff;
  font-size: 16px;
  font-weight: 700;
}

.clear-text {
  color: #111111;
  font-size: 15px;
  font-weight: 700;
}

.clear-action {
  color: #111111;
  font-size: 15px;
  font-weight: 700;
  text-decoration: underline;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.28);
  box-sizing: border-box;
}

.qa-modal {
  width: 100%;
  max-width: 330px;
  padding: 18px 16px 16px;
  border: 2px solid #111111;
  border-radius: 9px;
  background: #ffffff;
  box-sizing: border-box;
}

.form-line {
  gap: 8px;
  margin-bottom: 10px;
}

.form-line-top {
  align-items: flex-start;
}

.form-label {
  width: 56px;
  flex-shrink: 0;
  color: #111111;
  font-size: 15px;
  line-height: 32px;
}

.form-input,
.form-picker,
.form-textarea {
  flex: 1;
  min-width: 0;
  border: 2rpx solid #c8c8c8;
  box-sizing: border-box;
  color: #111111;
  font-size: 13px;
}

.form-input,
.form-picker {
  height: 32px;
  padding: 0 9px;
}

.picker-value {
  justify-content: space-between;
  height: 32px;
}

.picker-arrow {
  color: #111111;
  font-size: 11px;
}

.form-textarea {
  height: 44px;
  padding: 6px 9px;
  line-height: 17px;
}

.answer-textarea {
  height: 42px;
}

.form-actions {
  justify-content: center;
  gap: 14px;
  margin-top: 15px;
}

.confirm-btn,
.cancel-btn {
  width: 62px;
  height: 30px;
  margin: 0;
  padding: 0;
  border-radius: 999px;
  font-size: 13px;
  line-height: 30px;
}

.confirm-btn {
  color: #111111;
  background: linear-gradient(180deg, #f5cd59 0%, #e6b72e 100%);
  font-weight: 700;
}

.cancel-btn {
  color: #6f5e32;
  background: #f7f1df;
}
</style>
