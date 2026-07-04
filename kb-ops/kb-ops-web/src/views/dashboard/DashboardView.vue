<template>
  <div class="dashboard-view">
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">主机总数</div>
          <div class="stat-value primary">{{ dashboard.hostCount || 0 }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">服务总数</div>
          <div class="stat-value success">{{ dashboard.serviceCount || 0 }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">端口总数</div>
          <div class="stat-value warning">{{ dashboard.portCount || 0 }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">待处理矛盾</div>
          <div class="stat-value danger">{{ dashboard.conflictCount || 0 }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>资源分布</span>
          </template>
          <div ref="resourceChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>最近部署</span>
          </template>
          <el-table :data="dashboard.recentDeployments || []" size="small">
            <el-table-column prop="serviceName" label="服务名称" />
            <el-table-column prop="version" label="版本" width="100" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="deploymentStatusType(row.status)" size="small">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="deployTime" label="部署时间" width="160">
              <template #default="{ row }">
                {{ formatDate(row.deployTime) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>
            <span>最近操作日志</span>
          </template>
          <el-table :data="dashboard.recentLogs || []" size="small">
            <el-table-column prop="username" label="用户" width="120" />
            <el-table-column prop="action" label="动作" width="100" />
            <el-table-column prop="resourceType" label="资源类型" width="120" />
            <el-table-column prop="detail" label="详情" show-overflow-tooltip />
            <el-table-column prop="ip" label="IP" width="140" />
            <el-table-column prop="createdAt" label="时间" width="160">
              <template #default="{ row }">
                {{ formatDate(row.createdAt) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getDashboard } from '@/api/dashboard'
import type { DashboardVO } from '@/types'
import { formatDate } from '@/utils/format'

const dashboard = ref<DashboardVO>({} as DashboardVO)
const resourceChartRef = ref<HTMLElement>()
let resourceChart: echarts.ECharts | null = null

function deploymentStatusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    SUCCESS: 'success',
    RUNNING: 'warning',
    FAILED: 'danger',
  }
  return map[status] || 'info'
}

async function loadData() {
  try {
    const res = await getDashboard()
    dashboard.value = res.data.data
    await nextTick()
    initChart()
  } catch {
  }
}

function initChart() {
  if (!resourceChartRef.value) return
  if (resourceChart) {
    resourceChart.dispose()
  }
  resourceChart = echarts.init(resourceChartRef.value)
  const option = {
    tooltip: {
      trigger: 'item',
    },
    legend: {
      bottom: 0,
      left: 'center',
    },
    series: [
      {
        name: '资源分布',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2,
        },
        label: {
          show: false,
          position: 'center',
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 20,
            fontWeight: 'bold',
          },
        },
        labelLine: {
          show: false,
        },
        data: [
          { value: dashboard.value.hostCount || 0, name: '主机', itemStyle: { color: '#409eff' } },
          { value: dashboard.value.serviceCount || 0, name: '服务', itemStyle: { color: '#67c23a' } },
          { value: dashboard.value.portCount || 0, name: '端口', itemStyle: { color: '#e6a23c' } },
          { value: dashboard.value.domainCount || 0, name: '域名', itemStyle: { color: '#909399' } },
          { value: dashboard.value.credentialCount || 0, name: '凭据', itemStyle: { color: '#f56c6c' } },
        ],
      },
    ],
  }
  resourceChart.setOption(option)
}

function handleResize() {
  resourceChart?.resize()
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  resourceChart?.dispose()
})
</script>

<style scoped lang="scss">
.dashboard-view {
  .stat-cards {
    margin-bottom: 0;
  }
}

.chart-container {
  height: 300px;
  width: 100%;
}
</style>
