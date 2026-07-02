<template>
  <div class="tag-manage">
    <el-card shadow="never">
      <template #header>
        <div class="header-bar">
          <span>标签管理</span>
          <div class="header-actions">
            <el-input
              v-model="keyword"
              placeholder="搜索标签名称"
              clearable
              :prefix-icon="Search"
              style="width: 200px"
              @input="filterList"
            />
            <el-button type="primary" @click="showDialog()">新建标签</el-button>
          </div>
        </div>
      </template>
      <el-table :data="filteredList" v-loading="loading" stripe>
        <el-table-column prop="name" label="名称" min-width="160">
          <template #default="{ row }">
            <el-tag :color="row.color" effect="dark" size="small" class="color-dot">{{ row.name }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="颜色" width="160">
          <template #default="{ row }">
            <div class="color-cell">
              <el-color-picker v-model="row.color" disabled size="small" />
              <span class="color-text">{{ row.color || '默认' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text @click="showDialog(row)">编辑</el-button>
            <el-button text type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      <template #empty>
        <el-empty description="暂无数据" />
      </template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑标签' : '新建标签'" width="420px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="请输入标签名称" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="颜色">
          <el-color-picker v-model="form.color" show-alpha />
          <span class="color-hint">{{ form.color || '未选择则使用默认色' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { getTagList, createTag, updateTag, deleteTag } from '@/api/tag'
import type { Tag } from '@/types'
import { formatDate } from '@/utils/format'
import { confirmDelete } from '@/utils/confirm'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const list = ref<Tag[]>([])
const keyword = ref('')
const dialogVisible = ref(false)
const form = reactive<{ id?: number; name: string; color?: string }>({
  name: '',
  color: '',
})

const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter((t) => t.name.toLowerCase().includes(kw))
})

function filterList() {
  // 输入即过滤，由 computed 自动响应
}

async function loadData() {
  loading.value = true
  try {
    const res = await getTagList()
    list.value = res.data.data
  } catch {
    ElMessage.error('加载标签列表失败')
  } finally {
    loading.value = false
  }
}

function showDialog(row?: any) {
  form.id = row?.id
  form.name = row?.name ?? ''
  form.color = row?.color ?? ''
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入标签名称')
    return
  }
  try {
    const payload = { name: form.name.trim(), color: form.color || undefined }
    if (form.id) {
      await updateTag(form.id, payload)
    } else {
      await createTag(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch {
    ElMessage.error('保存失败')
  }
}

async function handleDelete(id: number) {
  try {
    await confirmDelete('确认删除该标签？删除后资源上的该标签关联也将失效。')
    await deleteTag(id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
    // 用户取消
  }
}

onMounted(loadData)
</script>

<style scoped>
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.color-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-text {
  font-size: 12px;
  color: #909399;
}

.color-dot {
  border: none;
}

.color-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}

@media (max-width: 768px) {
  .header-bar {
    flex-wrap: wrap;
    gap: 8px;
  }

  .header-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .header-actions .el-input {
    width: 100% !important;
  }

  .tag-manage :deep(.el-table) {
    font-size: 12px;
  }
}
</style>
