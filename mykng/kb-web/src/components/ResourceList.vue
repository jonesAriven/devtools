<template>
  <div class="resource-list">
    <div class="list-header">
      <div class="list-view-toggle">
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="list">
            <el-icon><List /></el-icon>
          </el-radio-button>
          <el-radio-button value="grid">
            <el-icon><Grid /></el-icon>
          </el-radio-button>
        </el-radio-group>
      </div>
      <div class="list-sort">
        <el-select v-model="sortBy" size="small" class="sort-select" @change="loadResources">
          <el-option label="按名称" value="name" />
          <el-option label="按时间" value="time" />
          <el-option label="按类型" value="type" />
        </el-select>
      </div>
    </div>

    <div v-if="loading" class="loading-state">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <div v-else-if="resources.length === 0" class="empty-state">
      <el-icon class="empty-icon"><FolderOpened /></el-icon>
      <div class="empty-text">此目录下暂无资源</div>
    </div>

    <div v-else-if="viewMode === 'list'" class="list-view">
      <div v-for="item in resources" :key="`${item.type}-${item.id}`" class="resource-item" @click="goToResource(item)">
        <el-icon class="resource-icon">
          <Document v-if="item.type === 'doc'" />
          <Picture v-else-if="item.type === 'file'" />
          <Link v-else />
        </el-icon>
        <div class="resource-info">
          <div class="resource-name">{{ item.name || item.title }}</div>
          <div class="resource-meta">
            <el-tag size="small" type="info">{{ typeLabel(item.type) }}</el-tag>
            <span>{{ formatRelativeTime(item.updatedAt) }}</span>
          </div>
        </div>
        <div class="resource-actions">
          <StarToggle :starred="item.starred" @toggle="handleToggleStar(item)" />
        </div>
      </div>
    </div>

    <div v-else class="grid-view">
      <div v-for="item in resources" :key="`${item.type}-${item.id}`" class="grid-item" @click="goToResource(item)">
        <div class="grid-icon">
          <el-icon :size="32">
            <Document v-if="item.type === 'doc'" />
            <Picture v-else-if="item.type === 'file'" />
            <Link v-else />
          </el-icon>
        </div>
        <div class="grid-name">{{ item.name || item.title }}</div>
        <div class="grid-meta">
          <el-tag size="small" type="info">{{ typeLabel(item.type) }}</el-tag>
        </div>
      </div>
    </div>

    <div v-if="total > pageSize" class="pagination-wrapper">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadResources"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getFileList } from '@/api/file'
import { getDocList } from '@/api/doc'
import { getWebPageList } from '@/api/web'
import { toggleFileStar } from '@/api/file'
import { toggleDocStar } from '@/api/doc'
import { toggleWebPageStar } from '@/api/web'
import { formatRelativeTime } from '@/utils/format'
import type { KbFile, Doc, WebPage } from '@/types'
import StarToggle from '@/components/StarToggle.vue'

interface ResourceListItem {
  id: number
  name: string
  title: string
  type: 'file' | 'doc' | 'web'
  starred: boolean
  updatedAt: string
}

const props = defineProps<{
  spaceId: number
  folderId: number | null
}>()

const router = useRouter()
const resources = ref<ResourceListItem[]>([])
const loading = ref(false)
const viewMode = ref<'list' | 'grid'>('list')
const sortBy = ref('time')
const page = ref(1)
const pageSize = 50
const total = ref(0)

onMounted(() => {
  loadResources()
})

watch(() => [props.spaceId, props.folderId], () => {
  page.value = 1
  loadResources()
})

