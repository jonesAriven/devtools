<template>
  <div class="file-detail-page">
    <div class="page-header">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>文件详情</el-breadcrumb-item>
      </el-breadcrumb>
      <div class="page-actions">
        <StarToggle :starred="file?.starred || false" @toggle="handleToggleStar" />
        <el-button size="small" :icon="Share" @click="showShareDialog = true">分享</el-button>
        <el-button size="small" :icon="Download" @click="handleDownload">下载</el-button>
        <el-button size="small" type="danger" :icon="Delete" @click="handleDelete">删除</el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="16">
        <div class="info-card">
          <div class="card-title">文件信息</div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="文件名">{{ file?.name }}</el-descriptions-item>
            <el-descriptions-item label="文件大小">{{ formatFileSize(file?.size || 0) }}</el-descriptions-item>
            <el-descriptions-item label="文件类型">{{ file?.type }}</el-descriptions-item>
            <el-descriptions-item label="解析状态">
              <el-tag :type="parseStatusType">{{ parseStatusLabel }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="上传时间">{{ formatDate(file?.createdAt || '') }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDate(file?.updatedAt || '') }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="info-card">
          <div class="card-title">
            <span>文件内容预览</span>
            <el-button
              v-if="isTextFile && file"
              type="primary"
              size="small"
              :icon="Edit"
              @click="showEditDialog = true"
            >
              在线编辑
            </el-button>
          </div>
          <div class="file-content-preview">
            <FilePreview
              v-if="file"
              :file-id="Number(props.id)"
              :file-name="file.name"
              :file-type="file.type"
            />
            <div v-else class="content-empty">
              <el-icon><Document /></el-icon>
              <span>文件信息加载中...</span>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :span="8">
        <div class="info-card">
          <div class="card-title">标签</div>
          <TagInput
            :resource-id="Number(id)"
            resource-type="file"
          />
        </div>

        <div class="info-card" style="margin-top: 16px">
          <div class="card-title">版本历史</div>
          <div v-if="versions.length === 0" class="empty-state">
            <div class="empty-text">暂无版本记录</div>
          </div>
          <el-timeline v-else>
            <el-timeline-item
              v-for="ver in versions"
              :key="ver.id"
              :timestamp="formatDate(ver.createdAt)"
              placement="top"
            >
              <span>版本 {{ ver.versionNumber }}</span>
              <el-button link type="primary" size="small" @click="handleRollback(ver.id)">回滚</el-button>
            </el-timeline-item>
          </el-timeline>
        </div>
      </el-col>
    </el-row>

    <ShareDialog
      v-model:visible="showShareDialog"
      :resource-id="Number(id)"
      resource-type="file"
    />

    <el-dialog
      v-model="showEditDialog"
      :title="`在线编辑 - ${file?.name || ''}`"
      width="80%"
      top="5vh"
      :close-on-click-modal="false"
      @open="loadFileContent"
    >
      <div v-loading="editLoading" class="edit-content-wrapper">
        <el-input
          v-model="editContent"
          type="textarea"
          :rows="20"
          placeholder="文件内容..."
          :input-style="{ fontFamily: 'Consolas, Monaco, monospace', fontSize: '13px' }"
          :disabled="editLoading"
        />
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showEditDialog = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSaveContent">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Share, Download, Delete, Document, Edit } from '@element-plus/icons-vue'
import { getFileDetail, deleteFile, downloadFile, toggleFileStar, getFileContent, updateFileContent } from '@/api/file'
import { getVersionList, rollbackVersion } from '@/api/version'
import { formatFileSize, formatDate } from '@/utils/format'
import type { KbFile, Version } from '@/types'
import StarToggle from '@/components/StarToggle.vue'
import TagInput from '@/components/TagInput.vue'
import ShareDialog from '@/components/ShareDialog.vue'
import FilePreview from '@/components/FilePreview.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps<{
  id: string
}>()

const router = useRouter()
const file = ref<KbFile | null>(null)
const versions = ref<Version[]>([])
const showShareDialog = ref(false)

// 在线编辑相关状态
const showEditDialog = ref(false)
const editContent = ref('')
const editLoading = ref(false)
const saving = ref(false)

