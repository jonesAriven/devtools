<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Trash2, RefreshCw } from 'lucide-vue-next'
import { hostApi } from '@/api/ops'
import type { Host } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const list = ref<Host[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ name: '', ip: '', sshPort: 22 })

const rules: FormRules = {
  name: [{ required: true, message: '请输入主机名称', trigger: 'blur' }],
  ip: [
    { required: true, message: '请输入IP地址', trigger: 'blur' },
    { pattern: /^(\d{1,3}\.){3}\d{1,3}$/, message: 'IP格式不正确', trigger: 'blur' },
  ],
  sshPort: [{ required: true, message: '请输入SSH端口', trigger: 'blur' }],
}

async function loadList() {
  loading.value = true
  try {
    const res = await hostApi.list({ page: page.value, size: size.value })
    list.value = res?.list || []
    total.value = res?.total || 0
  } finally {
    loading.value = false
  }
}

function openDialog() {
  form.name = ''
  form.ip = ''
  form.sshPort = 22
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    await hostApi.create({ name: form.name, ip: form.ip, sshPort: form.sshPort })
    ElMessage.success('添加成功')
    dialogVisible.value = false
    loadList()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(host: Host) {
  await ElMessageBox.confirm(`确定删除主机「${host.name}」吗？`, '删除确认', { type: 'warning' })
  await hostApi.delete(host.id)
  ElMessage.success('删除成功')
  loadList()
}

function onPageChange(p: number) {
  page.value = p
  loadList()
}

function onSizeChange(s: number) {
  size.value = s
  page.value = 1
  loadList()
}

function statusMeta(status: number) {
  if (status === 1) return { text: '在线', type: 'success' as const }
  if (status === 2) return { text: '告警', type: 'danger' as const }
  return { text: '离线', type: 'info' as const }
}

function usageColor(usage?: number) {
  if (usage === undefined) return ''
  if (usage >= 90) return '#e74c3c'
  if (usage >= 70) return '#f39c12'
  return '#27ae60'
}

onMounted(loadList)
</script>

<template>
  <div class="host-page" v-loading="loading">
    <div class="page-header">
      <div>
        <h2 class="title">主机管理</h2>
        <p class="desc">管理运维主机资源及运行状态</p>
      </div>
      <div class="actions">
        <el-button :icon="RefreshCw" @click="loadList">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openDialog">添加主机</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="list" stripe style="width: 100%">
        <el-table-column label="主机名" prop="name" min-width="140" show-overflow-tooltip />
        <el-table-column label="IP地址" prop="ip" min-width="140" />
        <el-table-column label="SSH端口" prop="sshPort" width="100" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type" size="small">{{ statusMeta(row.status).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="CPU" width="140">
          <template #default="{ row }">
            <el-progress v-if="row.cpuUsage !== undefined && row.cpuUsage !== null" :percentage="row.cpuUsage" :color="usageColor(row.cpuUsage)" :stroke-width="10" />
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="内存" width="140">
          <template #default="{ row }">
            <el-progress v-if="row.memUsage !== undefined && row.memUsage !== null" :percentage="row.memUsage" :color="usageColor(row.memUsage)" :stroke-width="10" />
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="磁盘" width="140">
          <template #default="{ row }">
            <el-progress v-if="row.diskUsage !== undefined && row.diskUsage !== null" :percentage="row.diskUsage" :color="usageColor(row.diskUsage)" :stroke-width="10" />
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" :icon="Trash2" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无主机" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="page"
          :page-size="size"
          :page-sizes="[10, 20, 50]"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>

    <!-- 添加主机对话框 -->
    <el-dialog v-model="dialogVisible" title="添加主机" width="440px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="主机名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入主机名称" />
        </el-form-item>
        <el-form-item label="IP地址" prop="ip">
          <el-input v-model="form.ip" placeholder="如 192.168.1.10" />
        </el-form-item>
        <el-form-item label="SSH端口" prop="sshPort">
          <el-input-number v-model="form.sshPort" :min="1" :max="65535" controls-position="right" style="width: 100%" />
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
.host-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;

  .title { font-size: 20px; font-weight: 600; color: #2c3e50; }
  .desc { margin-top: 4px; font-size: 13px; color: #7f8c8d; }
}

.actions { display: flex; gap: 8px; }

.table-card { border-radius: 8px; }

.muted { color: #bbb; }

.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