async function loadResources() {
  if (!props.spaceId) return
  loading.value = true
  try {
    const folderId = props.folderId || 0
    const params = { page: page.value, pageSize, folderId }
    const [fileRes, docRes, webRes] = await Promise.all([
      getFileList(params as any),
      getDocList(params as any),
      getWebPageList(params as any),
    ])

    const list: ResourceListItem[] = []
    for (const f of fileRes.data.data.list) {
      list.push({ id: f.id, name: f.name, title: f.name, type: 'file', starred: f.starred, updatedAt: f.updatedAt })
    }
    for (const d of docRes.data.data.list) {
      list.push({ id: d.id, name: d.title, title: d.title, type: 'doc', starred: d.starred, updatedAt: d.updatedAt })
    }
    for (const w of webRes.data.data.list) {
      list.push({ id: w.id, name: w.title, title: w.title, type: 'web', starred: w.starred, updatedAt: w.updatedAt })
    }

    total.value = fileRes.data.data.total + docRes.data.data.total + webRes.data.data.total

    if (sortBy.value === 'name') {
      list.sort((a, b) => a.name.localeCompare(b.name))
    } else if (sortBy.value === 'time') {
      list.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
    } else if (sortBy.value === 'type') {
      list.sort((a, b) => a.type.localeCompare(b.type))
    }

    resources.value = list
  } finally {
    loading.value = false
  }
}

function typeLabel(type: string): string {
  const map: Record<string, string> = { file: '文件', doc: '笔记', web: '网页' }
  return map[type] || type
}

function goToResource(item: ResourceListItem) {
  if (item.type === 'file') {
    router.push(`/file/${item.id}`)
  } else if (item.type === 'doc') {
    router.push(`/doc/${item.id}`)
  } else if (item.type === 'web') {
    router.push(`/web/${item.id}`)
  }
}

async function handleToggleStar(item: ResourceListItem) {
  if (item.type === 'file') {
    await toggleFileStar(item.id)
  } else if (item.type === 'doc') {
    await toggleDocStar(item.id)
  } else if (item.type === 'web') {
    await toggleWebPageStar(item.id)
  }
  item.starred = !item.starred
}
</script>

<style scoped lang="scss">
.resource-list {
  .list-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
    gap: 8px;
    flex-wrap: wrap;
  }

  .sort-select {
    width: 120px;
  }

  .loading-state {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 48px 0;
    color: #909399;
  }

  .list-view {
    .resource-item {
      display: flex;
      align-items: center;
      padding: 10px 12px;
      border-bottom: 1px solid #f0f0f0;
      cursor: pointer;
      transition: background-color 0.2s;

      &:hover {
        background-color: #f5f7fa;
      }

      .resource-icon {
        font-size: 22px;
        margin-right: 12px;
        color: #909399;
      }

      .resource-info {
        flex: 1;
        min-width: 0;

        .resource-name {
          font-size: 14px;
          color: #303133;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .resource-meta {
          display: flex;
          align-items: center;
          gap: 8px;
          margin-top: 4px;
          font-size: 12px;
          color: #909399;
        }
      }

      .resource-actions {
        display: flex;
        align-items: center;
      }
    }
  }

  .grid-view {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    gap: 12px;

    .grid-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 16px 8px;
      border: 1px solid #ebeef5;
      border-radius: 4px;
      cursor: pointer;
      transition: border-color 0.2s, box-shadow 0.2s;

      &:hover {
        border-color: #409eff;
        box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
      }

      .grid-icon {
        color: #909399;
        margin-bottom: 8px;
      }

      .grid-name {
        font-size: 13px;
        color: #303133;
        text-align: center;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        max-width: 100%;
      }

      .grid-meta {
        margin-top: 4px;
      }
    }
  }

  .pagination-wrapper {
    display: flex;
    justify-content: center;
    margin-top: 16px;
  }
}

@media (max-width: 768px) {
  .resource-list {
    .list-header {
      flex-direction: column;
      align-items: stretch;

      .sort-select {
        width: 100%;
      }
    }

    .list-view {
      .resource-item {
        .resource-actions {
          flex-shrink: 0;

          .el-button {
            padding: 4px;
          }
        }
      }
    }

    .grid-view {
      grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
      gap: 8px;

      .grid-item {
        padding: 12px 4px;
      }
    }
  }
}
</style>
