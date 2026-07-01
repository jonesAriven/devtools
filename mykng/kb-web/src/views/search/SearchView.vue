<template>
  <div class="search-page">
    <div class="search-header">
      <el-input
        v-model="keyword"
        placeholder="搜索文件、笔记、网页..."
        size="large"
        clearable
        class="search-input"
        @keyup.enter="handleNewSearch"
        @input="debouncedSearch"
        @clear="handleClear"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
        <template #append>
          <el-button type="primary" :loading="loading" @click="handleNewSearch">搜索</el-button>
        </template>
      </el-input>
    </div>

    <el-row :gutter="16">
      <el-col :span="5">
        <div class="info-card filter-card">
          <div class="card-title">筛选条件</div>
          <el-form label-width="60px" label-position="top">
            <el-form-item label="类型">
              <el-select v-model="filters.type" placeholder="全部" clearable style="width: 100%" @change="handleNewSearch">
                <el-option label="全部" value="all" />
                <el-option label="文件" value="file" />
                <el-option label="笔记" value="doc" />
                <el-option label="网页" value="web" />
              </el-select>
            </el-form-item>
          </el-form>
        </div>
      </el-col>

      <el-col :span="19">
        <div class="info-card">
          <div class="card-title">
            搜索结果
            <span v-if="total > 0" class="result-count">共 {{ total }} 条</span>
          </div>

          <!-- 加载中 -->
          <div v-if="loading" class="loading-state">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>正在搜索...</span>
          </div>

          <!-- 空状态：仅在非首次加载且无结果时显示 -->
          <div v-else-if="results.length === 0 && searched" class="empty-state">
            <el-icon class="empty-icon"><Search /></el-icon>
            <div class="empty-text">未找到相关结果</div>
          </div>

          <!-- 搜索结果列表 -->
          <div v-else>
            <div v-for="item in results" :key="item.id + '-' + item.type" class="resource-item" @click="goToResource(item)">
              <el-icon class="resource-icon">
                <Document v-if="item.type === 'doc'" />
                <Picture v-else-if="item.type === 'file'" />
                <Link v-else />
              </el-icon>
              <div class="resource-info">
                <div class="resource-name" v-html="safeTitle(item.title)"></div>
                <div class="resource-meta">
                  <span class="resource-type-label">{{ typeLabel(item.type) }}</span>
                  <span v-if="item.highlight" class="resource-highlight" v-html="safeHighlight(item.highlight)"></span>
                </div>
              </div>
              <div class="resource-actions">
                <StarToggle :starred="item.starred" @toggle="handleToggleStar(item)" />
              </div>
            </div>
          </div>

          <div v-if="total > pageSize" class="pagination-wrapper">
            <el-pagination
              v-model:current-page="page"
              :page-size="pageSize"
              :total="total"
              layout="prev, pager, next"
              @current-change="handlePageChange"
            />
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Loading } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import { search } from '@/api/search'
import type { SearchResult } from '@/types'
import StarToggle from '@/components/StarToggle.vue'
import { toggleFileStar } from '@/api/file'
import { toggleDocStar } from '@/api/doc'
import { toggleWebPageStar } from '@/api/web'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const results = ref<SearchResult[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const searched = ref(false)
const loading = ref(false)

const filters = reactive({
  type: 'all' as string,
})

// 本地缓存：相同查询 5 分钟内不重复请求
const searchCache = new Map<string, { data: SearchResult[]; total: number; ts: number }>()
const CACHE_TTL = 5 * 60 * 1000

// 请求取消：新请求发出时取消上一个未完成的请求
let abortController: AbortController | null = null

// 防抖
let debounceTimer: ReturnType<typeof setTimeout> | null = null

function cacheKey(q: string, type: string, p: number): string {
  return `${q.trim().toLowerCase()}|${type}|${p}`
}

onMounted(() => {
  if (route.query.q) {
    keyword.value = route.query.q as string
    handleNewSearch()
  }
})

onUnmounted(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
  if (abortController) abortController.abort()
})

async function handleNewSearch() {
  page.value = 1
  await doSearch()
}

