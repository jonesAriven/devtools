<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { Monitor, Server, Activity, AlertCircle } from 'lucide-vue-next'
import { opsDashboardApi } from '@/api/ops'
import type { OpsDashboardData } from '@/types/api'

use([CanvasRenderer, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent])

const loading = ref(false)
const data = ref<OpsDashboardData | null>(null)

const cards = computed(() => {
  const d = data.value
  return [
    { label: '主机总数', value: d?.hostStats.total ?? 0, icon: Monitor, color: '#1a2332' },
    { label: '运行中主机', value: d?.hostStats.running ?? 0, icon: Activity, color: '#27ae60' },
    { label: '服务总数', value: d?.serviceStats.total ?? 0, icon: Server, color: '#d4a574' },
    { label: '异常服务', value: d?.serviceStats.abnormal ?? 0, icon: AlertCircle, color: '#e74c3c' },
  ]
})

const lineOption = computed(() => {
  const trend = data.value?.deployTrend || []
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 24, top: 30, bottom: 30 },
    xAxis: {
      type: 'category',
      data: trend.map((t) => t.date.slice(5)),
      axisLine: { lineStyle: { color: '#e8e4e0' } },
      axisLabel: { color: '#7f8c8d' },
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#f0eeeb' } },
      axisLabel: { color: '#7f8c8d' },
    },
    series: [
      {
        name: '部署次数',
        type: 'line',
        smooth: true,
        data: trend.map((t) => t.count),
        symbolSize: 8,
        itemStyle: { color: '#d4a574' },
        lineStyle: { width: 3, color: '#d4a574' },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(212,165,116,0.35)' },
              { offset: 1, color: 'rgba(212,165,116,0.02)' },
            ],
          },
        },
      },
    ],
  }
})

const pieOption = computed(() => {
  const dist = data.value?.serviceTypeDistribution || {}
  const colors = ['#1a2332', '#27ae60', '#d4a574', '#f39c12', '#e74c3c', '#3498db']
  const pieData = Object.entries(dist).map(([name, value], i) => ({
    value,
    name,
    itemStyle: { color: colors[i % colors.length] },
  }))
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, icon: 'circle', textStyle: { color: '#7f8c8d' } },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        data: pieData.length > 0 ? pieData : [{ value: 1, name: '暂无数据', itemStyle: { color: '#e0e0e0' } }],
      },
    ],
  }
})

async function loadSummary() {
  loading.value = true
  try {
    data.value = await opsDashboardApi.summary()
  } finally {
    loading.value = false
  }
}

onMounted(loadSummary)
</script>

<template>
  <div class="dashboard-page" v-loading="loading">
    <!-- 统计卡片 -->
    <div class="stat-grid">
      <div v-for="card in cards" :key="card.label" class="stat-card">
        <div class="stat-icon" :style="{ background: card.color }">
          <component :is="card.icon" :size="22" color="#fff" />
        </div>
        <div class="stat-body">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </div>
      </div>
    </div>

    <!-- 图表区 -->
    <div class="chart-grid">
      <el-card shadow="never" class="chart-card">
        <template #header><span class="chart-title">最近 7 天部署趋势</span></template>
        <VChart class="chart" :option="lineOption" autoresize />
      </el-card>
      <el-card shadow="never" class="chart-card">
        <template #header><span class="chart-title">服务类型分布</span></template>
        <VChart class="chart" :option="pieOption" autoresize />
      </el-card>
    </div>
  </div>
</template>

<style scoped lang="scss">
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(26, 35, 50, 0.04);
  transition: box-shadow 0.3s, transform 0.3s;

  &:hover {
    box-shadow: 0 4px 20px rgba(26, 35, 50, 0.1);
    transform: translateY(-2px);
  }
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-body {
  .stat-value {
    font-size: 26px;
    font-weight: 700;
    color: #2c3e50;
    line-height: 1.2;
  }
  .stat-label {
    margin-top: 4px;
    font-size: 13px;
    color: #7f8c8d;
  }
}

.chart-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
}

.chart-card {
  border-radius: 8px;
}

.chart-title {
  font-weight: 600;
  color: #2c3e50;
}

.chart {
  height: 320px;
}

@media (max-width: 1100px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
  .chart-grid { grid-template-columns: 1fr; }
}
</style>
