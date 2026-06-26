<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { spaceApi, docApi, tagApi, shareApi } from '@/api/knowledge'
import type { Space, Doc } from '@/types/api'
import { formatDateTime } from '@/utils/format'
import {
  FileText, FolderOpen, Tag, Share2, Plus, Search, Clock,
} from 'lucide-vue-next'

const router = useRouter()

const loading = ref(false)
const stats = reactive({
  totalDocs: 0,
  totalSpaces: 0,
  totalTags: 0,
  totalShares: 0,
})
const recentDocs = ref<Doc[]>([])
const spaceMap = ref<Record<number, string>>({})

// 顶部统计卡片配置
const cards = [
  { key: 'totalDocs' as const, label: '文档数', icon: FileText, color: '#3b82f6' },
  { key: 'totalSpaces' as const, label: '空间数', icon: FolderOpen, color: '#10b981' },
  { key: 'totalTags' as const, label: '标签数', icon: Tag, color: '#f59e0b' },
  { key: 'totalShares' as const, label: '分享数', icon: Share2, color: '#8b5cf6' },
]

// 快捷操作
const shortcuts = [
  { label: '新建空间', icon: Plus, path: '/space' },
  { label: '搜索文档', icon: Search, path: '/search' },
  { label: '我的标签', icon: Tag, path: '/tag' },
  { label: '分享中心', icon: Share2, path: '/share' },
]

function spaceName(id: number) {
  return spaceMap.value[id] || '未知空间'
}

async function loadData() {
  loading.value = true
  try {
    const [spacesRes, docsRes, tagsRes, sharesRes] = await Promise.allSettled([
      spaceApi.list(),
      docApi.list({ page: 1, size: 5 }),
      tagApi.list(),
      shareApi.list(),
    ])
    // 空间列表 → 统计 + id->name 映射
    if (spacesRes.status === 'fulfilled' && spacesRes.value) {
      stats.totalSpaces = spacesRes.value.length
      const map: Record<number, string> = {}
      spacesRes.value.forEach((s: Space) => (map[s.id] = s.name))
      spaceMap.value = map
    }
    // 最近文档 + 文档总数
    if (docsRes.status === 'fulfilled' && docsRes.value) {
      stats.totalDocs = docsRes.value.total || 0
      recentDocs.value = docsRes.value.list || []
    }
    // 标签数
    if (tagsRes.status === 'fulfilled' && tagsRes.value) {
      stats.totalTags = tagsRes.value.length
    }
    // 分享数
    if (sharesRes.status === 'fulfilled' && sharesRes.value) {
      stats.totalShares = sharesRes.value.length
    }
  } finally {
    loading.value = false
  }
}

function goDoc(doc: Doc) {
  router.push(`/doc/${doc.id}`)
}

onMounted(loadData)
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <!-- 顶部统计卡片 -->
    <div class="stat-cards">
      <el-card v-for="card in cards" :key="card.key" shadow="hover" class="stat-card" :body-style="{ padding: 0 }">
        <div class="stat-inner" :style="{ borderLeftColor: card.color }">
          <div class="stat-info">
            <div class="stat-label">{{ card.label }}</div>
            <div class="stat-value">{{ stats[card.key] }}</div>
          </div>
          <div class="stat-icon" :style="{ background: card.color }">
            <component :is="card.icon" :size="24" color="#fff" />
          </div>
        </div>
      </el-card>
    </div>

    <!-- 主体区 -->
    <div class="main-area">
      <!-- 最近文档 -->
      <el-card class="doc-card" shadow="never">
        <template #header>
          <div class="card-header">
            <Clock :size="18" class="header-icon" />
            <span>最近文档</span>
          </div>
        </template>
        <div v-if="recentDocs.length === 0" class="empty-tip">暂无文档</div>
        <div v-else class="doc-list">
          <div v-for="doc in recentDocs" :key="doc.id" class="doc-item" @click="goDoc(doc)">
            <FileText :size="16" class="doc-icon" />
            <div class="doc-info">
              <div class="doc-title">{{ doc.title }}</div>
              <div class="doc-meta">
                <span>{{ spaceName(doc.spaceId) }}</span>
                <span class="dot">·</span>
                <span>{{ formatDateTime(doc.updatedAt) }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 快捷操作 -->
      <el-card class="shortcut-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>快捷操作</span>
          </div>
        </template>
        <div class="shortcut-list">
          <div
            v-for="item in shortcuts"
            :key="item.path"
            class="shortcut-item"
            @click="router.push(item.path)"
          >
            <div class="shortcut-icon">
              <component :is="item.icon" :size="20" />
            </div>
            <span>{{ item.label }}</span>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped lang="scss">
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 统计卡片 */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  border-radius: 10px;
  overflow: hidden;
}

.stat-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-left: 4px solid #3b82f6;
  height: 88px;
}

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-label {
  font-size: 13px;
  color: #95a5a6;
}

.stat-value {
  font-size: 30px;
  font-weight: 700;
  color: #2c3e50;
  line-height: 1;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 主体区 */
.main-area {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #2c3e50;

  .header-icon {
    color: #d4a574;
  }
}

.empty-tip {
  text-align: center;
  color: #bdc3c7;
  padding: 40px 0;
  font-size: 14px;
}

.doc-list {
  display: flex;
  flex-direction: column;
}

.doc-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #f5f3f0;
  }

  & + .doc-item {
    border-top: 1px solid #f0eeea;
  }
}

.doc-icon {
  color: #d4a574;
  flex-shrink: 0;
}

.doc-info {
  flex: 1;
  min-width: 0;
}

.doc-title {
  font-size: 14px;
  color: #2c3e50;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #95a5a6;
  display: flex;
  gap: 6px;

  .dot {
    color: #ddd;
  }
}

/* 快捷操作 */
.shortcut-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.shortcut-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 8px;
  border-radius: 10px;
  border: 1px solid #ebeef0;
  cursor: pointer;
  font-size: 13px;
  color: #5e6d82;
  transition: all 0.2s;

  &:hover {
    border-color: #d4a574;
    color: #d4a574;
    background: #fdfaf5;
  }
}

.shortcut-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #1a2332;
  color: #d4a574;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
