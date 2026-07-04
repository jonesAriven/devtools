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

      <!-- 搜索历史 -->
      <div v-if="showHistory" class="search-history">
        <div class="history-header">
          <span class="history-title">
            <el-icon><Clock /></el-icon>
            搜索历史
          </span>
          <el-button link type="info" size="small" @click="clearHistory">
            <el-icon><Delete /></el-icon>
            清空
          </el-button>
        </div>
        <div class="history-tags">
          <el-tag
            v-for="(item, idx) in searchHistory"
            :key="idx"
            class="history-tag"
            closable
            @close="removeHistoryItem(idx)"
            @click="useHistoryItem(item)"
          >
            {{ item.keyword }}
          </el-tag>
        </div>
      </div>
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
            <el-form-item label="标签">
              <el-select
                v-model="filters.tagId"
                placeholder="全部标签"
                clearable
                filterable
                style="width: 100%"
                @change="handleNewSearch"
              >
                <el-option
                  v-for="tag in tagList"
                  :key="tag.id"
                  :label="tag.name"
                  :value="tag.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item v-if="filters.type !== 'all' || filters.tagId">
              <el-button type="info" plain size="small" @click="resetFilters">
                <el-icon><RefreshLeft /></el-icon>
                重置筛选
              </el-button>
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
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Loading, Clock, Delete, RefreshLeft } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import { search } from '@/api/search'
import { getTagList } from '@/api/tag'
import type { SearchResult, Tag } from '@/types'
import StarToggle from '@/components/StarToggle.vue'
import { toggleFileStar } from '@/api/file'
import { toggleDocStar } from '@/api/doc'
import { toggleWebPageStar } from '@/api/web'
import { typeLabel, navigateToResource } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const results = ref<SearchResult[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const searched = ref(false)
const loading = ref(false)

const tagList = ref<Tag[]>([])

const filters = reactive({
  type: 'all' as string,
  tagId: undefined as number | undefined,
})

/* ============ 搜索历史 ============ */
interface HistoryItem {
  keyword: string
  ts: number
}
const HISTORY_KEY = 'kb-search-history'
const HISTORY_MAX = 10
const searchHistory = ref<HistoryItem[]>(loadHistory())

function loadHistory(): HistoryItem[] {
  try {
    const raw = localStorage.getItem(HISTORY_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw) as HistoryItem[]
    return Array.isArray(arr) ? arr.slice(0, HISTORY_MAX) : []
  } catch {
    return []
  }
}

function saveHistory(list: HistoryItem[]) {
  localStorage.setItem(HISTORY_KEY, JSON.stringify(list.slice(0, HISTORY_MAX)))
}

function pushHistory(q: string) {
  const trimmed = q.trim()
  if (!trimmed) return
  // 去重
  const filtered = searchHistory.value.filter(h => h.keyword !== trimmed)
  filtered.unshift({ keyword: trimmed, ts: Date.now() })
  searchHistory.value = filtered.slice(0, HISTORY_MAX)
  saveHistory(searchHistory.value)
}

function removeHistoryItem(idx: number) {
  searchHistory.value.splice(idx, 1)
  saveHistory(searchHistory.value)
}

function clearHistory() {
  searchHistory.value = []
  localStorage.removeItem(HISTORY_KEY)
}

function useHistoryItem(item: HistoryItem) {
  keyword.value = item.keyword
  handleNewSearch()
}

// 仅在未输入关键字且无结果时显示历史
const showHistory = computed(() => {
  if (loading.value) return false
  if (results.value.length > 0) return false
  if (searched.value && keyword.value.trim()) return false
  return searchHistory.value.length > 0
})

// 本地缓存：相同查询 5 分钟内不重复请求（LRU 上限 50 条防止内存膨胀）
const searchCache = new Map<string, { data: SearchResult[]; total: number; ts: number }>()
const CACHE_TTL = 5 * 60 * 1000
const CACHE_MAX_SIZE = 50

/** 写入缓存并维持 LRU 上限：超出时淘汰最早的条目 */
function setCache(key: string, value: { data: SearchResult[]; total: number; ts: number }) {
  searchCache.set(key, value)
  while (searchCache.size > CACHE_MAX_SIZE) {
    const oldestKey = searchCache.keys().next().value
    if (oldestKey === undefined) break
    searchCache.delete(oldestKey)
  }
}

let abortController: AbortController | null = null
let debounceTimer: ReturnType<typeof setTimeout> | null = null

function cacheKey(q: string, type: string, tagId: number | undefined, p: number): string {
  return `${q.trim().toLowerCase()}|${type}|${tagId ?? ''}|${p}`
}

onMounted(async () => {
  // 加载标签列表用于筛选
  try {
    const res = await getTagList()
    tagList.value = res.data.data || []
  } catch {
    // 忽略：标签加载失败不阻塞搜索
  }
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

function debouncedSearch() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    handleNewSearch()
  }, 350)
}

function resetFilters() {
  filters.type = 'all'
  filters.tagId = undefined
  handleNewSearch()
}

async function doSearch() {
  const q = keyword.value.trim()
  if (!q) return

  const typeVal = filters.type && filters.type !== 'all' ? filters.type : ''
  const key = cacheKey(q, typeVal, filters.tagId, page.value)
  const cached = searchCache.get(key)
  if (cached && Date.now() - cached.ts < CACHE_TTL) {
    results.value = cached.data
    total.value = cached.total
    searched.value = true
    searchCache.delete(key)
    searchCache.set(key, cached)
    pushHistory(q)
    return
  }

  if (abortController) abortController.abort()
  abortController = new AbortController()

  loading.value = true
  searched.value = true
  try {
    const res = await search({
      keyword: q,
      type: typeVal || undefined,
      tagId: filters.tagId,
      page: page.value,
      size: pageSize,
    }, abortController.signal)
    results.value = res.data.data.list || []
    total.value = res.data.data.total || 0
    setCache(key, { data: results.value, total: total.value, ts: Date.now() })
    // 仅在搜索到结果或第一页时记录历史
    if (page.value === 1) {
      pushHistory(q)
    }
  } catch (e: any) {
    if (e?.code !== 'ERR_CANCELED' && e?.name !== 'CanceledError') {
      console.error('搜索失败', e)
      results.value = []
      total.value = 0
    }
  } finally {
    loading.value = false
  }
}

function safeTitle(title: string): string {
  if (!title) return ''
  return DOMPurify.sanitize(title, { ALLOWED_TAGS: ['em'], ALLOWED_ATTR: [] })
}

function safeHighlight(html: string): string {
  if (!html) return ''
  return DOMPurify.sanitize(html, { ALLOWED_TAGS: ['em'], ALLOWED_ATTR: [] })
}

function goToResource(item: SearchResult) {
  navigateToResource(router, item.type, item.id)
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

  .search-history {
    max-width: 700px;
    margin: 12px auto 0;

    .history-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 8px;

      .history-title {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        font-size: 13px;
        color: var(--el-text-color-secondary);
      }
    }

    .history-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .history-tag {
      cursor: pointer;
      transition: all 0.2s;

      &:hover {
        transform: translateY(-1px);
        box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
      }
    }
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

    .search-history {
      max-width: 100%;
    }
  }
}
</style>
