<template>
  <view class="page">
    <app-subpage-header title="导入文档" />

    <view class="content">
      <view class="upload-card">
        <view class="upload-log-entry" @tap="openUploadLogs">
          上传记录
          <text v-if="failedDocumentCount > 0" class="upload-log-badge">{{ failedDocumentCount }}</text>
        </view>
        <view class="card-action" :class="{ disabled: uploading }" @tap="chooseDocument">
          {{ uploading ? '上传中...' : '+添加文档' }}
        </view>
        <text class="upload-tip">(*支持WORD/TXT/EXCEL文本)</text>
        <view v-if="documents.length > 0" class="doc-grid">
          <view v-for="doc in documents" :key="doc.id" class="doc-chip" :class="documentStatusClass(doc.parseStatus)" @tap="openDocumentDetail(doc)">
            <text class="doc-name">{{ shortFileName(doc.fileName) }}</text>
            <text v-if="doc.parseStatus === 'FAILED'" class="doc-status-dot">!</text>
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

    <view v-if="uploadLogsVisible" class="modal-mask doc-detail-mask" @tap="closeUploadLogs">
      <view class="upload-log-modal" @tap.stop>
        <view class="doc-detail-head">
          <view class="doc-detail-title-wrap">
            <text class="doc-detail-title">上传记录</text>
            <text class="doc-detail-subtitle">点击记录可查看解析片段</text>
          </view>
          <text class="doc-detail-close" @tap="closeUploadLogs">×</text>
        </view>

        <scroll-view class="upload-log-list" scroll-y>
          <view v-if="documents.length === 0" class="chunk-empty">暂无上传记录</view>
          <view v-for="doc in documents" v-else :key="doc.id" class="upload-log-row" @tap="openDocumentDetailFromLog(doc)">
            <view class="upload-log-main">
              <text class="upload-log-name">{{ doc.fileName || '文档' }}</text>
              <text class="upload-log-meta">
                {{ doc.fileType || '文档' }} · 片段 {{ doc.chunkCount || 0 }}
              </text>
              <text v-if="doc.parseError" class="upload-log-error">{{ doc.parseError }}</text>
            </view>
            <view class="upload-log-side">
              <text class="upload-log-status" :class="documentStatusTextClass(doc.parseStatus)">
                {{ parseStatusText(doc.parseStatus) || '-' }}
              </text>
              <text class="upload-log-delete" @tap.stop="confirmRemoveDocument(doc)">删除</text>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>

    <view v-if="docDetailVisible" class="modal-mask doc-detail-mask" @tap="closeDocumentDetail">
      <view class="doc-detail-modal" @tap.stop>
        <view class="doc-detail-head">
          <view class="doc-detail-title-wrap">
            <text class="doc-detail-title">{{ selectedDocument?.fileName || '文档详情' }}</text>
            <text class="doc-detail-subtitle">{{ parseStatusText(selectedDocument?.parseStatus) }}</text>
          </view>
          <text class="doc-detail-close" @tap="closeDocumentDetail">×</text>
        </view>

        <view class="doc-summary">
          <view class="summary-item">
            <text class="summary-label">片段数</text>
            <text class="summary-value">{{ selectedDocument?.chunkCount || 0 }}</text>
          </view>
          <view class="summary-item">
            <text class="summary-label">类型</text>
            <text class="summary-value">{{ selectedDocument?.fileType || '-' }}</text>
          </view>
          <view class="summary-item">
            <text class="summary-label">状态</text>
            <text class="summary-value" :class="documentStatusTextClass(selectedDocument?.parseStatus)">
              {{ parseStatusText(selectedDocument?.parseStatus) || '-' }}
            </text>
          </view>
        </view>

        <text v-if="selectedDocument?.parseError" class="doc-error">失败原因：{{ selectedDocument.parseError }}</text>

        <view class="chunk-toolbar">
          <text class="chunk-title">解析片段</text>
          <text class="chunk-refresh" @tap="reloadDocumentDetail">刷新</text>
        </view>

        <scroll-view class="chunk-list" scroll-y>
          <view v-if="loadingChunks" class="chunk-empty">加载中...</view>
          <view v-else-if="documentChunks.length === 0" class="chunk-empty">暂无可用片段</view>
          <view v-else>
            <view v-for="chunk in documentChunks" :key="chunk.id" class="chunk-card" :class="{ 'chunk-disabled': !chunk.enabled }">
              <view class="chunk-head">
                <text class="sentiment-badge" :class="sentimentClass(chunk.sentiment)">{{ sentimentLabel(chunk.sentiment) }}</text>
                <text class="chunk-enabled">{{ chunk.enabled ? '启用中' : '已停用' }}</text>
              </view>
              <text class="chunk-text">{{ chunk.chunkText }}</text>
              <view class="chunk-actions">
                <text class="chunk-action" @tap="changeChunkSentiment(chunk)">改类型</text>
                <text class="chunk-action" @tap="toggleChunkEnabled(chunk)">{{ chunk.enabled ? '停用' : '启用' }}</text>
                <text class="chunk-action danger" @tap="removeChunk(chunk)">删除</text>
              </view>
            </view>
          </view>
        </scroll-view>

        <view class="doc-detail-actions">
          <button class="delete-doc-btn" @tap="confirmRemoveDocument(selectedDocument)">删除文档</button>
          <button class="close-doc-btn" @tap="closeDocumentDetail">关闭</button>
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
  deleteCommentReplyChunk,
  deleteCommentReplyQaItem,
  getCommentReplyDocumentChunks,
  getCommentReplyDocuments,
  getCommentReplyQaItems,
  updateCommentReplyChunkEnabled,
  updateCommentReplyChunkSentiment,
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
const uploadLogsVisible = ref(false)
const docDetailVisible = ref(false)
const selectedDocument = ref(null)
const documentChunks = ref([])
const loadingChunks = ref(false)
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
  return qaItems.value.map((item) => ({
    rowKey: `qa-${item.id}`,
    type: 'QA',
    id: item.id,
    title: item.title || '回复内容',
    question: item.question || '',
    answer: item.answer || '',
    sentiment: item.sentiment,
    raw: item,
  }))
})

