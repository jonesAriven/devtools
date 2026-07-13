<template>
  <div class="dashboard-page">
    <div class="page-header">
      <h2 class="page-title">总览看板</h2>
      <div class="page-actions">
        <el-upload
          ref="uploadRef"
          :show-file-list="false"
          :before-upload="handleBeforeUpload"
          :http-request="handleImport"
          accept=".json,.yaml,.yml"
          style="display: inline-block;"
        >
          <el-button type="primary">
            <el-icon><Upload /></el-icon>
            导入数据
          </el-button>
        </el-upload>
        <el-button type="success" @click="handleExportJson">
          <el-icon><Download /></el-icon>
          导出 JSON
        </el-button>
        <el-button type="warning" @click="handleExportYaml">
          <el-icon><Download /></el-icon>
          导出 YAML
        </el-button>
      </div>
    </div>

    <el-row :gutter="16" class="stat-cards">
      <el-col :xs="12" :sm="8" :md="6" :lg="4">
        <div class="stat-card" @click="goToPage('/hosts')">
          <div class="stat-icon primary">
            <el-icon><Cpu /></el-icon>
          </div>
          <div class="stat-label">主机数</div>
          <div class="stat-value primary">{{ summary.hostCount || 0 }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="6" :lg="4">
        <div class="stat-card" @click="goToPage('/credentials')">
          <div class="stat-icon warning">
            <el-icon><Key /></el-icon>
          </div>
          <div class="stat-label">凭据数</div>
          <div class="stat-value warning">{{ summary.credentialCount || 0 }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="6" :lg="4">
        <div class="stat-card" @click="goToPage('/configs')">
          <div class="stat-icon info">
            <el-icon><Setting /></el-icon>
          </div>
          <div class="stat-label">配置数</div>
          <div class="stat-value">{{ summary.configCount || 0 }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="6" :lg="4">
        <div class="stat-card" @click="goToPage('/services')">
          <div class="stat-icon primary">
            <el-icon><Connection /></el-icon>
          </div>
          <div class="stat-label">服务监控数</div>
          <div class="stat-value primary">{{ summary.serviceCount || 0 }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="6" :lg="4">
        <div class="stat-card" @click="goToPage('/services')">
          <div class="stat-icon success">
            <el-icon><CircleCheck /></el-icon>
          </div>
          <div class="stat-label">在线服务</div>
          <div class="stat-value success">{{ summary.servicesOnline || 0 }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="8" :md="6" :lg="4">
        <div class="stat-card" @click="goToPage('/services')">
          <div class="stat-icon danger">
            <el-icon><CircleClose /></el-icon>
          </div>
          <div class="stat-label">离线服务</div>
          <div class="stat-value danger">{{ summary.servicesOffline || 0 }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="quick-section">
      <el-col :span="24">
        <div class="section-card">
          <div class="section-title">快速入口</div>
          <div class="quick-entries">
            <div class="quick-item" @click="goToPage('/hosts')">
              <el-icon :size="28" color="#409eff"><Cpu /></el-icon>
              <span>主机管理</span>
            </div>
            <div class="quick-item" @click="goToPage('/credentials')">
              <el-icon :size="28" color="#e6a23c"><Key /></el-icon>
              <span>凭据管理</span>
            </div>
            <div class="quick-item" @click="goToPage('/configs')">
              <el-icon :size="28" color="#909399"><Setting /></el-icon>
              <span>配置信息</span>
            </div>
            <div class="quick-item" @click="goToPage('/services')">
              <el-icon :size="28" color="#67c23a"><Connection /></el-icon>
              <span>服务监控</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { reactive, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadInstance, UploadFiles, UploadRequestOptions } from 'element-plus'
import request from '@/utils/request'
import type { DashboardSummary } from '@/types'

const router = useRouter()
const uploadRef = ref<UploadInstance>()
const importing = ref(false)

const summary = reactive<DashboardSummary>({
  hostCount: 0,
  credentialCount: 0,
  configCount: 0,
  serviceCount: 0,
  servicesOnline: 0,
  servicesOffline: 0,
  servicesUnknown: 0,
})

async function fetchSummary() {
  try {
    const data = await request.get('/dashboard/summary')
    if (data) {
      Object.assign(summary, data)
    }
  } catch (e) {
    console.error('获取看板数据失败', e)
  }
}

function goToPage(path: string) {
  router.push(path)
}

function handleBeforeUpload(file: File) {
  const isJsonOrYaml = file.name.endsWith('.json') || file.name.endsWith('.yaml') || file.name.endsWith('.yml')
  if (!isJsonOrYaml) {
    ElMessage.error('只支持 JSON 或 YAML 格式的文件!')
    return false
  }
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    ElMessage.error('文件大小不能超过 10MB!')
    return false
  }
  return true
}

async function handleImport(options: UploadRequestOptions) {
  try {
    await ElMessageBox.confirm('导入数据将覆盖现有数据，确定要继续吗？', '确认导入', {
      type: 'warning',
      confirmButtonText: '确定导入',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  importing.value = true
  try {
    const formData = new FormData()
    formData.append('file', options.file)
    const res = await request.post('/io/import', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
    ElMessage.success('导入成功')
    fetchSummary()
  } catch (e) {
    ElMessage.error('导入失败')
  } finally {
    importing.value = false
  }
}

async function handleExportJson() {
  try {
    const res = await request.get('/io/export/json', {
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const link = document.createElement('a')
    link.href = url
    const dateStr = new Date().toISOString().slice(0, 10)
    link.setAttribute('download', `infra-monitor-export-${dateStr}.json`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

async function handleExportYaml() {
  try {
    const res = await request.get('/io/export/yaml', {
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const link = document.createElement('a')
    link.href = url
    const dateStr = new Date().toISOString().slice(0, 10)
    link.setAttribute('download', `infra-monitor-export-${dateStr}.yaml`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

onMounted(() => {
  fetchSummary()
})
</script>

<style scoped lang="scss">
.dashboard-page {
  padding: 20px;
  min-height: calc(100vh - 82px);

  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .page-title {
      font-size: 18px;
      font-weight: 600;
      color: #303133;
      margin: 0;
    }

    .page-actions {
      display: flex;
      gap: 8px;
    }
  }

  .stat-cards {
    margin-bottom: 16px;
  }

  .quick-section {
    .section-card {
      background: #fff;
      border-radius: 8px;
      padding: 20px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);

      .section-title {
        font-size: 16px;
        font-weight: 600;
        color: #303133;
        margin-bottom: 16px;
      }

      .quick-entries {
        display: flex;
        gap: 24px;
        flex-wrap: wrap;

        .quick-item {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 8px;
          padding: 20px 32px;
          border-radius: 8px;
          background: #f5f7fa;
          cursor: pointer;
          transition: all 0.3s;
          min-width: 120px;

          &:hover {
            background: #ecf5ff;
            transform: translateY(-2px);
          }

          span {
            font-size: 14px;
            color: #606266;
          }
        }
      }
    }
  }
}
</style>
