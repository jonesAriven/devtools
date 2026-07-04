<template>
  <div class="dashboard-view">
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">主机总数</div>
          <div class="stat-value primary">{{ totalHosts }}</div>
          <div class="stat-sub">
            <span class="success">运行 {{ hostStats.running || 0 }}</span>
            <span class="danger">停止 {{ hostStats.stopped || 0 }}</span>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">服务总数</div>
          <div class="stat-value success">{{ totalServices }}</div>
          <div class="stat-sub">
            <span class="success">运行 {{ serviceStats.running || 0 }}</span>
            <span class="danger">异常 {{ serviceStats.abnormal || 0 }}</span>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">最近部署</div>
          <div class="stat-value warning">{{ dashboard.recentDeployCount || 0 }}</div>
          <div class="stat-sub">近 7 天</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">待处理矛盾</div>
          <div class="stat-value danger">{{ dashboard.unresolvedConflictCount || 0 }}</div>
          <div class="stat-sub">需关注</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>服务类型分布</span>
          </template>
          <div ref="typeChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>最近部署</span>
          </template>
          <el-table :data="dashboard.recentDeploys || []" size="small">
            <el-table-column prop="serviceName" label="服务名称" />
            <el-table-column prop="version" label="版本" width="100" />
            <el-table-column label="结果" width="80">
              <template #default="{ row }">
                <el-tag :type="row.result === 1 ? 'success' : 'danger'" size="small">
                  {{ row.result === 1 ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="deployTime" label="部署时间" width="160" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>部署趋势（近7天）</span>
          </template>
          <div ref="trendChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>最近矛盾</span>
          </template>
          <el-table :data="dashboard.recentConflicts || []" size="small">
            <el-table-column prop="ruleName" label="规则" width="120" />
            <el-table-column prop="targetName" label="目标" width="120" />
            <el-table-column label="级别" width="80">
              <template #default="{ row }">
                <el-tag :type="severityType(row.severity)" size="small">
                  {{ severityText(row.severity) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="detail" label="详情" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getDashboard } from '@/api/dashboard'
import type { DashboardVO } from '@/types'

const dashboard = ref<DashboardVO>({} as DashboardVO)
const typeChartRef = ref<HTMLElement>()
const trendChartRef = ref<HTMLElement>()
let typeChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null

const hostStats = computed(() => dashboard.value.hostStats || {})
const serviceStats = computed(() => dashboard.value.serviceStats || {})

const totalHosts = computed(() => {
  const s = hostStats.value
  return (s.running || 0) + (s.stopped || 0) + (s.maintenance || 0)
})

const totalServices = computed(() => {
  const s = serviceStats.value
  return (s.running || 0) + (s.stopped || 0) + (s.abnormal || 0)
})

function severityType(severity: number): 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<number, 'success' | 'warning' | 'danger' | 'info'> = {
    1: 'info',
    2: 'warning',
    3: 'danger',
  }
  return map[severity] || 'info'
}

function severityText(severity: number): string {
  const map: Record<number, string> = { 1: '低', 2: '中', 3: '高' }
  return map[severity] || '未知'
}

async function loadData() {
  try {
    const res = await getDashboard()
    dashboard.value = res.data.data
    await nextTick()
    initCharts()
  } catch {
  }
}

function initCharts() {
  initTypeChart()
  initTrendChart()
}

function initTypeChart() {
  if (!typeChartRef.value) return
  if (typeChart) {
    typeChart.dispose()
  }
  typeChart = echarts.init(typeChartRef.value)
  const dist = dashboard.value.serviceTypeDistribution || {}
  const data = Object.entries(dist).map(([name, value]) => ({ name, value }))
  const option = {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, left: 'center' },
    series: [
      {
        name: '服务类型',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2,
        },
        label: { show: false, position: 'center' },
        emphasis: {
          label: { show: true, fontSize: 18, fontWeight: 'bold' },
        },
        labelLine: { show: false },
        data,
      },
    ],
  }
  typeChart.setOption(option)
}

function initTrendChart() {
  if (!trendChartRef.value) return
  if (trendChart) {
    trendChart.dispose()
  }
  trendChart = echarts.init(trendChartRef.value)
  const trend = dashboard.value.deployTrend || []
  const dates = trend.map((item) => item.date || '')
  const counts = trend.map((item) => item.count || 0)
  const option = {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
    },
    yAxis: { type: 'value' },
    series: [
      {
        name: '部署次数',
        type: 'line',
        smooth: true,
        areaStyle: {},
        data: counts,
        itemStyle: { color: '#409eff' },
      },
    ],
  }
  trendChart.setOption(option)
}

function handleResize() {
  typeChart?.resize()
  trendChart?.resize()
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  typeChart?.dispose()
  trendChart?.dispose()
})
</script>

<style scoped lang="scss">
.dashboard-view {
  .stat-cards {
    margin-bottom: 0;
  }

  .stat-card {
    padding: 20px;
    background: #fff;
    border-radius: 8px;

    .stat-label {
      font-size: 14px;
      color: #909399;
      margin-bottom: 8px;
    }

    .stat-value {
      font-size: 28px;
      font-weight: 600;
      margin-bottom: 8px;

      &.primary { color: #409eff; }
      &.success { color: #67c23a; }
      &.warning { color: #e6a23c; }
      &.danger { color: #f56c6c; }
    }

    .stat-sub {
      font-size: 12px;
      color: #909399;
      display: flex;
      gap: 12px;

      .success { color: #67c23a; }
      .danger { color: #f56c6c; }
      .warning { color: #e6a23c; }
    }
  }
}

.chart-container {
  height: 300px;
  width: 100%;
}
</style>