const failedDocumentCount = computed(() => {
  return documents.value.filter((doc) => doc.parseStatus === 'FAILED').length
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
        const uploaded = await uploadCommentReplyDocument(file.path)
        uni.showToast({ title: '上传成功，解析中', icon: 'none' })
        await refresh()
        await pollDocumentParse(uploaded?.id)
      } catch (error) {
        uni.showToast({ title: error.message || '上传失败', icon: 'none' })
      } finally {
        uploading.value = false
      }
    },
  })
}

function confirmRemoveDocument(doc) {
  if (!doc?.id) {
    return
  }
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
        if (selectedDocument.value?.id === doc.id) {
          closeDocumentDetail()
        }
        await refresh()
      } catch (error) {
        uni.showToast({ title: error.message || '删除失败', icon: 'none' })
      }
    },
  })
}

async function pollDocumentParse(documentId) {
  if (!documentId) {
    return
  }
  for (let index = 0; index < 8; index += 1) {
    await sleep(900)
    await refresh()
    const latest = documents.value.find((doc) => doc.id === documentId)
    if (!latest) {
      return
    }
    if (latest.parseStatus === 'SUCCESS') {
      uni.showToast({ title: `已学习${latest.chunkCount || 0}条`, icon: 'success' })
      return
    }
    if (latest.parseStatus === 'FAILED') {
      uni.showToast({ title: latest.parseError || '解析失败', icon: 'none' })
      return
    }
  }
  uni.showToast({ title: '文档仍在解析，可点文档查看进度', icon: 'none' })
}

function sleep(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms)
  })
}

async function openDocumentDetail(doc) {
  selectedDocument.value = doc
  docDetailVisible.value = true
  await loadDocumentChunks(doc.id)
}

async function openDocumentDetailFromLog(doc) {
  uploadLogsVisible.value = false
  await openDocumentDetail(doc)
}

function closeDocumentDetail() {
  docDetailVisible.value = false
  selectedDocument.value = null
  documentChunks.value = []
}

function openUploadLogs() {
  uploadLogsVisible.value = true
}

function closeUploadLogs() {
  uploadLogsVisible.value = false
}

