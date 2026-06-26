<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Copy, Trash2, Share2 } from 'lucide-vue-next'
import { shareApi } from '@/api/knowledge'
import type { Share } from '@/types/api'
import { formatDateTime, timeAgo } from '@/utils/format'

const loading = ref(false)
const list = ref<Share[]>([])

async function loadList() {
  loading.value = true
  try {
    list.value = (await shareApi.list()) || []
  } finally {
    loading.value = false
  }
}

function buildLink(code: string) {
  return window.location.origin + '/kb/share/' + code
}

async function copyLink(code: string) {
  const link = buildLink(code)
  try {
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(link)
    } else {
      const ta = document.createElement('textarea')
      ta.value = link
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    ElMessage.success('链接已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

async function handleDelete(id: number, title?: string) {
  await ElMessageBox.confirm(
    `确定要删除分享「${title || '未命名'}」吗？删除后链接将立即失效。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
  await shareApi.delete(id)
  ElMessage.success('删除成功')
  loadList()
}

function statusMeta(status: number) {
  if (status === 1) return { text: '有效', type: 'success' as const }
  if (status === 2) return { text: '已过期', type: 'info' as const }
  return { text: '已失效', type: 'danger' as const }
}

function typeLabel(type: string) {
  const map: Record<string, string> = { doc: '文档', folder: '目录', page: '网页' }
  return map[type] || type
}

onMounted(loadList)
</script>

<template>
  <div class="share-page" v-loading="loading">
    <div class="page-header">
      <div class="header-info">
        <h2 class="title">分享中心</h2>
        <p class="desc">管理对外分享的文档链接，可复制链接或取消分享</p>
      </div>
      <el-button type="primary" :icon="Share2" @click="loadList">刷新</el-button>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="list" stripe style="width: 100%">
        <el-table-column label="分享码" prop="code" min-width="140">
          <template #default="{ row }">
            <span class="code-text">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="资源标题" prop="resourceTitle" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" effect="plain" class="mr-2">{{ typeLabel(row.resourceType) }}</el-tag>
            <span>{{ row.resourceTitle || '未命名' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="提取码" prop="extractCode" width="110">
          <template #default="{ row }">
            <span v-if="row.extractCode" class="code-text">{{ row.extractCode }}</span>
            <span v-else class="muted">无</span>
          </template>
        </el-table-column>
        <el-table-column label="访问次数" prop="viewCount" width="100" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type" size="small">{{ statusMeta(row.status).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            <div>{{ formatDateTime(row.createdAt) }}</div>
            <div class="muted small">{{ timeAgo(row.createdAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Copy" @click="copyLink(row.code)">复制链接</el-button>
            <el-button link type="danger" :icon="Trash2" @click="handleDelete(row.id, row.resourceTitle)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无分享记录" />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.share-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;

  .title {
    font-size: 20px;
    font-weight: 600;
    color: #2c3e50;
  }

  .desc {
    margin-top: 4px;
    font-size: 13px;
    color: #7f8c8d;
  }
}

.table-card {
  border-radius: 8px;
}

.code-text {
  font-family: 'Consolas', 'Monaco', monospace;
  color: #d4a574;
  font-weight: 600;
}

.muted {
  color: #bbb;
}

.small {
  font-size: 12px;
}

.mr-2 {
  margin-right: 8px;
}
</style>
