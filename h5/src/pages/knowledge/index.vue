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
        <view class="card-action" @tap="openQaForm()">+添加知识问答</view>
        <text class="qa-tip">(输入文字内容)</text>

        <view v-if="knowledgeRows.length === 0" class="empty-state">
          <view class="empty-icon">?</view>
          <text class="empty-title">暂无知识内容</text>
          <text class="empty-desc">可以上传文档，或手工添加顾客问题和知识答案。</text>
        </view>

        <scroll-view v-else class="knowledge-list" scroll-y>
          <view v-for="row in knowledgeRows" :key="row.rowKey" class="knowledge-row">
            <view class="question-icon">?</view>
            <view class="row-main">
              <view class="row-head">
                <text class="category-badge" :class="categoryClass(row.category)">{{ categoryLabel(row.category) }}</text>
                <text class="row-title">{{ row.title }}</text>
                <view class="row-actions">
                  <text v-if="row.type === 'QA'" class="row-link" @tap="toggleAnswer(row.id)">查看回答</text>
                  <text v-if="row.type === 'QA'" class="row-close" @tap="removeQa(row.id)">×</text>
                </view>
              </view>
              <text class="row-question">Q：{{ row.question }}</text>
              <text v-if="row.type === 'QA' && expandedQaId === row.id" class="row-answer">A：{{ row.answer }}</text>
              <view class="row-foot">
                <text v-if="row.statusText" class="status-text">{{ row.statusText }}</text>
                <text v-if="row.type === 'QA'" class="edit-link" @tap="openQaForm(row.raw)">编辑</text>
              </view>
            </view>
          </view>
        </scroll-view>
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
          <text class="form-label">类型：</text>
          <picker class="form-picker" :range="categoryOptions" range-key="label" :value="categoryIndex" @change="changeCategory">
            <view class="picker-value">
              <text>{{ categoryOptions[categoryIndex].label }}</text>
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

    <view v-if="uploadPreviewVisible" class="modal-mask doc-detail-mask" @tap="closeUploadPreview">
      <view class="upload-preview-modal" @tap.stop>
        <view class="doc-detail-head">
          <view class="doc-detail-title-wrap">
            <text class="doc-detail-title">解析预览</text>
            <text class="doc-detail-subtitle">确认片段无误后再上传学习</text>
          </view>
          <text class="doc-detail-close" @tap="closeUploadPreview">×</text>
        </view>

        <view v-if="pendingUploadFile" class="preview-summary">
          <view class="preview-summary-row">
            <text class="preview-label">文件名</text>
            <text class="preview-value">{{ pendingUploadFile.name }}</text>
          </view>
          <view class="preview-summary-row">
            <text class="preview-label">格式</text>
            <text class="preview-value">{{ pendingUploadFile.type }}</text>
          </view>
          <view class="preview-summary-row">
            <text class="preview-label">大小</text>
            <text class="preview-value">{{ formatFileSize(pendingUploadFile.size) }}</text>
          </view>
        </view>

        <view class="preview-content">
          <view class="chunk-toolbar">
            <text class="preview-title">{{ uploadPreviewSummaryText }}</text>
            <text v-if="previewingUpload" class="status-text">解析中...</text>
          </view>
          <scroll-view v-if="uploadPreviewChunks.length > 0" class="preview-chunk-list" scroll-y>
            <view v-for="chunk in uploadPreviewChunks" :key="chunk.index" class="preview-chunk-card">
              <view class="chunk-head"><text class="chunk-enabled">片段 {{ chunk.index }}</text></view>
              <text class="chunk-text">{{ chunk.chunkText }}</text>
            </view>
          </scroll-view>
          <view v-else class="preview-box muted">{{ pendingPreviewHint }}</view>
        </view>

        <view class="doc-detail-actions">
          <button class="close-doc-btn" :loading="uploading" :disabled="previewingUpload || uploadPreviewChunks.length === 0" @tap="confirmUploadDocument">确认上传</button>
          <button class="delete-doc-btn" @tap="closeUploadPreview">取消</button>
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
              <view class="chunk-head"><text class="chunk-enabled">{{ chunk.enabled ? '启用中' : '已停用' }}</text></view>
              <text class="chunk-text">{{ chunk.chunkText }}</text>
              <view class="chunk-actions">
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
  clearKnowledge,
  createKnowledgeQaItem,
  deleteKnowledgeDocument,
  deleteKnowledgeChunk,
  deleteKnowledgeQaItem,
  getKnowledgeDocumentChunks,
  getKnowledgeDocuments,
  getKnowledgeQaItems,
  previewKnowledgeDocument,
  reportMtH5BehaviorEvent,
  reportMtH5PageView,
  updateKnowledgeChunkEnabled,
  updateKnowledgeQaItem,
  uploadKnowledgeDocument,
} from '@/utils/mt-auth'