async function handlePageChange(p: number) {
  page.value = p
  await doSearch()
}

function handleClear() {
  keyword.value = ''
  results.value = []
  total.value = 0
  searched.value = false
}

/**
 * 带防抖的搜索（用于输入框实时触发）
 */
function debouncedSearch() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    handleNewSearch()
  }, 350)
}

async function doSearch() {
  const q = keyword.value.trim()
  if (!q) return

  // 检查本地缓存
  const typeVal = filters.type && filters.type !== 'all' ? filters.type : ''
  const key = cacheKey(q, typeVal, page.value)
  const cached = searchCache.get(key)
  if (cached && Date.now() - cached.ts < CACHE_TTL) {
    results.value = cached.data
    total.value = cached.total
    searched.value = true
    return
  }

  // 取消上一个未完成请求
  if (abortController) abortController.abort()
  abortController = new AbortController()

  loading.value = true
  searched.value = true
  try {
    const res = await search({
      keyword: q,
      type: typeVal || undefined,
      page: page.value,
      size: pageSize,
    }, abortController.signal)
    results.value = res.data.data.list || []
    total.value = res.data.data.total || 0
    // 写入缓存
    searchCache.set(key, { data: results.value, total: total.value, ts: Date.now() })
  } catch (e: any) {
    // AbortError 是正常取消，不显示错误
    if (e?.code !== 'ERR_CANCELED' && e?.name !== 'CanceledError') {
      console.error('搜索失败', e)
    }
    // 取消时不清空已有结果
    if (e?.code !== 'ERR_CANCELED' && e?.name !== 'CanceledError') {
      results.value = []
      total.value = 0
    }
  } finally {
    loading.value = false
  }
}

/**
 * 清洗标题 HTML（保留 <em> 高亮标签，移除其他危险标签）
 */
function safeTitle(title: string): string {
  if (!title) return ''
  return DOMPurify.sanitize(title, { ALLOWED_TAGS: ['em'], ALLOWED_ATTR: [] })
}

/**
 * 清洗高亮片段 HTML（仅保留 <em> 标签）
 */
function safeHighlight(html: string): string {
  if (!html) return ''
  return DOMPurify.sanitize(html, { ALLOWED_TAGS: ['em'], ALLOWED_ATTR: [] })
}

function typeLabel(type: string): string {
  const map: Record<string, string> = { file: '文件', doc: '笔记', web: '网页' }
  return map[type] || type
}

function goToResource(item: SearchResult) {
  if (item.type === 'file') {
    router.push(`/file/${item.id}`)
  } else if (item.type === 'doc') {
    router.push(`/doc/${item.id}`)
  } else if (item.type === 'web') {
    router.push(`/web/${item.id}`)
  }
}

async function handleToggleStar(item: SearchResult) {
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
.search-page {
  .search-header {
    margin-bottom: 16px;
  }

  .search-input {
    max-width: 700px;
    margin: 0 auto;
    display: block;
  }

  .filter-card {
    position: sticky;
    top: 0;
  }

  .loading-state {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 40px;
    color: #909399;

    .el-icon {
      font-size: 20px;
    }
  }

  .result-count {
    font-size: 13px;
    font-weight: 400;
    color: #909399;
    margin-left: 8px;
  }

  .resource-type-label {
    display: inline-block;
    padding: 0 6px;
    font-size: 12px;
    color: #409eff;
    background-color: #ecf5ff;
    border-radius: 2px;
    margin-right: 8px;
  }

  .resource-highlight {
    font-size: 12px;
    color: #606266;

    :deep(em) {
      color: #f56c6c;
      font-style: normal;
      font-weight: 600;
    }
  }

  .pagination-wrapper {
    display: flex;
    justify-content: center;
    margin-top: 16px;
  }
}

@media (max-width: 768px) {
  .search-page {
    .search-input {
      max-width: 100%;
    }

    .filter-card {
      position: static;
      margin-bottom: 12px;
    }

    .resource-highlight {
      font-size: 11px;
    }
  }
}
</style>
