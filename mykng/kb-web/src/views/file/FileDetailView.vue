<template>
  <div class="file-detail-page">
    <div class="page-header">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: `${CONTEXT_PATH}/dashboard` }">首页</el-breadcrumb-item>
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
            <el-descriptions-item label="文件大小">{{ formatFileSize(file?.fileSize || 0) }}</el-descriptions-item>
            <el-descriptions-item label="文件类型">{{ file?.mimeType }}</el-descriptions-item>
            <el-descriptions-item label="解析状态">
              <el-tag :type="parseStatusType">{{ parseStatusLabel }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="上传时间">{{ formatDate(file?.createdAt || '') }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDate(file?.updatedAt || '') }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div v-if="file?.parsedContent" class="info-card">
          <div class="card-title">文件内容预览</div>
          <div class="file-content-preview">
            <pre>{{ file.parsedContent }}</pre>
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Share, Download, Delete } from '@element-plus/icons-vue'
import { getFileDetail, deleteFile, downloadFile, toggleFileStar } from '@/api/file'
import { getVersionList, rollbackVersion } from '@/api/version'
import { formatFileSize, formatDate } from '@/utils/format'
import type { KbFile, Version } from '@/types'
import StarToggle from '@/components/StarToggle.vue'
import TagInput from '@/components/TagInput.vue'
import ShareDialog from '@/components/ShareDialog.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CONTEXT_PATH } from '@/config'

const props = defineProps<{
  id: string
}>()

const router = useRouter()
const file = ref<KbFile | null>(null)
const versions = ref<Version[]>([])
const showShareDialog = ref(false)

const parseStatusType = computed(() => {
  const status = file.value?.parseStatus
  if (status === 'completed') return 'success'
  if (status === 'processing') return 'warning'
  if (status === 'failed') return 'danger'
  return 'info'
})

const parseStatusLabel = computed(() => {
  const map: Record<string, string> = {
    pending: '等待解析',
    processing: '解析中',
    completed: '解析完成',
    failed: '解析失败',
  }
  return map[file.value?.parseStatus || 'pending'] || '未知'
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
  const res = await downloadFile(Number(props.id))
  const blob = new Blob([res.data])
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = file.value?.name || 'download'
  link.click()
  window.URL.revokeObjectURL(url)
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

  .file-content-preview {
    max-height: 400px;
    overflow-y: auto;
    padding: 12px;
    background-color: #f5f7fa;
    border-radius: 4px;

    pre {
      white-space: pre-wrap;
      word-break: break-word;
      font-size: 13px;
      line-height: 1.6;
      margin: 0;
    }
  }
}
</style>
