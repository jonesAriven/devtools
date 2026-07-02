<template>
  <div class="ops-dashboard">
    <div class="dashboard-header">
      <h2 class="dashboard-title">运维看板</h2>
      <div class="header-actions">
        <el-button type="primary" :icon="Refresh" @click="loadData">刷新</el-button>
        <el-popconfirm
          title="确认从知识引擎同步数据？"
          confirm-button-text="同步"
          cancel-button-text="取消"
          @confirm="handleSync(false)"
        >
          <template #reference>
            <el-button type="success" :icon="Download" :loading="syncing">
              {{ syncing ? '同步中...' : '从知识引擎同步' }}
            </el-button>
          </template>
        </el-popconfirm>
        <el-dropdown @command="handleSyncCommand" v-if="!syncing">
          <el-button :icon="MoreFilled">更多</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="override">强制覆盖同步</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

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
          <template #empty>
            <el-empty description="暂无数据" />
          </template>
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
          <template #empty>
            <el-empty description="暂无数据" />
          </template>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="syncResultVisible" title="同步结果" width="600px">
      <div v-if="syncResult" class="sync-result">
        <el-alert v-if="syncResult.error" :title="'同步出错: ' + syncResult.error" type="error" show-icon class="mb-3" />
        <p class="sync-duration">
          总耗时: <b>{{ syncResult.durationMs }}ms</b>
        </p>
        <el-table :data="syncResultRows" size="small" border>
          <el-table-column prop="label" label="实体类型" width="120" />
          <el-table-column prop="total" label="总数" width="70" align="right" />
          <el-table-column prop="created" label="新增" width="70" align="right">
            <template #default="{ row }">
              <span class="text-success">{{ row.created }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="updated" label="更新" width="70" align="right">
            <template #default="{ row }">
              <span class="text-warning">{{ row.updated }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="skipped" label="跳过" width="70" align="right" />
          <el-table-column prop="failed" label="失败" width="70" align="right">
            <template #default="{ row }">
              <span class="text-danger">{{ row.failed }}</span>
            </template>
          </el-table-column>
        <template #empty>
          <el-empty description="暂无数据" />
        </template>
        </el-table>
      </div>
      <template #footer>
        <el-button type="primary" @click="syncResultVisible = false">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getDashboard, syncFromIntelligence, type SyncFromIntelResult } from '@/api/ops'
import type { DashboardVO } from '@/types'
import { ElMessage } from 'element-plus'
import { Refresh, Download, MoreFilled } from '@element-plus/icons-vue'

const loading = ref(false)
const syncing = ref(false)
const data = ref<DashboardVO | null>(null)
const syncResult = ref<SyncFromIntelResult | null>(null)
const syncResultVisible = ref(false)

const syncResultRows = computed(() => {
  if (!syncResult.value) return []
  const r = syncResult.value
  return [
    { label: '主机', ...r.host },
    { label: '服务', ...r.service },
    { label: '端口', ...r.port },
    { label: '凭据', ...r.credential },
    { label: '域名', ...r.domain },
    { label: '依赖', ...r.dependency },
  ]
})

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

async function handleSync(override: boolean) {
  syncing.value = true
  try {
    const res = await syncFromIntelligence({ override })
    syncResult.value = res.data.data
    syncResultVisible.value = true
    ElMessage.success('同步完成')
    loadData()
  } catch (e: any) {
    ElMessage.error('同步失败: ' + (e?.message || e))
  } finally {
    syncing.value = false
  }
}

function handleSyncCommand(cmd: string) {
  if (cmd === 'override') {
    handleSync(true)
  }
}

onMounted(loadData)
</script>

<style scoped>
.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.dashboard-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.header-actions {
  display: flex;
  gap: 8px;
}
.deploy-trend { display: flex; flex-direction: column; gap: 8px; }
.trend-bar { display: flex; align-items: center; gap: 8px; }
.trend-bar .date { width: 40px; font-size: 12px; color: #909399; }
.trend-bar .count { width: 20px; font-size: 12px; text-align: right; }
.mt-2 { margin-top: 8px; }
.mt-3 { margin-top: 12px; }
.mt-4 { margin-top: 16px; }
.mb-3 { margin-bottom: 12px; }
.ml-2 { margin-left: 8px; }
.text-success { color: #67c23a; }
.text-warning { color: #e6a23c; }
.text-danger { color: #f56c6c; }
.sync-duration {
  margin: 0 0 12px 0;
  color: #606266;
  font-size: 14px;
}

@media (max-width: 768px) {
  .deploy-trend { gap: 4px; }
  .trend-bar { font-size: 11px; }
  .trend-bar .date { width: 36px; }
  .dashboard-header { flex-direction: column; align-items: flex-start; gap: 12px; }
}
</style>
