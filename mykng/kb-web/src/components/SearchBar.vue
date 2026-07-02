<template>
  <div class="search-bar">
    <el-input
      v-model="keyword"
      placeholder="搜索文件、笔记、网页..."
      clearable
      class="search-input"
      @keyup.enter="handleSearch"
      @input="handleSuggest"
    >
      <template #prefix>
        <el-icon><Search /></el-icon>
      </template>
      <template #suffix>
        <kbd class="shortcut-hint">Ctrl K</kbd>
      </template>
    </el-input>

    <el-popover
      v-model:visible="showSuggest"
      placement="bottom-start"
      :width="300"
      trigger="manual"
    >
      <template #reference>
        <span></span>
      </template>
      <div class="suggest-list">
        <div
          v-for="item in suggestions"
          :key="item"
          class="suggest-item"
          @click="handleSelectSuggest(item)"
        >
          {{ item }}
        </div>
        <div v-if="suggestions.length === 0" class="suggest-empty">无搜索建议</div>
      </div>
    </el-popover>
  </div>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { searchSuggest } from '@/api/search'

const router = useRouter()
const keyword = ref('')
const showSuggest = ref(false)
const suggestions = ref<string[]>([])

let suggestTimer: ReturnType<typeof setTimeout> | null = null

onUnmounted(() => {
  if (suggestTimer) {
    clearTimeout(suggestTimer)
    suggestTimer = null
  }
})

async function handleSuggest() {
  if (!keyword.value.trim()) {
    showSuggest.value = false
    return
  }

  if (suggestTimer) clearTimeout(suggestTimer)
  suggestTimer = setTimeout(async () => {
    try {
      const res = await searchSuggest(keyword.value)
      suggestions.value = res.data.data
      showSuggest.value = true
    } catch {
      suggestions.value = []
    }
  }, 300)
}

function handleSearch() {
  showSuggest.value = false
  if (!keyword.value.trim()) return
  router.push({ path: '/search', query: { q: keyword.value } })
}

function handleSelectSuggest(item: string) {
  keyword.value = item
  showSuggest.value = false
  router.push({ path: '/search', query: { q: item } })
}
</script>

<style scoped lang="scss">
.search-bar {
  position: relative;
  width: 100%;
  max-width: 400px;
  flex: 1;
}

.search-input {
  width: 100%;
}

.shortcut-hint {
  display: inline-block;
  padding: 2px 6px;
  font-size: 11px;
  font-family: inherit;
  color: #909399;
  background-color: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  line-height: 1;
  user-select: none;
  pointer-events: none;
}

.suggest-list {
  .suggest-item {
    padding: 8px 12px;
    cursor: pointer;
    font-size: 13px;
    color: #606266;

    &:hover {
      background-color: var(--el-color-primary-light-9);
      color: var(--el-color-primary);
    }
  }

  .suggest-empty {
    padding: 12px;
    text-align: center;
    font-size: 13px;
    color: #909399;
  }
}
</style>
