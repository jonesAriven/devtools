<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, FolderOpen, Pencil, Trash2, FileText } from 'lucide-vue-next'
import { spaceApi } from '@/api/knowledge'
import type { Space } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const spaces = ref<Space[]>([])
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)

const form = reactive({
  name: '',
  type: 'personal',
  description: '',
})

const typeOptions = [
  { label: '个人', value: 'personal' },
  { label: '团队', value: 'team' },
]

async function loadSpaces() {
  loading.value = true
  try {
    spaces.value = (await spaceApi.list()) as Space[]
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.type = 'personal'
  form.description = ''
  dialogVisible.value = true
}

function openEdit(space: Space) {
  editingId.value = space.id
  form.name = space.name
  form.type = space.type
  form.description = space.description
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入空间名称')
    return
  }
  submitting.value = true
  try {
    if (editingId.value) {
      await spaceApi.update(editingId.value, { name: form.name, description: form.description })
      ElMessage.success('更新成功')
    } else {
      await spaceApi.create({ name: form.name, type: form.type, description: form.description })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadSpaces()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(space: Space) {
  await ElMessageBox.confirm(`确认删除空间「${space.name}」？该操作不可恢复`, '删除确认', {
    type: 'warning',
  })
  await spaceApi.delete(space.id)
  ElMessage.success('删除成功')
  await loadSpaces()
}

function enterSpace(space: Space) {
  router.push(`/space/${space.id}`)
}

const typeLabel = (t: string) => typeOptions.find((o) => o.value === t)?.label || t

onMounted(loadSpaces)
</script>

<template>
  <div class="space-list" v-loading="loading">
    <div class="header">
      <h2 class="title">知识空间</h2>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建空间</el-button>
    </div>

    <el-empty v-if="!loading && spaces.length === 0" description="创建你的第一个知识空间">
      <el-button type="primary" :icon="Plus" @click="openCreate">新建空间</el-button>
    </el-empty>

    <el-row v-else :gutter="16">
      <el-col v-for="space in spaces" :key="space.id" :xs="24" :sm="12" :md="8" :lg="6">
        <div class="card">
          <div class="card-head">
            <span class="card-name">{{ space.name }}</span>
            <el-tag size="small" type="info">{{ typeLabel(space.type) }}</el-tag>
          </div>
          <p class="card-desc">{{ space.description || '暂无描述' }}</p>
          <div class="card-meta">
            <span class="meta-item">
              <FileText :size="14" /> {{ space.docCount || 0 }} 篇文档
            </span>
            <span class="meta-time">{{ formatDateTime(space.createdAt) }}</span>
          </div>
          <div class="card-actions">
            <el-button size="small" type="primary" :icon="FolderOpen" @click="enterSpace(space)">进入</el-button>
            <el-button size="small" :icon="Pencil" @click="openEdit(space)">编辑</el-button>
            <el-button size="small" type="danger" :icon="Trash2" @click="handleDelete(space)" />
          </div>
        </div>
      </el-col>
    </el-row>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑空间' : '新建空间'" width="480px">
      <el-form :model="form" label-width="72px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="请输入空间名称" maxlength="50" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="类型">
          <el-radio-group v-model="form.type">
            <el-radio v-for="o in typeOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.space-list {
  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
  }
  .title {
    font-size: 22px;
    font-weight: 600;
    color: #2c3e50;
    margin: 0;
  }
}

.card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
  transition: box-shadow 0.2s, transform 0.2s;

  &:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
    transform: translateY(-2px);
  }
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.card-name {
  font-size: 17px;
  font-weight: 600;
  color: #2c3e50;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  color: #7f8c8d;
  font-size: 13px;
  min-height: 38px;
  margin: 0 0 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #95a5a6;
  font-size: 12px;
  margin-bottom: 12px;

  .meta-item {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
}

.card-actions {
  display: flex;
  gap: 6px;
}
</style>