const categoryOptions = [
  { label: '普通类', value: 'GENERAL' },
  { label: '配送类', value: 'DELIVERY' },
  { label: '售后类', value: 'AFTER_SALE' },
  { label: '其他', value: 'OTHER' },
]

const qaItems = ref([])
const documents = ref([])
const uploading = ref(false)
const savingQa = ref(false)
const qaFormVisible = ref(false)
const uploadPreviewVisible = ref(false)
const uploadLogsVisible = ref(false)
const docDetailVisible = ref(false)
const selectedDocument = ref(null)
const documentChunks = ref([])
const loadingChunks = ref(false)
const previewingUpload = ref(false)
const pendingUploadFile = ref(null)
const uploadPreviewResult = ref(null)
const pendingPreviewHint = ref('')
const editingQaId = ref(null)
const expandedQaId = ref(null)
const qaForm = reactive({
  title: '',
  question: '',
  answer: '',
  category: 'GENERAL',
})

const categoryIndex = computed(() => {
  const index = categoryOptions.findIndex((item) => item.value === qaForm.category)
  return index === -1 ? 0 : index
})

const knowledgeRows = computed(() => {
  return qaItems.value.map((item) => ({
    rowKey: `qa-${item.id}`,
    type: 'QA',
    id: item.id,
    title: item.title || '知识问答',
    category: item.category || 'GENERAL',
    question: item.question || '',
    answer: item.answer || '',
    raw: item,
  }))
})

const failedDocumentCount = computed(() => {
  return documents.value.filter((doc) => doc.parseStatus === 'FAILED').length
})

const uploadPreviewSummaryText = computed(() => {
  const total = uploadPreviewResult.value?.chunkCount || 0
  const shown = uploadPreviewChunks.value.length
  if (total > shown && shown > 0) {
    return `将生成 ${total} 个知识片段，仅展示前 ${shown} 个`
  }
  return `将生成 ${total} 个知识片段`
})

onMounted(() => {
  reportMtH5PageView('KNOWLEDGE', 'HOME_CARD')
  refresh()
})

async function refresh() {
  try {
    const [qa, docs] = await Promise.all([
      getKnowledgeQaItems(),
      getKnowledgeDocuments(),
    ])
    qaItems.value = qa || []
    documents.value = docs || []
  } catch (error) {
    uni.showToast({ title: error.message || '加载失败', icon: 'none' })
  }
}

function chooseDocument() {
  reportMtH5BehaviorEvent('H5_KNOWLEDGE_ACTION', {
    actionCode: 'DOCUMENT_SELECT_OPEN',
  })
  uni.chooseFile({
    count: 1,
    extension: ['.txt', '.docx', '.xlsx'],
    success: async (res) => {
      const file = res.tempFiles?.[0]
      if (!file?.path) {
        return
      }
      await openUploadPreview(file)
    },
  })
}

