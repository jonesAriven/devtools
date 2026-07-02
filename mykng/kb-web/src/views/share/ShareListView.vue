<template>
  <div class="share-list">
    <el-card shadow="never">
      <template #header>
        <div class="header-bar">
          <span>分享中心</span>
          <div class="header-actions">
            <el-select v-model="statusFilter" placeholder="状态筛选" style="width: 130px" @change="applyFilter">
              <el-option label="全部" value="all" />
              <el-option label="有效" value="valid" />
              <el-option label="已过期" value="expired" />
            </el-select>
            <el-button :icon="Refresh" @click="loadData">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table :data="filteredList" v-loading="loading" stripe>
        <el-table-column label="资源类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="resourceTagType(row.resourceType)">{{ resourceLabel(row.resourceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="资源标题" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.title || `#${row.resourceId}` }}
          </template>
        </el-table-column>
        <el-table-column label="分享码" width="180">
          <template #default="{ row }">
            <el-link type="primary" underline="never" @click="copyCode(row.code)">
              <span class="mono-text">{{ row.code }}</span>
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="提取码" width="120">
          <template #default="{ row }">
            <span v-if="row.extractCode" class="mono-text copy-cell" @click="copyCode(row.extractCode)">
              {{ row.extractCode }}
            </span>
            <span v-else class="muted">无</span>
          </template>
        </el-table-column>
        <el-table-column label="过期时间" width="180">
          <template #default="{ row }">
            <span v-if="!row.expireAt" class="muted">永久</span>
            <span v-else>{{ formatDate(row.expireAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="isExpired(row) ? 'info' : 'success'" size="small">
              {{ isExpired(row) ? '已过期' : '有效' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="viewCount" label="访问次数" width="100" />
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button text @click="copyLink(row)">复制链接</el-button>
            <el-button text type="danger" @click="handleCancel(row.id)">取消分享</el-button>
          </template>
        </el-table-column>
      <template #empty>
        <el-empty description="暂无数据" />
      </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getMyShares, cancelShare } from '@/api/share'
import type { Share } from '@/types'
import { formatDate } from '@/utils/format'
import { confirmDanger } from '@/utils/confirm'
import { ElMessage } from 'element-plus'
import { CONTEXT_PATH } from '@/config'

const loading = ref(false)
const list = ref<Share[]>([])
const statusFilter = ref<'all' | 'valid' | 'expired'>('all')

const filteredList = computed(() => {
  if (statusFilter.value === 'all') return list.value
  return list.value.filter((s) => {
    const expired = isExpired(s)
    return statusFilter.value === 'expired' ? expired : !expired
  })
})

function applyFilter() {
  // 由 computed 自动响应
}

function isExpired(row: any): boolean {
  if (!row?.expireAt) return false
  return new Date(row.expireAt).getTime() < Date.now()
}

function resourceLabel(type: string): string {
  const map: Record<string, string> = { doc: '笔记', web: '网页', file: '文件' }
  return map[type] || type
}

function resourceTagType(type: string): 'success' | 'warning' | 'primary' | 'info' {
  const map: Record<string, 'success' | 'warning' | 'primary' | 'info'> = {
    doc: 'warning',
    web: 'success',
    file: 'primary',
  }
  return map[type] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getMyShares()
    list.value = res.data.data
  } catch {
    ElMessage.error('加载分享列表失败')
  } finally {
    loading.value = false
  }
}

async function copyCode(code: string) {
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}

async function copyLink(row: any) {
  const link = `${window.location.origin}${CONTEXT_PATH}/share/${row.code}`
  try {
    await navigator.clipboard.writeText(link)
    ElMessage.success('分享链接已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

async function handleCancel(id: number) {
  try {
    await confirmDanger('取消后该分享链接将立即失效，确定要取消吗？', '确定取消分享')
    await cancelShare(id)
    ElMessage.success('已取消分享')
    loadData()
  } catch {
    // 用户取消
  }
}

onMounted(loadData)
</script>

<style scoped>
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.mono-text {
  font-family: 'Courier New', Consolas, monospace;
  font-size: 12px;
}

.copy-cell {
  cursor: pointer;
  border-bottom: 1px dashed #c0c4cc;
}

.copy-cell:hover {
  color: #409eff;
}

.muted {
  color: #909399;
  font-size: 12px;
}

@media (max-width: 768px) {
  .header-bar {
    flex-wrap: wrap;
    gap: 8px;
  }

  .header-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .header-actions .el-select {
    width: 100% !important;
  }

  .share-list :deep(.el-table) {
    font-size: 12px;
  }
}
</style>
