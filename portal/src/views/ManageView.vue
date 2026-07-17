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
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="filterCategory" placeholder="全部分类" clearable class="filter-select" @change="handleFilterChange">
          <el-option
            v-for="(label, key) in categoryLabels"
            :key="key"
            :label="label"
            :value="key"
          />
        </el-select>
        <el-select v-model="filterStatus" placeholder="全部状态" clearable class="filter-select" @change="handleFilterChange">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-select v-model="filterHasCredentials" placeholder="账密配置" clearable class="filter-select" @change="handleFilterChange">
          <el-option label="已配置账密" :value="true" />
          <el-option label="未配置账密" :value="false" />
        </el-select>
        <el-select v-model="filterHasUrl" placeholder="访问地址" clearable class="filter-select" @change="handleFilterChange">
          <el-option label="有访问地址" :value="true" />
          <el-option label="无访问地址" :value="false" />
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
            <el-button type="primary" link size="small" @click="handleEdit(row as SystemConfig)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row as SystemConfig)">
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
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
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
        <el-divider content-position="left">登录账密</el-divider>
        <el-form-item label="登录账号" prop="loginUsername">
          <el-input v-model="formData.loginUsername" placeholder="请输入登录账号" />
        </el-form-item>
        <el-form-item label="登录密码" prop="loginPassword">
          <el-input v-model="formData.loginPassword" placeholder="请输入登录密码，留空则不修改" show-password />
        </el-form-item>
        <div class="form-tip">
          <el-icon class="tip-icon"><InfoFilled /></el-icon>
          <span>密码将加密存储，仅登录用户可查看</span>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search, Edit, Delete, InfoFilled } from '@element-plus/icons-vue'
import { categoryLabels, type SystemConfig, type SystemCategory } from '@/config/systems'
import { getSystemList, createSystem, updateSystem, deleteSystem } from '@/api/system'

const loading = ref(false)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const searchKeyword = ref('')
const filterCategory = ref<SystemCategory | ''>('')
const filterStatus = ref<number | ''>('')
const filterHasCredentials = ref<boolean | ''>('')
const filterHasUrl = ref<boolean | ''>('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const isEdit = ref(false)
const editId = ref('')

const systems = ref<SystemConfig[]>([])

const categoryTagTypes: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
  web: 'primary',
  infra: 'success',
  tool: 'warning',
  doc: 'info'
}

const pagedSystems = computed(() => systems.value)

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
  loginUsername: '',
  loginPassword: '',
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

async function fetchList() {
  loading.value = true
  try {
    const data = await getSystemList({
      page: currentPage.value,
      pageSize: pageSize.value,
      keyword: searchKeyword.value || undefined,
      category: filterCategory.value || undefined,
      status: filterStatus.value !== '' ? filterStatus.value : undefined,
      hasCredentials: filterHasCredentials.value !== '' ? filterHasCredentials.value : undefined,
      hasUrl: filterHasUrl.value !== '' ? filterHasUrl.value : undefined,
    })
    systems.value = data.list
    total.value = data.total
  } catch (e: any) {
    ElMessage.error(e.message || '获取系统列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  fetchList()
}

function handleFilterChange() {
  currentPage.value = 1
  fetchList()
}

function handlePageChange(page: number) {
  currentPage.value = page
  fetchList()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  fetchList()
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
    loginUsername: '',
    loginPassword: '',
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
        await updateSystem(editId.value, formData)
        ElMessage.success('更新成功')
      } else {
        await createSystem(formData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      fetchList()
    } catch (e: any) {
      ElMessage.error(e.message || '操作失败')
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
  }).then(async () => {
    try {
      await deleteSystem(row.id)
      ElMessage.success('删除成功')
      fetchList()
    } catch (e: any) {
      ElMessage.error(e.message || '删除失败')
    }
  }).catch(() => {})
}

onMounted(() => {
  fetchList()
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
    gap: 12px;
    margin-bottom: 20px;
    flex-wrap: wrap;

    .search-input {
      flex: 1;
      min-width: 200px;
      max-width: 300px;
    }

    .filter-select {
      width: 140px;
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

  .form-tip {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    color: #909399;
    margin: -10px 0 10px 100px;

    .tip-icon {
      color: #909399;
    }
  }
}
</style>
