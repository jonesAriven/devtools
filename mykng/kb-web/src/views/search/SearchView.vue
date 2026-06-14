<template>
  <div class="search-page">
    <div class="search-header">
      <el-input
        v-model="keyword"
        placeholder="搜索文件、笔记、网页..."
        size="large"
        clearable
        class="search-input"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
        <template #append>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
        </template>
      </el-input>
    </div>

    <el-row :gutter="16">
      <el-col :span="5">
        <div class="info-card filter-card">
          <div class="card-title">筛选条件</div>
          <el-form label-width="60px" label-position="top">
            <el-form-item label="类型">
              <el-select v-model="filters.type" placeholder="全部" clearable style="width: 100%" @change="handleSearch">
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

          <div v-if="results.length === 0 && searched" class="empty-state">
            <el-icon class="empty-icon"><Search /></el-icon>
            <div class="empty-text">未找到相关结果</div>
          </div>

          <div v-else>
            <div v-for="item in results" :key="item.id" class="resource-item" @click="goToResource(item)">
              <el-icon class="resource-icon">
                <Document v-if="item.type === 'doc'" />
                <Picture v-else-if="item.type === 'file'" />
                <Link v-else />
              </el-icon>
              <div class="resource-info">
                <div class="resource-name" v-html="item.title"></div>
                <div class="resource-meta">
                  <span class="resource-type-label">{{ typeLabel(item.type) }}</span>
                  <span v-if="item.highlight" class="resource-highlight" v-html="item.highlight"></span>
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
              @current-change="handleSearch"
            />
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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

const filters = reactive({
  type: 'all' as string,
})

onMounted(() => {
  if (route.query.q) {
    keyword.value = route.query.q as string
    handleSearch()
  }
})

async function handleSearch() {
  if (!keyword.value.trim()) return
  searched.value = true
  const res = await search({
    keyword: keyword.value,
    type: (filters.type as any) || undefined,
    page: page.value,
    pageSize,
  })
  results.value = res.data.data.list
  total.value = res.data.data.total
}

function typeLabel(type: string): string {
  const map: Record<string, string> = { file: '文件', doc: '笔记', web: '网页' }
  return map[type] || type
}

function goToResource(item: SearchResult) {
  if (item.type === 'file') {
    router.push(`/kb/file/${item.id}`)
  } else if (item.type === 'doc') {
    router.push(`/kb/doc/${item.id}`)
  } else if (item.type === 'web') {
    router.push(`/kb/web/${item.id}`)
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
</style>
