<template>
  <div class="ops-dashboard">
    <el-row :gutter="20" v-loading="loading">
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>主机统计</template>
          <el-statistic title="总数" :value="data?.hostStats.total || 0" />
          <el-tag type="success" class="mt-2">在线: {{ data?.hostStats.online || 0 }}</el-tag>
          <el-tag type="danger" class="ml-2">离线: {{ data?.hostStats.offline || 0 }}</el-tag>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>服务统计</template>
          <el-statistic title="总数" :value="data?.serviceStats.total || 0" />
          <el-tag type="success" class="mt-2">运行中: {{ data?.serviceStats.running || 0 }}</el-tag>
          <el-tag type="info" class="ml-2">已停止: {{ data?.serviceStats.stopped || 0 }}</el-tag>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>部署趋势（近7天）</template>
          <div v-if="data?.deployTrend?.length" class="deploy-trend">
            <div v-for="item in data.deployTrend" :key="item.date" class="trend-bar">
              <span class="date">{{ item.date.slice(5) }}</span>
              <el-progress :percentage="item.count * 10" :show-text="false" :stroke-width="14" />
              <span class="count">{{ item.count }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无部署数据" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="mt-4">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span>最近部署</span>
            <el-button text @click="$router.push('/ops/services')">查看全部</el-button>
          </template>
          <el-table :data="data?.recentDeploys || []" size="small" stripe>
            <el-table-column prop="serviceName" label="服务" width="120" />
            <el-table-column prop="hostName" label="主机" width="100" />
            <el-table-column prop="version" label="版本" width="80" />
            <el-table-column prop="deployer" label="部署人" width="80" />
            <el-table-column prop="deployTime" label="时间" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span>矛盾检测</span>
            <el-button text @click="$router.push('/ops/conflicts')">查看全部</el-button>
          </template>
          <el-table :data="data?.recentConflicts || []" size="small" stripe>
            <el-table-column prop="type" label="类型" width="150" />
            <el-table-column prop="severity" label="级别" width="80">
              <template #default="{ row }">
                <el-tag :type="row.severity === 'HIGH' ? 'danger' : 'warning'">{{ row.severity }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getDashboard } from '@/api/ops'
import type { DashboardVO } from '@/types'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const data = ref<DashboardVO | null>(null)

async function loadData() {
  loading.value = true
  try {
    const res = await getDashboard()
    data.value = res.data.data
  } catch {
    ElMessage.error('加载看板数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.deploy-trend { display: flex; flex-direction: column; gap: 8px; }
.trend-bar { display: flex; align-items: center; gap: 8px; }
.trend-bar .date { width: 40px; font-size: 12px; color: #909399; }
.trend-bar .count { width: 20px; font-size: 12px; text-align: right; }
.mt-2 { margin-top: 8px; }
.mt-4 { margin-top: 16px; }

@media (max-width: 768px) {
  .deploy-trend { gap: 4px; }
  .trend-bar { font-size: 11px; }
  .trend-bar .date { width: 36px; }
}
</style>