async function openUploadPreview(file) {
  pendingUploadFile.value = normalizeUploadFile(file)
  uploadPreviewResult.value = null
  pendingPreviewHint.value = '正在解析文档，请稍候...'
  uploadPreviewVisible.value = true
  previewingUpload.value = true
  try {
    uploadPreviewResult.value = await previewKnowledgeDocument(file.path)
    reportMtH5BehaviorEvent('H5_KNOWLEDGE_ACTION', {
      actionCode: 'DOCUMENT_PREVIEW_RESULT',
      result: 'SUCCESS',
      fileType: pendingUploadFile.value.type,
      chunkCount: Number(uploadPreviewResult.value?.chunkCount || 0),
    })
    pendingPreviewHint.value = uploadPreviewChunks.value.length > 0
      ? ''
      : '没有解析出可用知识片段，建议取消上传并调整文档内容。'
  } catch (error) {
    reportMtH5BehaviorEvent('H5_KNOWLEDGE_ACTION', {
      actionCode: 'DOCUMENT_PREVIEW_RESULT',
      result: 'FAILED',
      fileType: pendingUploadFile.value?.type || 'UNKNOWN',
    })
    pendingPreviewHint.value = error.message || '解析预览失败，请调整文档后重试。'
  } finally {
    previewingUpload.value = false
  }
}

function closeUploadPreview() {
  if (uploading.value) {
    return
  }
  resetUploadPreview()
}

async function confirmUploadDocument() {
  if (!pendingUploadFile.value?.path) {
    uni.showToast({ title: '请先选择文档', icon: 'none' })
    return
  }
  if (uploadPreviewChunks.value.length === 0) {
    uni.showToast({ title: '请先完成解析预览', icon: 'none' })
    return
  }
  reportMtH5BehaviorEvent('H5_KNOWLEDGE_ACTION', {
    actionCode: 'DOCUMENT_UPLOAD_SUBMIT',
    fileType: pendingUploadFile.value.type,
    chunkCount: uploadPreviewChunks.value.length,
  })
  uploading.value = true
  try {
    const uploaded = await uploadKnowledgeDocument(pendingUploadFile.value.path)
    resetUploadPreview()
    uni.showToast({ title: '上传成功，解析中', icon: 'none' })
    await refresh()
    await pollDocumentParse(uploaded?.id)
  } catch (error) {
    uni.showToast({ title: error.message || '上传失败', icon: 'none' })
  } finally {
    uploading.value = false
  }
}

function normalizeUploadFile(file) {
  const name = file.name || file.path?.split('/').pop() || 'knowledge'
  return {
    name,
    path: file.path,
    size: file.size || 0,
    type: resolveUploadFileType(name),
  }
}

function resetUploadPreview() {
  uploadPreviewVisible.value = false
  pendingUploadFile.value = null
  uploadPreviewResult.value = null
  pendingPreviewHint.value = ''
  previewingUpload.value = false
}

function resolveUploadFileType(name) {
  const lower = String(name || '').toLowerCase()
  if (lower.endsWith('.txt')) {
    return 'TXT'
  }
  if (lower.endsWith('.docx')) {
    return 'WORD'
  }
  if (lower.endsWith('.xlsx')) {
    return 'EXCEL'
  }
  return '文档'
}

const uploadPreviewChunks = computed(() => uploadPreviewResult.value?.chunks || [])

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
        await deleteKnowledgeDocument(doc.id)
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
    documentChunks.value = await getKnowledgeDocumentChunks(documentId) || []
  } catch (error) {
    uni.showToast({ title: error.message || '加载片段失败', icon: 'none' })
  } finally {
    loadingChunks.value = false
  }
}

async function toggleChunkEnabled(chunk) {
  try {
    await updateKnowledgeChunkEnabled(chunk.id, !chunk.enabled)
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
        await deleteKnowledgeChunk(chunk.id)
        await reloadDocumentDetail()
      } catch (error) {
        uni.showToast({ title: error.message || '删除失败', icon: 'none' })
      }
    },
  })
}