async function reloadDocumentDetail() {
  if (!selectedDocument.value?.id) {
    return
  }
  await refresh()
  const latest = documents.value.find((doc) => doc.id === selectedDocument.value.id)
  if (latest) {
    selectedDocument.value = latest
  }
  await loadDocumentChunks(selectedDocument.value.id)
}

async function loadDocumentChunks(documentId) {
  loadingChunks.value = true
  try {
    documentChunks.value = await getCommentReplyDocumentChunks(documentId) || []
  } catch (error) {
    uni.showToast({ title: error.message || '加载片段失败', icon: 'none' })
  } finally {
    loadingChunks.value = false
  }
}

function changeChunkSentiment(chunk) {
  const options = [
    { label: '好评', value: 'GOOD' },
    { label: '中评', value: 'NEUTRAL' },
    { label: '差评', value: 'BAD' },
    { label: '通用', value: 'UNKNOWN' },
  ]
  uni.showActionSheet({
    itemList: options.map((item) => item.label),
    success: async (res) => {
      const sentiment = options[res.tapIndex]?.value
      if (!sentiment || sentiment === chunk.sentiment) {
        return
      }
      try {
        await updateCommentReplyChunkSentiment(chunk.id, sentiment)
        await reloadDocumentDetail()
      } catch (error) {
        uni.showToast({ title: error.message || '修改失败', icon: 'none' })
      }
    },
  })
}

async function toggleChunkEnabled(chunk) {
  try {
    await updateCommentReplyChunkEnabled(chunk.id, !chunk.enabled)
    await reloadDocumentDetail()
  } catch (error) {
    uni.showToast({ title: error.message || '操作失败', icon: 'none' })
  }
}

