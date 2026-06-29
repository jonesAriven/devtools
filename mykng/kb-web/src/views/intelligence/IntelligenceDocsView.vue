<template>
  <div class="intelligence-docs" v-loading="loading">
    <!-- 顶部搜索栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="keyword"
            placeholder="标题/摘要/内容关键词"
            clearable
            style="width: 240px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="docType" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="t in docTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">搜索</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 文档列表 -->
    <el-card shadow="hover" class="mt-3">
      <template #header>
        <div class="card-header">
          <span>文档列表（共 {{ total }} 篇）</span>
          <el-button text type="primary" @click="$router.push('/intelligence')">返回看板</el-button>
        </div>
      </template>
      <el-table :data="docs" size="small" stripe @row-click="goDetail" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="docType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="docTypeTag(row.docType)">{{ docTypeLabel(row.docType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120" show-overflow-tooltip />
        <el-table-column prop="tags" label="标签" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.tags">{{ row.tags }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="entityCount" label="实体" width="70" />
        <el-table-column prop="commandCount" label="命令" width="70" />
        <el-table-column prop="wordCount" label="字数" width="80" />
        <el-table-column prop="createdAt" label="导入时间" width="160" />
      </el-table>

      <el-pagination
        v-if="total > 0"
        class="mt-3"
        :current-page="page"
        :page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDocList } from '@/api/intelligence'
import type { KnDoc, KnDocType } from '@/types/intelligence'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const docs = ref<KnDoc[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const docType = ref<string>('')

const docTypeOptions = [
  { label: '表格', value: 'TABLE' },
  { label: '方案', value: 'PLAN' },
  { label: '时间线', value: 'TIMELINE' },
  { label: '图谱', value: 'GRAPH' },
  { label: '规则', value: 'RULE' },
  { label: '通用', value: 'GENERAL' },
]

const DOC_TYPE_LABELS: Record<string, string> = Object.fromEntries(docTypeOptions.map(o => [o.value, o.label]))
const DOC_TYPE_TAGS: Record<string, '' | 'success' | 'warning' | 'info' | 'danger'> = {
  TABLE: 'info', PLAN: 'success', TIMELINE: 'warning', GRAPH: 'danger', RULE: '', GENERAL: 'info',
}

function docTypeLabel(t: string) { return DOC_TYPE_LABELS[t] || t }
function docTypeTag(t: string) { return DOC_TYPE_TAGS[t] || '' }

async function loadDocs() {
  loading.value = true
  try {
    const res = await getDocList({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      docType: docType.value || undefined,
    })
    const data = res.data.data
    docs.value = data.records || []
    total.value = data.total || 0
  } catch (e) {
    console.error(e)
    ElMessage.error('加载文档列表失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  loadDocs()
}

function onReset() {
  keyword.value = ''
  docType.value = ''
  page.value = 1
  loadDocs()
}

function onPageChange(p: number) {
  page.value = p
  loadDocs()
}

function onSizeChange(s: number) {
  size.value = s
  page.value = 1
  loadDocs()
}

function goDetail(row: KnDoc) {
  router.push(`/intelligence/docs/${row.id}`)
}

onMounted(loadDocs)
</script>

<style scoped lang="scss">
.intelligence-docs {
  padding: 16px;
}
.filter-card {
  :deep(.el-card__body) {
    padding: 12px 16px 0;
  }
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.text-muted { color: #c0c4cc; }
.mt-3 { margin-top: 12px; }
</style>
