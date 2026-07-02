<template>
  <div class="ops-knowledge">
    <el-card shadow="never">
      <template #header>
        <div class="header-bar">
          <span>运维知识库</span>
          <el-button type="primary" @click="showDialog()">新增知识</el-button>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="title" label="标题" width="200" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" show-overflow-tooltip />
        <el-table-column prop="tags" label="标签" width="150">
          <template #default="{ row }">
            <el-tag v-for="tag in (row.tags || '').split(',').filter(Boolean)" :key="tag" size="small" class="mr-1">{{ tag }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text @click="showDialog(row)">编辑</el-button>
            <el-button text type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑知识' : '新增知识'" width="600px">
      <el-form :model="form" label-width="60px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="form.category" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="8" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getOpsKnowledgeList, createOpsKnowledge, updateOpsKnowledge, deleteOpsKnowledge } from '@/api/ops'
import type { OpsKnowledge } from '@/types'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirm'

const loading = ref(false)
const list = ref<OpsKnowledge[]>([])
const dialogVisible = ref(false)
const form = reactive<Partial<OpsKnowledge>>({})

async function loadData() {
  loading.value = true
  try {
    const res = await getOpsKnowledgeList({ page: 1, size: 100 })
    list.value = res.data.data.list
  } catch { ElMessage.error('加载知识列表失败') }
  finally { loading.value = false }
}

function showDialog(row?: any) {
  Object.keys(form).forEach(k => delete (form as any)[k])
  if (row) Object.assign(form, row)
  dialogVisible.value = true
}

async function handleSave() {
  try {
    if (form.id) await updateOpsKnowledge(form.id, form)
    else await createOpsKnowledge(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch { ElMessage.error('保存失败') }
}

async function handleDelete(id: number) {
  try {
    await confirmDelete('确认删除该运维知识？')
    await deleteOpsKnowledge(id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
    // 用户取消或错误已在拦截器处理
  }
}

onMounted(loadData)
</script>

<style scoped>
.header-bar { display: flex; justify-content: space-between; align-items: center; }
.mr-1 { margin-right: 4px; }

@media (max-width: 768px) {
  .header-bar { flex-wrap: wrap; gap: 8px; }
  .ops-knowledge :deep(.el-table) { font-size: 12px; }
}
</style>