function removeChunk(chunk) {
  uni.showModal({
    title: '确认删除片段？',
    content: '删除后 AI 将不再使用这段知识。',
    confirmText: '确认',
    cancelText: '取消',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        await deleteCommentReplyChunk(chunk.id)
        await reloadDocumentDetail()
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

function documentStatusClass(value) {
  return {
    SUCCESS: 'doc-chip-success',
    FAILED: 'doc-chip-failed',
    PARSING: 'doc-chip-parsing',
    PENDING: 'doc-chip-parsing',
  }[value] || ''
}

function documentStatusTextClass(value) {
  return {
    SUCCESS: 'status-success',
    FAILED: 'status-failed',
    PARSING: 'status-parsing',
    PENDING: 'status-parsing',
  }[value] || ''
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
  position: relative;
  min-height: 132px;
  padding: 20px 16px 18px;
  text-align: center;
}

.upload-log-entry {
  position: absolute;
  top: 12px;
  right: 14px;
  display: flex;
  align-items: center;
  gap: 4px;
  color: #111111;
  font-size: 12px;
  text-decoration: underline;
}

.upload-log-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 15px;
  height: 15px;
  padding: 0 4px;
  border-radius: 999px;
  background: #cf2d23;
  color: #ffffff;
  font-size: 10px;
  line-height: 15px;
  box-sizing: border-box;
  text-decoration: none;
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

.doc-chip-success {
  border-color: #138a3d;
}

.doc-chip-failed {
  border-color: #cf2d23;
  background: #fff5f4;
}

.doc-chip-parsing {
  border-color: #e6b72e;
}

.doc-name {
  flex: 1;
  min-width: 0;
  color: #111111;
  font-size: 12px;
  overflow: hidden;
  white-space: nowrap;
}

.doc-status-dot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  margin-left: 3px;
  border-radius: 999px;
  background: #cf2d23;
  color: #ffffff;
  font-size: 10px;
  font-weight: 700;
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

.doc-detail-mask {
  align-items: flex-end;
  padding: 0 12px 18px;
}

.doc-detail-modal {
  width: 100%;
  max-width: 351px;
  max-height: 84vh;
  padding: 16px 14px 14px;
  border-radius: 18px;
  background: #ffffff;
  box-sizing: border-box;
}

.upload-log-modal {
  width: 100%;
  max-width: 351px;
  max-height: 78vh;
  padding: 16px 14px 14px;
  border-radius: 18px;
  background: #ffffff;
  box-sizing: border-box;
}

.doc-detail-head,
.doc-summary,
.summary-item,
.chunk-toolbar,
.chunk-head,
.chunk-actions,
.doc-detail-actions {
  display: flex;
  align-items: center;
}

.doc-detail-head {
  justify-content: space-between;
  gap: 12px;
}

.doc-detail-title-wrap {
  flex: 1;
  min-width: 0;
}

.doc-detail-title {
  display: block;
  color: #111111;
  font-size: 16px;
  font-weight: 700;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.doc-detail-subtitle {
  display: block;
  margin-top: 3px;
  color: #888888;
  font-size: 12px;
}

.doc-detail-close {
  flex-shrink: 0;
  color: #111111;
  font-size: 24px;
  line-height: 1;
}

.doc-summary {
  gap: 8px;
  margin-top: 14px;
}

.summary-item {
  flex: 1;
  min-width: 0;
  flex-direction: column;
  justify-content: center;
  height: 54px;
  border-radius: 10px;
  background: #f6f7fb;
}

.summary-label {
  color: #888888;
  font-size: 11px;
}

.summary-value {
  max-width: 100%;
  margin-top: 4px;
  color: #111111;
  font-size: 13px;
  font-weight: 700;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.status-success {
  color: #138a3d;
}

.status-failed {
  color: #cf2d23;
}

.status-parsing {
  color: #9a6a00;
}

.doc-error {
  display: block;
  margin-top: 12px;
  padding: 9px 10px;
  border-radius: 8px;
  background: #fff5f4;
  color: #cf2d23;
  font-size: 12px;
  line-height: 18px;
}

.chunk-toolbar {
  justify-content: space-between;
  margin-top: 15px;
}

.chunk-title {
  color: #111111;
  font-size: 15px;
  font-weight: 700;
}

.chunk-refresh {
  color: #111111;
  font-size: 13px;
  text-decoration: underline;
}

.chunk-list {
  max-height: 48vh;
  margin-top: 10px;
}

.chunk-empty {
  padding: 22px 0;
  color: #888888;
  font-size: 13px;
  text-align: center;
}

.chunk-card {
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.chunk-card:last-child {
  border-bottom: 0;
}

.chunk-disabled {
  opacity: 0.55;
}

.chunk-head {
  justify-content: space-between;
}

.chunk-enabled {
  color: #888888;
  font-size: 12px;
}

.chunk-text {
  display: block;
  margin-top: 8px;
  color: #111111;
  font-size: 13px;
  line-height: 19px;
}

.chunk-actions {
  justify-content: flex-end;
  gap: 16px;
  margin-top: 9px;
}

.chunk-action {
  color: #111111;
  font-size: 13px;
  text-decoration: underline;
}

.chunk-action.danger {
  color: #cf2d23;
}

.doc-detail-actions {
  justify-content: center;
  gap: 14px;
  margin-top: 14px;
}

.delete-doc-btn,
.close-doc-btn {
  width: 86px;
  height: 32px;
  margin: 0;
  padding: 0;
  border-radius: 999px;
  font-size: 13px;
  line-height: 32px;
}

.delete-doc-btn {
  color: #cf2d23;
  background: #fff5f4;
}

.close-doc-btn {
  color: #111111;
  background: linear-gradient(180deg, #f5cd59 0%, #e6b72e 100%);
  font-weight: 700;
}

.upload-log-list {
  max-height: 62vh;
  margin-top: 12px;
}

.upload-log-row {
  display: flex;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.upload-log-row:last-child {
  border-bottom: 0;
}

.upload-log-main {
  flex: 1;
  min-width: 0;
}

.upload-log-name {
  display: block;
  color: #111111;
  font-size: 14px;
  font-weight: 700;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.upload-log-meta {
  display: block;
  margin-top: 4px;
  color: #888888;
  font-size: 12px;
}

.upload-log-error {
  display: block;
  margin-top: 5px;
  color: #cf2d23;
  font-size: 12px;
  line-height: 17px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.upload-log-side {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
}

.upload-log-status {
  font-size: 12px;
  font-weight: 700;
}

.upload-log-delete {
  color: #cf2d23;
  font-size: 12px;
  text-decoration: underline;
}
</style>
