<template>
  <div class="intelligence-dashboard" v-loading="loading">
    <!-- 顶部统计卡 -->
    <el-row :gutter="16">
      <el-col :xs="12" :sm="6" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-title">文档总数</div>
          <div class="stat-value">{{ stats?.docCount ?? 0 }}</div>
          <div class="stat-sub">篇龙虾记忆</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-title">主机数</div>
          <div class="stat-value">{{ stats?.hostCount ?? 0 }}</div>
          <div class="stat-sub">台设备指纹</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-title">命令数</div>
          <div class="stat-value">{{ stats?.commandCount ?? 0 }}</div>
          <div class="stat-sub">条运维命令</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-title">时间线</div>
          <div class="stat-value">{{ stats?.timelineCount ?? 0 }}</div>
          <div class="stat-sub">条事件</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 文档类型分布 + 服务状态 -->
    <el-row :gutter="16" class="mt-4">
      <el-col :xs="24" :md="14">
        <el-card shadow="hover">
          <template #header>
            <span>文档类型分布</span>
          </template>
          <div v-if="stats?.byType?.length" class="type-distribution">
            <div v-for="item in stats.byType" :key="item.docType" class="type-bar">
              <span class="type-name">{{ docTypeLabel(item.docType) }}</span>
              <el-progress
                :percentage="percentage(item.count, stats?.docCount || 1)"
                :stroke-width="14"
                :format="() => `${item.count}`"
              />
            </div>
          </div>
          <el-empty v-else description="暂无数据" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="10">
        <el-card shadow="hover">
          <template #header>
            <span>其他实体</span>
          </template>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="服务数">{{ stats?.serviceCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="端口数">{{ stats?.portCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="凭据数">{{ stats?.credentialCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="域名数">{{ stats?.domainCount ?? 0 }}</el-descriptions-item>
          </el-descriptions>
          <div class="mt-3">
            <el-tag size="small" type="info">服务状态：{{ importStatus?.status || '未知' }}</el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近文档 -->
    <el-card shadow="hover" class="mt-4">
      <template #header>
        <div class="card-header">
          <span>最近导入的文档</span>
          <el-button text type="primary" @click="$router.push('/intelligence/docs')">查看全部</el-button>
        </div>
      </template>
      <el-table :data="recentDocs" size="small" stripe @row-click="goDetail">
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="docType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="docTypeTag(row.docType)">{{ docTypeLabel(row.docType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120" show-overflow-tooltip />
        <el-table-column prop="wordCount" label="字数" width="80" />
        <el-table-column prop="createdAt" label="导入时间" width="160" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getStats, getImportStatus, getDocList } from '@/api/intelligence'
import type { KnStats, KnImportStatus, KnDoc, KnDocType } from '@/types/intelligence'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const stats = ref<KnStats | null>(null)
const importStatus = ref<KnImportStatus | null>(null)
const recentDocs = ref<KnDoc[]>([])

const DOC_TYPE_LABELS: Record<KnDocType, string> = {
  TABLE: '表格',
  PLAN: '方案',
  TIMELINE: '时间线',
  GRAPH: '图谱',
  RULE: '规则',
  GENERAL: '通用',
}

const DOC_TYPE_TAGS: Record<KnDocType, '' | 'success' | 'warning' | 'info' | 'danger'> = {
  TABLE: 'info',
  PLAN: 'success',
  TIMELINE: 'warning',
  GRAPH: 'danger',
  RULE: '',
  GENERAL: 'info',
}

function docTypeLabel(t: KnDocType) {
  return DOC_TYPE_LABELS[t] || t
}

function docTypeTag(t: KnDocType) {
  return DOC_TYPE_TAGS[t] || ''
}

function percentage(count: number, total: number) {
  return total === 0 ? 0 : Math.round((count / total) * 100)
}

function goDetail(row: KnDoc) {
  router.push(`/intelligence/docs/${row.id}`)
}

async function loadData() {
  loading.value = true
  try {
    const [statsRes, statusRes, docsRes] = await Promise.all([
      getStats(),
      getImportStatus(),
      getDocList({ page: 1, size: 10 }),
    ])
    stats.value = statsRes.data.data
    importStatus.value = statusRes.data.data
    recentDocs.value = docsRes.data.data.records || []
  } catch (e) {
    console.error(e)
    ElMessage.error('加载知识引擎数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.intelligence-dashboard {
  padding: 16px;
}

.stat-card {
  text-align: center;
  .stat-title {
    font-size: 13px;
    color: #909399;
    margin-bottom: 6px;
  }
  .stat-value {
    font-size: 28px;
    font-weight: 700;
    color: #c9a96e;
    line-height: 1.2;
  }
  .stat-sub {
    font-size: 12px;
    color: #c0c4cc;
    margin-top: 4px;
  }
}

.type-distribution {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.type-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  .type-name {
    width: 60px;
    font-size: 13px;
    color: #606266;
    flex-shrink: 0;
  }
  .el-progress {
    flex: 1;
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mt-3 { margin-top: 12px; }
.mt-4 { margin-top: 16px; }
</style>
