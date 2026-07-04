<template>
  <div class="knowledge-view">
    <el-card shadow="never">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="keyword"
            placeholder="搜索标题/标签"
            clearable
            style="width: 240px"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-select v-model="categoryFilter" placeholder="分类" clearable style="width: 140px" @change="handleSearch">
            <el-option label="故障排查" value="故障排查" />
            <el-option label="部署指南" value="部署指南" />
            <el-option label="运维手册" value="运维手册" />
            <el-option label="最佳实践" value="最佳实践" />
          </el-select>
        </div>
        <div class="toolbar-right">
          <el-button @click="loadData">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增文档
          </el-button>
        </div>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="tags" label="标签" width="180" show-overflow-tooltip />
        <el-table-column prop="author" label="作者" width="120" />
        <el-table-column prop="viewCount" label="浏览量" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleView(row)">查看</el-button>
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无数据" />
        </template>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadData"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑文档' : '新增文档'" width="700px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%">
            <el-option label="故障排查" value="故障排查" />
            <el-option label="部署指南" value="部署指南" />
            <el-option label="运维手册" value="运维手册" />
            <el-option label="最佳实践" value="最佳实践" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签" prop="tags">
          <el-input v-model="form.tags" placeholder="多个标签用逗号分隔" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="10" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="文档详情" width="700px">
      <div v-if="currentDoc" class="doc-detail">
        <h3>{{ currentDoc.title }}</h3>
        <div class="doc-meta">
          <el-tag size="small">{{ currentDoc.category }}</el-tag>
          <span class="meta-item">作者：{{ currentDoc.author }}</span>
          <span class="meta-item">浏览：{{ currentDoc.viewCount }}</span>
          <span class="meta-item">{{ formatDate(currentDoc.createdAt) }}</span>
        </div>
        <div v-if="currentDoc.tags" class="doc-tags">
          <el-tag v-for="tag in currentDoc.tags.split(',')" :key="tag" size="small" type="info" class="tag-item">
            {{ tag }}
          </el-tag>
        </div>
        <div class="doc-content">{{ currentDoc.content }}</div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getKnowledgeList, createKnowledge, updateKnowledge, deleteKnowledge } from '@/api/knowledge'
import type { OpsKnowledge, OpsKnowledgeRequest } from '@/types'
import { formatDate } from '@/utils/format'

const loading = ref(false)
const list = ref<OpsKnowledge[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const keyword = ref('')
const categoryFilter = ref('')

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const detailVisible = ref(false)
const currentDoc = ref<OpsKnowledge | null>(null)

const form = reactive<OpsKnowledgeRequest>({
  title: '',
  category: '故障排查',
  content: '',
  tags: '',
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }],
}

async function loadData() {
  loading.value = true
  try {
    const res = await getKnowledgeList({
      page: page.value,
      size: pageSize.value,
      keyword: keyword.value || undefined,
      category: categoryFilter.value || undefined,
    })
    list.value = res.data.data.list
    total.value = res.data.data.total
  } catch {
    ElMessage.error('加载列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadData()
}

function handleSizeChange() {
  page.value = 1
  loadData()
}

function resetForm() {
  form.title = ''
  form.category = '故障排查'
  form.content = ''
  form.tags = ''
  formRef.value?.clearValidate()
}

function handleAdd() {
  isEdit.value = false
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: OpsKnowledge) {
  isEdit.value = true
  editId.value = row.id
  Object.assign(form, {
    title: row.title,
    category: row.category,
    content: row.content,
    tags: row.tags,
  })
  dialogVisible.value = true
}

function handleView(row: OpsKnowledge) {
  currentDoc.value = row
  detailVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    if (isEdit.value && editId.value) {
      await updateKnowledge(editId.value, form)
      ElMessage.success('编辑成功')
    } else {
      await createKnowledge(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch {
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(row: OpsKnowledge) {
  try {
    await ElMessageBox.confirm(`确定删除文档「${row.title}」吗？`, '提示', {
      type: 'warning',
    })
    await deleteKnowledge(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
  }
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.knowledge-view {
  :deep(.el-table) {
    margin-top: 0;
  }
}

.doc-detail {
  h3 {
    margin: 0 0 12px 0;
    font-size: 18px;
  }

  .doc-meta {
    display: flex;
    gap: 16px;
    align-items: center;
    margin-bottom: 12px;
    font-size: 13px;
    color: #909399;

    .meta-item {
      flex-shrink: 0;
    }
  }

  .doc-tags {
    margin-bottom: 16px;

    .tag-item {
      margin-right: 8px;
    }
  }

  .doc-content {
    white-space: pre-wrap;
    word-break: break-all;
    line-height: 1.8;
    color: #303133;
    padding: 16px;
    background-color: #f5f7fa;
    border-radius: 4px;
    max-height: 400px;
    overflow-y: auto;
  }
}
</style>