function openQaForm(item) {
  reportMtH5BehaviorEvent('H5_KNOWLEDGE_ACTION', {
    actionCode: 'QA_FORM_OPEN',
    mode: item?.id ? 'EDIT' : 'CREATE',
  })
  editingQaId.value = item?.id || null
  qaForm.title = item?.title || ''
  qaForm.question = item?.question || ''
  qaForm.answer = item?.answer || ''
  qaForm.category = item?.category || 'GENERAL'
  qaFormVisible.value = true
}

function changeCategory(event) {
  const index = Number(event?.detail?.value || 0)
  qaForm.category = categoryOptions[index]?.value || 'GENERAL'
}

function closeQaForm() {
  if (savingQa.value) {
    return
  }
  qaFormVisible.value = false
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
      question,
      answer,
      category: qaForm.category || 'GENERAL',
      enabled: true,
    }
    if (editingQaId.value) {
      await updateKnowledgeQaItem(editingQaId.value, payload)
    } else {
      await createKnowledgeQaItem(payload)
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
    content: '删除后 AI 将不再使用这条知识问答。',
    confirmText: '确认',
    cancelText: '取消',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        await deleteKnowledgeQaItem(id)
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
    content: '删除后已上传文档和知识问答都会清空。',
    confirmText: '确认',
    cancelText: '取消',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        await clearKnowledge()
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

function categoryLabel(value) {
  return categoryOptions.find((item) => item.value === value)?.label || '普通类'
}

function categoryClass(value) {
  return {
    GENERAL: 'category-general',
    DELIVERY: 'category-delivery',
    AFTER_SALE: 'category-after-sale',
    OTHER: 'category-other',
  }[value] || 'category-general'
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

function formatFileSize(value) {
  const size = Number(value || 0)
  if (size <= 0) {
    return '-'
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)}KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)}MB`
}
</script>

<style scoped>
.page {
  width: 100%;
  min-height: 100vh;
  box-sizing: border-box;
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

.knowledge-list {
  max-height: 330px;
  margin-top: 6px;
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

.category-badge {
  flex-shrink: 0;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 17px;
  font-weight: 700;
}

.category-general {
  background: #eef1f6;
  color: #55617a;
}

.category-delivery {
  background: #e7f2ff;
  color: #2b6cb0;
}

.category-after-sale {
  background: #fdeeee;
  color: #cf4b4b;
}

.category-other {
  background: #f1f0ee;
  color: #8a7d63;
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

.status-text {
  color: #888888;
  font-size: 12px;
}

.clear-card {
  justify-content: center;
  gap: 10px;
  min-height: 54px;
  margin-top: 18px;
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

.upload-preview-modal {
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

.preview-summary {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f6f7fb;
}

.preview-summary-row {
  display: flex;
  gap: 10px;
  padding: 5px 0;
}

.preview-label {
  width: 48px;
  flex-shrink: 0;
  color: #888888;
  font-size: 12px;
}

.preview-value {
  flex: 1;
  min-width: 0;
  color: #111111;
  font-size: 12px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.preview-content {
  margin-top: 14px;
}

.preview-title {
  display: block;
  color: #111111;
  font-size: 14px;
  font-weight: 700;
}

.preview-box {
  max-height: 210px;
  margin-top: 8px;
  padding: 10px;
  border: 1px solid #eeeeee;
  border-radius: 10px;
  background: #fbfbfb;
  color: #111111;
  font-size: 12px;
  line-height: 18px;
  box-sizing: border-box;
  white-space: pre-wrap;
}

.preview-box.muted {
  color: #888888;
  text-align: center;
}

.preview-chunk-list {
  max-height: 260px;
  margin-top: 8px;
}

.preview-chunk-card {
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}

.preview-chunk-card:last-child {
  border-bottom: 0;
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
