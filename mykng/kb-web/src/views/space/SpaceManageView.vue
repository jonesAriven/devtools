<template>
  <div class="space-manage-page">
    <div class="page-header">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>知识空间</el-breadcrumb-item>
      </el-breadcrumb>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        <span>新建空间</span>
      </el-button>
    </div>

    <div v-if="loading" class="loading-wrap">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
    </div>

    <div v-else class="space-grid">
      <div
        v-for="space in spaceList"
        :key="space.id"
        class="space-card"
        :class="{ 'is-active': spaceStore.currentSpace?.id === space.id }"
        @click="handleEnter(space)"
      >
        <div class="space-icon">
          <el-icon :size="28"><FolderOpened /></el-icon>
        </div>
        <div class="space-info">
          <div class="space-name">{{ space.name }}</div>
          <div class="space-desc">{{ space.description || '暂无描述' }}</div>
          <div class="space-meta">
            <el-icon><Clock /></el-icon>
            <span>{{ formatDate(space.createdAt) }}</span>
          </div>
        </div>
        <div class="space-actions" @click.stop>
          <el-dropdown trigger="click" placement="bottom-end">
            <el-button circle size="small">
              <el-icon><MoreFilled /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleEdit(space)">
                  <el-icon><Edit /></el-icon>编辑
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleDelete(space)">
                  <el-icon><Delete /></el-icon>删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <div v-if="spaceList.length === 0" class="empty-wrap">
        <el-empty description="暂无空间，点击右上角新建一个">
          <el-button type="primary" @click="handleCreate">新建空间</el-button>
        </el-empty>
      </div>
    </div>

    <el-dialog v-model="showDialog" :title="isEdit ? '编辑空间' : '新建空间'" width="480px">
      <el-form :model="form" label-width="80px" ref="formRef">
        <el-form-item label="空间名称" prop="name" :rules="[{ required: true, message: '请输入空间名称', trigger: 'blur' }]">
          <el-input v-model="form.name" placeholder="请输入空间名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="空间描述（可选）" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { FolderOpened, Plus, MoreFilled, Edit, Delete, Clock, Loading } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useSpaceStore } from '@/stores/space'
import { getSpaceList, createSpace, updateSpace, deleteSpace } from '@/api/space'
import { formatDate } from '@/utils/format'
import type { Space } from '@/types'

const router = useRouter()
const spaceStore = useSpaceStore()

const loading = ref(false)
const spaceList = ref<Space[]>([])
const showDialog = ref(false)
const isEdit = ref(false)
const editingId = ref(0)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  description: '',
})

onMounted(() => {
  loadSpaces()
})

async function loadSpaces() {
  loading.value = true
  try {
    const res = await getSpaceList()
    spaceList.value = res.data.data
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  isEdit.value = false
  editingId.value = 0
  form.name = ''
  form.description = ''
  showDialog.value = true
}

function handleEdit(space: Space) {
  isEdit.value = true
  editingId.value = space.id
  form.name = space.name
  form.description = space.description
  showDialog.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (isEdit.value) {
        await updateSpace(editingId.value, { name: form.name, description: form.description })
        ElMessage.success('修改成功')
      } else {
        await createSpace({ name: form.name, description: form.description })
        ElMessage.success('创建成功')
      }
      showDialog.value = false
      await spaceStore.fetchSpaceList()
      loadSpaces()
    } finally {
      saving.value = false
    }
  })
}

async function handleDelete(space: Space) {
  await ElMessageBox.confirm(
    `确定要删除空间"${space.name}"吗？空间内的所有内容（文档、文件、目录）将被一并删除，此操作不可恢复。`,
    '警告',
    { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
  )
  try {
    await deleteSpace(space.id)
    ElMessage.success('已删除')
    if (spaceStore.currentSpace?.id === space.id) {
      spaceStore.clearCurrentSpace()
    }
    await spaceStore.fetchSpaceList()
    loadSpaces()
  } catch {
    // 错误已在拦截器处理
  }
}

function handleEnter(space: Space) {
  spaceStore.setCurrentSpace(space)
  router.push(`/space/${space.id}`)
}
</script>

<style scoped lang="scss">
.space-manage-page {
  padding: 20px 24px;
  min-height: 100%;
  background-color: #faf8f5;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.loading-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  color: #c0c4cc;
}

.space-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.space-card {
  position: relative;
  display: flex;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  border: 2px solid transparent;
  cursor: pointer;
  transition: all 0.25s ease;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);

  &:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
    transform: translateY(-2px);
    border-color: #c9a96e;
  }

  &.is-active {
    border-color: #c9a96e;
    background-color: #fdf8ef;
  }
}

.space-icon {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  border-radius: 10px;
  background: linear-gradient(135deg, #f6d365 0%, #fda085 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.space-info {
  flex: 1;
  min-width: 0;
}

.space-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.space-desc {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.4;
  min-height: 36px;
}

.space-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #c0c4cc;

  .el-icon {
    font-size: 12px;
  }
}

.space-actions {
  position: absolute;
  top: 12px;
  right: 12px;
}

.empty-wrap {
  grid-column: 1 / -1;
  padding: 60px 0;
  background: #fff;
  border-radius: 8px;
}
</style>
