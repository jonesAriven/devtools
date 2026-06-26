<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { Search as SearchIcon } from 'lucide-vue-next'
import { searchApi, tagApi } from '@/api/knowledge'
import type { Doc, Tag } from '@/types/api'
import { timeAgo, truncate } from '@/utils/format'

const router = useRouter()
const keyword = ref('')
const results = ref<Doc[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const hasSearched = ref(false)

const tags = ref<Tag[]>([])
let searchTimer: ReturnType<typeof setTimeout> | null = null

function highlight(text: string): string {
  if (!keyword.value.trim()) return text
  const kw = keyword.value.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return text.replace(new RegExp(kw, 'gi'), (m) => `<mark>${m}</mark>`)
}

async function doSearch() {
  const q = keyword.value.trim()
  if (!q) {
    results.value = []
    total.value = 0
    hasSearched.value = false
    return
  }
  loading.value = true
  hasSearched.value = true
  try {
    const res = await searchApi.search({ q, page: page.value, size: size.value })
    results.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onInput() {
  page.value = 1
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(doSearch, 300)
}

function onPageChange(p: number) {
  page.value = p
  doSearch()
}

function openDoc(doc: Doc) {
  router.push(`/doc/${doc.id}`)
}

function searchByTag(tag: Tag) {
  keyword.value = tag.name
  onInput()
}

async function loadTags() {
  tags.value = (await tagApi.list()) as Tag[]
}

onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
})

onMounted(loadTags)
</script>

<template>
  <div class="search-page">
    <!-- 搜索框 -->
    <div class="search-box">
      <el-input
        v-model="keyword"
        size="large"
        placeholder="搜索文档标题与内容..."
        clearable
        :prefix-icon="SearchIcon"
        @input="onInput"
        @keyup.enter="doSearch"
      />
    </div>

    <!-- 无搜索词：热门标签 -->
    <div v-if="!hasSearched" class="hot-tags">
      <h3 class="section-title">热门标签</h3>
      <div class="tag-cloud">
        <el-tag
          v-for="tag in tags"
          :key="tag.id"
          :color="tag.color"
          effect="plain"
          class="tag-item"
          @click="searchByTag(tag)"
        >
          {{ tag.name }}
          <span v-if="tag.count" class="tag-count">{{ tag.count }}</span>
        </el-tag>
        <el-empty v-if="tags.length === 0" description="暂无标签" :image-size="80" />
      </div>
    </div>

    <!-- 搜索结果 -->
    <div v-else v-loading="loading" class="result-area">
      <div class="result-meta" v-if="total > 0">共找到 {{ total }} 条结果</div>

      <div class="result-list">
        <div v-for="doc in results" :key="doc.id" class="result-item" @click="openDoc(doc)">
          <h4 class="result-title" v-html="highlight(doc.title)"></h4>
          <p class="result-summary" v-html="highlight(truncate(doc.content.replace(/[#*`>\-]/g, ''), 120))"></p>
          <div class="result-footer">
            <el-tag size="small" type="info">文档</el-tag>
            <span class="result-time">{{ timeAgo(doc.updatedAt) }}</span>
          </div>
        </div>

        <el-empty v-if="!loading && results.length === 0" description="未找到相关文档" />
      </div>

      <el-pagination
        v-if="total > size"
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="prev, pager, next"
        class="pagination"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.search-page {
  max-width: 900px;
  margin: 0 auto;
}

.search-box {
  margin-bottom: 24px;

  :deep(.el-input__wrapper) {
    border-radius: 8px;
  }
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #2c3e50;
  margin-bottom: 16px;
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.tag-item {
  cursor: pointer;
  transition: transform 0.2s;

  &:hover {
    transform: translateY(-2px);
  }
}

.tag-count {
  margin-left: 4px;
  opacity: 0.7;
}

.result-meta {
  color: #95a5a6;
  font-size: 13px;
  margin-bottom: 12px;
}

.result-item {
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fff;

  &:hover {
    border-color: #d4a574;
    box-shadow: 0 2px 8px rgba(212, 165, 116, 0.12);
  }
}

.result-title {
  font-size: 16px;
  font-weight: 600;
  color: #2c3e50;
  margin: 0 0 8px;
}

.result-summary {
  color: #7f8c8d;
  font-size: 13px;
  line-height: 1.6;
  margin: 0 0 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.result-footer {
  display: flex;
  align-items: center;
  gap: 12px;
}

.result-time {
  color: #95a5a6;
  font-size: 12px;
}

.pagination {
  margin-top: 20px;
  justify-content: center;
}

:deep(mark) {
  background: #fff3cd;
  color: #b8860b;
  padding: 0 2px;
  border-radius: 2px;
}
</style>
