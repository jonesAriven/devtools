<template>
  <div class="graph-page">
    <div class="graph-header">
      <div class="header-left">
        <h2 class="page-title">知识图谱</h2>
        <span class="doc-count">共 {{ nodeCount }} 个节点，{{ linkCount }} 条连线</span>
      </div>
      <div class="header-actions">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索节点..."
          clearable
          class="search-input"
          @input="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadGraphData">
          刷新
        </el-button>
        <el-button :icon="ZoomIn" @click="zoomIn">放大</el-button>
        <el-button :icon="ZoomOut" @click="zoomOut">缩小</el-button>
        <el-button :icon="RefreshRight" @click="resetView">重置视图</el-button>
      </div>
    </div>

    <div class="graph-container">
      <div v-loading="loading" class="chart-wrapper" ref="chartRef">
        <div v-if="!loading && nodeCount === 0" class="empty-state">
          <el-icon class="empty-icon"><Connection /></el-icon>
          <div class="empty-text">暂无图谱数据</div>
          <div class="empty-desc">文档内容中使用 [[docId]] 格式可创建双向链接</div>
        </div>
      </div>

      <div v-if="selectedNode" class="node-info-panel">
        <div class="panel-header">
          <span class="panel-title">节点详情</span>
          <el-icon class="close-btn" @click="selectedNode = null"><Close /></el-icon>
        </div>
        <div class="panel-body">
          <div class="info-row">
            <span class="info-label">标题</span>
            <span class="info-value">{{ selectedNode.name }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">链接数</span>
            <span class="info-value">{{ selectedNode.value }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">文档ID</span>
            <span class="info-value">{{ selectedNode.docId }}</span>
          </div>
          <el-button type="primary" class="go-btn" @click="goToDoc(selectedNode.docId)">
            查看文档
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { Search, Refresh, ZoomIn, ZoomOut, RefreshRight, Connection, Close } from '@element-plus/icons-vue'
import { fetchAllDocs, buildGraphFromDocs } from '@/api/graph'
import type { GraphData, GraphNode } from '@/api/graph'

const router = useRouter()
const chartRef = ref<HTMLElement | null>(null)
let chartInstance: echarts.ECharts | null = null

const loading = ref(false)
const searchKeyword = ref('')
const graphData = ref<GraphData>({ nodes: [], links: [], categories: [] })
const selectedNode = ref<GraphNode | null>(null)

const nodeCount = ref(0)
const linkCount = ref(0)

let resizeObserver: ResizeObserver | null = null

onMounted(async () => {
  await initChart()
  await loadGraphData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (resizeObserver) {
    resizeObserver.disconnect()
  }
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})

async function initChart() {
  await nextTick()
  if (chartRef.value) {
    chartInstance = echarts.init(chartRef.value)
    chartInstance.on('click', handleChartClick)

    resizeObserver = new ResizeObserver(() => {
      handleResize()
    })
    resizeObserver.observe(chartRef.value)
  }
}

function handleResize() {
  if (chartInstance) {
    chartInstance.resize()
  }
}

async function loadGraphData() {
  loading.value = true
  selectedNode.value = null
  try {
    const docs = await fetchAllDocs()
    const data = buildGraphFromDocs(docs)
    graphData.value = data
    nodeCount.value = data.nodes.length
    linkCount.value = data.links.length
    renderChart(data)
  } catch (e) {
    console.error('加载图谱数据失败', e)
  } finally {
    loading.value = false
  }
}

function renderChart(data: GraphData) {
  if (!chartInstance) return

  const option: echarts.EChartsOption = {
    tooltip: {
      formatter: (params: any) => {
        if (params.dataType === 'node') {
          return `<div style="font-weight:600;">${params.data.name}</div>
                  <div style="margin-top:4px;">链接数: ${params.data.value}</div>
                  <div>文档ID: ${params.data.docId}</div>
                  <div style="margin-top:4px;color:#909399;font-size:12px;">点击查看详情</div>`
        }
        return ''
      },
    },
    legend: [
      {
        data: data.categories.map((c) => c.name),
        bottom: 10,
      },
    ],
    series: [
      {
        type: 'graph',
        layout: 'force',
        data: data.nodes.map((node) => ({
          ...node,
          itemStyle: {
            color: '#c9a96e',
          },
        })),
        links: data.links.map((link) => ({
          ...link,
          lineStyle: {
            color: '#c9a96e',
            opacity: 0.4,
            width: 1.5,
          },
        })),
        categories: data.categories,
        roam: true,
        draggable: true,
        label: {
          show: true,
          position: 'right',
          formatter: '{b}',
          fontSize: 12,
          color: '#606266',
        },
        force: {
          repulsion: 300,
          gravity: 0.1,
          edgeLength: [100, 200],
          layoutAnimation: true,
        },
        emphasis: {
          focus: 'adjacency',
          lineStyle: {
            width: 3,
            opacity: 0.8,
          },
          itemStyle: {
            shadowBlur: 10,
            shadowColor: 'rgba(201, 169, 110, 0.5)',
          },
        },
        edgeLabel: {
          show: false,
        },
      },
    ],
  }

  chartInstance.setOption(option, true)
}

function handleChartClick(params: any) {
  if (params.dataType === 'node') {
    const node = graphData.value.nodes.find((n) => n.id === params.data.id)
    if (node) {
      selectedNode.value = node
    }
  }
}

function goToDoc(docId: number) {
  router.push(`/doc/${docId}`)
}

function handleSearch() {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) {
    renderChart(graphData.value)
    return
  }

  const filteredNodes = graphData.value.nodes.filter((node) =>
    node.name.toLowerCase().includes(keyword)
  )
  const nodeIds = new Set(filteredNodes.map((n) => n.id))
  const filteredLinks = graphData.value.links.filter(
    (link) => nodeIds.has(link.source) && nodeIds.has(link.target)
  )

  const filteredData: GraphData = {
    nodes: filteredNodes,
    links: filteredLinks,
    categories: graphData.value.categories,
  }

  renderChart(filteredData)
}

function zoomIn() {
  if (chartInstance) {
    const option = chartInstance.getOption() as any
    const currentZoom = option.series?.[0]?.zoom || 1
    chartInstance.setOption({
      series: [
        {
          zoom: currentZoom * 1.2,
        },
      ],
    })
  }
}

function zoomOut() {
  if (chartInstance) {
    const option = chartInstance.getOption() as any
    const currentZoom = option.series?.[0]?.zoom || 1
    chartInstance.setOption({
      series: [
        {
          zoom: currentZoom / 1.2,
        },
      ],
    })
  }
}

function resetView() {
  if (chartInstance) {
    chartInstance.dispatchAction({
      type: 'restore',
    })
  }
}
</script>

<style scoped lang="scss">
.graph-page {
  padding: 16px;
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.graph-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-shrink: 0;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .page-title {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    color: #303133;
  }

  .doc-count {
    font-size: 13px;
    color: #909399;
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .search-input {
    width: 200px;
  }
}

.graph-container {
  flex: 1;
  display: flex;
  gap: 16px;
  min-height: 0;
  position: relative;
}

.chart-wrapper {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.06);
  min-height: 400px;
  position: relative;
}

.empty-state {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  color: #909399;

  .empty-icon {
    font-size: 48px;
    margin-bottom: 12px;
    color: #dcdfe6;
  }

  .empty-text {
    font-size: 16px;
    margin-bottom: 8px;
  }

  .empty-desc {
    font-size: 13px;
    color: #c0c4cc;
  }
}

.node-info-panel {
  width: 280px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  max-height: 100%;

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 16px;
    border-bottom: 1px solid #ebeef5;

    .panel-title {
      font-weight: 600;
      font-size: 15px;
      color: #303133;
    }

    .close-btn {
      cursor: pointer;
      color: #909399;
      font-size: 18px;

      &:hover {
        color: #f56c6c;
      }
    }
  }

  .panel-body {
    padding: 16px;
    flex: 1;
    overflow-y: auto;

    .info-row {
      display: flex;
      margin-bottom: 12px;
      font-size: 13px;

      .info-label {
        color: #909399;
        width: 60px;
        flex-shrink: 0;
      }

      .info-value {
        color: #303133;
        flex: 1;
        word-break: break-all;
      }
    }

    .go-btn {
      width: 100%;
      margin-top: 8px;
    }
  }
}

@media (max-width: 768px) {
  .graph-page {
    padding: 12px;
  }

  .graph-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;

    .header-actions {
      width: 100%;
      flex-wrap: wrap;
    }

    .search-input {
      width: 100%;
    }
  }

  .graph-container {
    flex-direction: column;
  }

  .node-info-panel {
    width: 100%;
  }
}
</style>
