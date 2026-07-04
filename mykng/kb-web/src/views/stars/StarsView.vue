<template>
  <div class="stars-page">
    <div class="page-header">
      <h2 class="page-title">
        <el-icon class="title-icon"><Star /></el-icon>
        我的收藏
      </h2>
      <div class="filter-tabs">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <el-tab-pane label="全部" name="all" />
          <el-tab-pane label="文档" name="doc" />
          <el-tab-pane label="文件" name="file" />
          <el-tab-pane label="网页" name="web" />
        </el-tabs>
      </div>
    </div>

    <div class="page-content">
      <div v-if="loading" class="loading-state">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>

      <div v-else-if="starredItems.length === 0" class="empty-state">
        <el-icon class="empty-icon"><Star /></el-icon>
        <div class="empty-text">暂无收藏内容</div>
        <div class="empty-hint">收藏的文档、文件、网页会显示在这里</div>
      </div>

      <div v-else class="stars-list">
        <div
          v-for="item in starredItems"
          :key="item.type + '-' + item.id"
          class="star-item"
          @click="goToItem(item)"
        >
          <el-icon class="item-icon">
            <Document v-if="item.type === 'doc'" />
            <Picture v-else-if="item.type === 'file'" />
            <Link v-else />
          </el-icon>
          <div class="item-info">
            <div class="item-title">{{ item.title || item.name }}</div>
            <div class="item-meta">
              <span class="type-tag">{{ typeLabel(item.type) }}</span>
              <span class="item-time">{{ formatRelativeTime(item.updatedAt || item.createdAt) }}</span>
            </div>
          </div>
          <div class="item-actions" @click.stop>
            <StarToggle :starred="true" @toggle="handleToggleStar(item)" />
          </div>
        </div>
      </div>

      <div v-if="total > pageSize" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          layout="prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Star, Document, Picture, Link, Loading } from '@element-plus/icons-vue'
import { getStarredList } from '@/api/search'
import { toggleDocStar } from '@/api/doc'
import { toggleFileStar } from '@/api/file'
import { toggleWebPageStar } from '@/api/web'
import { formatRelativeTime } from '@/utils/format'
import StarToggle from '@/components/StarToggle.vue'
import { ElMessage } from 'element-plus'

const router = useRouter()

const activeTab = ref('all')
const loading = ref(false)
const starredItems = ref<any[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

onMounted(() => {
  loadStarredItems()
})

async function loadStarredItems() {
  loading.value = true
  try {
    const res = await getStarredList({
      type: activeTab.value,
      page: page.value,
      size: pageSize.value,
    })
    const data = res?.data?.data
    starredItems.value = data?.list || []
    total.value = data?.total || 0
  } catch {
    // 错误已在拦截器处理
  } finally {
    loading.value = false
  }
}

function handleTabChange() {
  page.value = 1
  loadStarredItems()
}

function handlePageChange() {
  loadStarredItems()
}

function typeLabel(type: string) {
  const map: Record<string, string> = {
    doc: '文档',
    file: '文件',
    web: '网页',
  }
  return map[type] || type
}

function goToItem(item: any) {
  if (item.type === 'doc') {
    router.push(`/doc/${item.id}`)
  } else if (item.type === 'file') {
    router.push(`/file/${item.id}`)
  } else if (item.type === 'web') {
    router.push(`/web/${item.id}`)
  }
}

async function handleToggleStar(item: any) {
  try {
    if (item.type === 'doc') {
      await toggleDocStar(item.id)
    } else if (item.type === 'file') {
      await toggleFileStar(item.id)
    } else if (item.type === 'web') {
      await toggleWebPageStar(item.id)
    }
    starredItems.value = starredItems.value.filter(i => !(i.id === item.id && i.type === item.type))
    total.value = Math.max(0, total.value - 1)
    ElMessage.success('已取消收藏')
  } catch {
    // 错误已在拦截器处理
  }
}
</script>

<style scoped lang="scss">
.stars-page {
  padding: 24px;
  min-height: 100%;
  background-color: #faf8f5;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 22px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 16px 0;
}

.title-icon {
  color: #e6a23c;
  font-size: 26px;
}

.filter-tabs {
  background: #fff;
  border-radius: 8px;
  padding: 0 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);

  :deep(.el-tabs__header) {
    margin-bottom: 0;
  }

  :deep(.el-tabs__item) {
    height: 48px;
    line-height: 48px;
  }
}

.page-content {
  background: #fff;
  border-radius: 8px;
  min-height: 400px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  color: #c0c4cc;
}

.loading-state {
  gap: 12px;
}

.empty-state {
  gap: 8px;
}

.empty-icon {
  font-size: 64px;
  color: #e4e7ed;
  margin-bottom: 8px;
}

.empty-text {
  font-size: 16px;
  color: #909399;
}

.empty-hint {
  font-size: 13px;
  color: #c0c4cc;
}

.stars-list {
  padding: 8px 0;
}

.star-item {
  display: flex;
  align-items: center;
  padding: 14px 24px;
  cursor: pointer;
  transition: background-color 0.2s;
  border-bottom: 1px solid #f5f5f5;

  &:hover {
    background-color: #faf8f5;
  }

  &:last-child {
    border-bottom: none;
  }
}

.item-icon {
  font-size: 22px;
  margin-right: 14px;
  flex-shrink: 0;
  color: #409eff;

  &:deep(.el-icon) {
    font-size: 22px;
  }
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-title {
  font-size: 15px;
  color: #303133;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.item-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: #909399;
}

.type-tag {
  padding: 2px 8px;
  background: #ecf5ff;
  color: #409eff;
  border-radius: 4px;
  font-size: 11px;
}

.item-time {
  color: #c0c4cc;
}

.item-actions {
  flex-shrink: 0;
  margin-left: 12px;
}

.pagination-wrapper {
  padding: 20px 24px;
  display: flex;
  justify-content: center;
}

@media (max-width: 768px) {
  .stars-page {
    padding: 16px 12px;
  }

  .star-item {
    padding: 12px 16px;
  }
}
</style>
