<template>
  <div class="manage-page">
    <div class="page-header">
      <h2 class="page-title">系统管理</h2>
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新增系统
      </el-button>
    </div>

    <el-card class="table-card">
      <div class="filter-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索系统名称"
          clearable
          :prefix-icon="Search"
          class="search-input"
        />
        <el-select v-model="filterCategory" placeholder="全部分类" clearable class="category-select">
          <el-option
            v-for="(label, key) in categoryLabels"
            :key="key"
            :label="label"
            :value="key"
          />
        </el-select>
      </div>

      <el-table
        :data="pagedSystems"
        stripe
        style="width: 100%"
        v-loading="loading"
      >
        <el-table-column prop="name" label="系统名称" min-width="180">
          <template #default="{ row }">
            <div class="name-cell">
              <div class="name-icon" :style="{ color: row.color, background: colorMix(row.color) }">
                <el-icon :size="20">
                  <component :is="row.icon" />
                </el-icon>
              </div>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            <el-tag :type="categoryTagTypes[row.category as SystemCategory] || 'info'">
              {{ categoryLabels[row.category as SystemCategory] || row.category }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="techStack" label="技术栈" min-width="160" show-overflow-tooltip />
        <el-table-column prop="url" label="访问地址" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link v-if="row.url" :href="row.url" target="_blank" type="primary">
              {{ row.url }}
            </el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="filteredSystems.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="系统名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入系统名称" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="formData.category" placeholder="请选择分类" style="width: 100%">
            <el-option
              v-for="(label, key) in categoryLabels"
              :key="key as string"
              :label="label"
              :value="key as SystemCategory"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入系统描述" />
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <el-input v-model="formData.icon" placeholder="请输入图标名称" />
        </el-form-item>
        <el-form-item label="主题色" prop="color">
          <el-color-picker v-model="formData.color" />
        </el-form-item>
        <el-form-item label="访问地址" prop="url">
          <el-input v-model="formData.url" placeholder="请输入访问地址" />
        </el-form-item>
        <el-form-item label="健康检查" prop="healthCheckUrl">
          <el-input v-model="formData.healthCheckUrl" placeholder="请输入健康检查地址" />
        </el-form-item>
        <el-form-item label="技术栈" prop="techStack">
          <el-input v-model="formData.techStack" placeholder="请输入技术栈" />
        </el-form-item>
        <el-form-item label="下载地址" prop="downloadPath">
          <el-input v-model="formData.downloadPath" placeholder="请输入下载地址" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search, Edit, Delete } from '@element-plus/icons-vue'
import { systems as systemsConfig, categoryLabels, type SystemConfig, type SystemCategory } from '@/config/systems'

const loading = ref(false)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const searchKeyword = ref('')
const filterCategory = ref<SystemCategory | ''>('')
const currentPage = ref(1)
const pageSize = ref(10)
const isEdit = ref(false)
const editId = ref('')

const systems = ref<SystemConfig[]>([...systemsConfig])

const categoryTagTypes: Record<string, string> = {
  web: 'primary',
  infra: 'success',
  tool: 'warning',
  doc: 'info'
}

const filteredSystems = computed(() => {
  return systems.value.filter(sys => {
    const matchKeyword = !searchKeyword.value ||
      sys.name.toLowerCase().includes(searchKeyword.value.toLowerCase()) ||
      sys.description.toLowerCase().includes(searchKeyword.value.toLowerCase())
    const matchCategory = !filterCategory.value || sys.category === filterCategory.value
    return matchKeyword && matchCategory
  })
})

const pagedSystems = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredSystems.value.slice(start, start + pageSize.value)
})

const dialogTitle = computed(() => isEdit.value ? '编辑系统' : '新增系统')

const formData = reactive<Partial<SystemConfig>>({
  id: '',
  name: '',
  category: 'web',
  description: '',
  icon: 'Monitor',
  color: '#409eff',
  url: '',
  healthCheckUrl: '',
  techStack: '',
  downloadPath: '',
  docs: []
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入系统名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  description: [{ required: true, message: '请输入系统描述', trigger: 'blur' }],
  icon: [{ required: true, message: '请输入图标名称', trigger: 'blur' }],
  color: [{ required: true, message: '请选择主题色', trigger: 'change' }]
}

function colorMix(color: string): string {
  return `color-mix(in srgb, ${color} 12%, transparent)`
}

function handleAdd() {
  isEdit.value = false
  editId.value = ''
  Object.assign(formData, {
    id: '',
    name: '',
    category: 'web',
    description: '',
    icon: 'Monitor',
    color: '#409eff',
    url: '',
    healthCheckUrl: '',
    techStack: '',
    downloadPath: '',
    docs: []
  })
  dialogVisible.value = true
}

function handleEdit(row: SystemConfig) {
  isEdit.value = true
  editId.value = row.id
  Object.assign(formData, { ...row })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (isEdit.value) {
        const index = systems.value.findIndex(s => s.id === editId.value)
        if (index > -1) {
          systems.value[index] = { ...systems.value[index], ...formData } as SystemConfig
        }
        ElMessage.success('更新成功')
      } else {
        const newSystem = {
          ...formData,
          id: Date.now().toString()
        } as SystemConfig
        systems.value.unshift(newSystem)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
    } finally {
      submitLoading.value = false
    }
  })
}

function handleDelete(row: SystemConfig) {
  ElMessageBox.confirm(`确定要删除系统「${row.name}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    const index = systems.value.findIndex(s => s.id === row.id)
    if (index > -1) {
      systems.value.splice(index, 1)
    }
    ElMessage.success('删除成功')
  }).catch(() => {})
}

onMounted(() => {
})
</script>

<style scoped lang="scss">
.manage-page {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    .page-title {
      font-size: 22px;
      font-weight: 600;
      color: #303133;
      margin: 0;
    }
  }

  .table-card {
    border-radius: 12px;
  }

  .filter-bar {
    display: flex;
    gap: 16px;
    margin-bottom: 20px;

    .search-input {
      flex: 1;
      max-width: 300px;
    }

    .category-select {
      width: 160px;
    }
  }

  .name-cell {
    display: flex;
    align-items: center;
    gap: 10px;

    .name-icon {
      width: 36px;
      height: 36px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
  }

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
  }
}
</style>