// 文本类文件扩展名集合（与后端 KbFileServiceImpl#isTextFile 保持一致）
const TEXT_EXTENSIONS = new Set([
  'txt', 'md', 'markdown', 'json', 'csv', 'xml', 'html', 'htm',
  'log', 'js', 'ts', 'java', 'py', 'go', 'sql', 'yml', 'yaml',
  'ini', 'conf', 'properties', 'sh', 'bat', 'ps1', 'css', 'scss',
  'vue', 'jsx', 'tsx', 'rs', 'c', 'cpp', 'h', 'hpp', 'cs', 'php',
  'rb', 'swift', 'kt', 'gradle', 'toml',
])

const isTextFile = computed(() => {
  if (!file.value?.name) return false
  const dot = file.value.name.lastIndexOf('.')
  if (dot < 0) return false
  const ext = file.value.name.substring(dot + 1).toLowerCase()
  return TEXT_EXTENSIONS.has(ext)
})

const parseStatusType = computed(() => {
  const status = file.value?.parseStatus
  if (status === 'READY') return 'success'
  if (status === 'PARSING') return 'warning'
  if (status === 'PARSE_FAILED') return 'danger'
  return 'info'
})

const parseStatusLabel = computed(() => {
  const map: Record<string, string> = {
    PENDING: '等待解析',
    PARSING: '解析中',
    READY: '解析完成',
    PARSE_FAILED: '解析失败',
  }
  return map[file.value?.parseStatus || 'PENDING'] || '未知'
})

onMounted(() => {
  loadFile()
  loadVersions()
})

async function loadFile() {
  const res = await getFileDetail(Number(props.id))
  file.value = res.data.data
}

async function loadVersions() {
  const res = await getVersionList(Number(props.id), 'file')
  versions.value = res.data.data
}

async function handleToggleStar() {
  await toggleFileStar(Number(props.id))
  if (file.value) {
    file.value.starred = !file.value.starred
  }
}

async function handleDownload() {
  try {
    await downloadFile(Number(props.id), file.value?.name)
    ElMessage.success('下载已开始')
  } catch (e) {
    console.error('下载失败', e)
    ElMessage.error('下载失败，请重试')
  }
}

async function handleDelete() {
  await ElMessageBox.confirm('确定要删除此文件吗？', '提示', { type: 'warning' })
  await deleteFile(Number(props.id))
  ElMessage.success('已删除')
  router.back()
}

async function handleRollback(versionId: number) {
  await ElMessageBox.confirm('确定要回滚到此版本吗？', '提示', { type: 'warning' })
  await rollbackVersion(versionId)
  ElMessage.success('已回滚')
  loadFile()
}

async function loadFileContent() {
  editLoading.value = true
  try {
    const res = await getFileContent(Number(props.id))
    editContent.value = res.data.data || ''
  } catch (e: any) {
    ElMessage.error('加载文件内容失败：' + (e?.message || ''))
  } finally {
    editLoading.value = false
  }
}

async function handleSaveContent() {
  await ElMessageBox.confirm('保存将覆盖原文件内容，且会创建新版本。是否继续？', '提示', { type: 'warning' })
  saving.value = true
  try {
    await updateFileContent(Number(props.id), editContent.value)
    ElMessage.success('保存成功')
    showEditDialog.value = false
    // 重新加载文件信息和版本
    loadFile()
    loadVersions()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message || ''))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped lang="scss">
.file-detail-page {
  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
  }

  .page-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .card-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }

  .file-content-preview {
    max-height: 800px;
    overflow-y: auto;
    padding: 0;
    background-color: #f5f7fa;
    border-radius: 4px;

    .content-empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 32px;
      color: #c0c4cc;
      justify-content: center;
    }
  }

  .edit-content-wrapper {
    min-height: 400px;
  }
}

@media (max-width: 768px) {
  .file-detail-page {
    .page-actions {
      flex-wrap: wrap;
      gap: 4px;

      .el-button {
        margin: 0;
      }
    }

    .file-content-preview {
      max-height: 300px;
    }
  }
}
</style>
