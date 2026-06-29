<template>
  <div class="intelligence-entities">
    <!-- 顶部搜索 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="关键词搜索">
          <el-input
            v-model="searchKeyword"
            placeholder="跨文档搜索"
            clearable
            style="width: 280px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">搜索</el-button>
          <el-button @click="onResetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 搜索结果 -->
    <el-card v-if="searchHits.length" shadow="hover" class="mt-3">
      <template #header>
        <span>搜索结果（{{ searchHits.length }} 条命中）</span>
      </template>
      <el-table :data="searchHits" size="small" stripe @row-click="goDocDetail">
        <el-table-column prop="docTitle" label="文档标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="docType" label="类型" width="80">
          <template #default="{ row }">{{ row.docType }}</template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="100" show-overflow-tooltip />
        <el-table-column prop="score" label="分数" width="80" />
        <el-table-column prop="highlight" label="命中片段" show-overflow-tooltip />
      </el-table>
    </el-card>

    <!-- 实体 Tabs -->
    <el-card shadow="hover" class="mt-3">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- 主机 -->
        <el-tab-pane label="主机" name="hosts">
          <el-table :data="hosts" size="small" stripe v-loading="loading.hosts">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="ip" label="IP" width="130" show-overflow-tooltip>
              <template #default="{ row }">
                <span :class="{ 'invalid-ip': !isValidIp(row.ip) }">{{ row.ip || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="tailscaleIp" label="Tailscale IP" width="130" show-overflow-tooltip />
            <el-table-column prop="sshPort" label="SSH端口" width="80" />
            <el-table-column prop="username" label="用户" width="100" show-overflow-tooltip />
            <el-table-column prop="role" label="角色" min-width="180" show-overflow-tooltip />
            <el-table-column prop="osType" label="OS" width="80" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'running' ? 'success' : 'info'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 服务 -->
        <el-tab-pane label="服务" name="services">
          <el-table :data="services" size="small" stripe v-loading="loading.services">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="hostName" label="主机" width="120" show-overflow-tooltip />
            <el-table-column prop="port" label="端口" width="80" />
            <el-table-column prop="type" label="类型" width="100" />
            <el-table-column prop="status" label="状态" width="80" />
          </el-table>
          <el-empty v-if="!loading.services && !services.length" description="暂无服务数据（serviceCount=0 是正常的）" />
        </el-tab-pane>

        <!-- 命令 -->
        <el-tab-pane label="命令" name="commands">
          <el-table :data="commands" size="small" stripe v-loading="loading.commands">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="command" label="命令" min-width="280" show-overflow-tooltip />
            <el-table-column prop="category" label="分类" width="100" />
            <el-table-column prop="riskLevel" label="风险" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="riskTagType(row.riskLevel)">{{ row.riskLevel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="osType" label="OS" width="80" />
            <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>

        <!-- 时间线 -->
        <el-tab-pane label="时间线" name="timelines">
          <el-timeline v-if="timelines.length" v-loading="loading.timelines">
            <el-timeline-item
              v-for="tl in timelines"
              :key="tl.id"
              :timestamp="tl.eventTime"
              :type="timelineTagType(tl.eventType)"
              placement="top"
            >
              <el-card shadow="hover" class="timeline-item-card">
                <div class="timeline-title">
                  <span class="title-text">{{ tl.title }}</span>
                  <el-tag size="small" :type="severityTagType(tl.severity)">{{ tl.severity }}</el-tag>
                  <el-tag size="small" type="info">{{ tl.eventType }}</el-tag>
                </div>
                <div v-if="tl.description" class="timeline-desc">{{ tl.description }}</div>
                <div v-if="tl.solution" class="timeline-solution">
                  <span class="solution-label">解决方案：</span>{{ tl.solution }}
                </div>
              </el-card>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无时间线数据" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  getHostList, getServiceList, getCommandList, getTimelineList, searchDocs,
} from '@/api/intelligence'
import type { KnHost, KnService, KnCommand, KnTimeline, KnSearchHit } from '@/types/intelligence'
import { ElMessage } from 'element-plus'

const router = useRouter()
const activeTab = ref<'hosts' | 'services' | 'commands' | 'timelines'>('hosts')

const hosts = ref<KnHost[]>([])
const services = ref<KnService[]>([])
const commands = ref<KnCommand[]>([])
const timelines = ref<KnTimeline[]>([])
const searchHits = ref<KnSearchHit[]>([])
const searchKeyword = ref('')

const loading = ref({
  hosts: false,
  services: false,
  commands: false,
  timelines: false,
})

const loaded = ref({
  hosts: false,
  services: false,
  commands: false,
  timelines: false,
})

function isValidIp(ip: string | null): boolean {
  if (!ip) return false
  // 简单校验：必须是合法 IPv4 或主机名（不含 ~~ 或中文括号）
  if (/^~~/.test(ip) || /[（）]/.test(ip)) return false
  return /^[\d.]+$/.test(ip) || /^[a-zA-Z0-9.\-]+$/.test(ip)
}

function riskTagType(risk: string): '' | 'success' | 'warning' | 'info' | 'danger' {
  const m: Record<string, '' | 'success' | 'warning' | 'info' | 'danger'> = {
    low: 'info', medium: 'warning', high: 'danger',
  }
  return m[risk] || ''
}

function severityTagType(sev: string): '' | 'success' | 'warning' | 'info' | 'danger' {
  const m: Record<string, '' | 'success' | 'warning' | 'info' | 'danger'> = {
    low: 'info', medium: 'warning', high: 'danger', critical: 'danger',
  }
  return m[sev] || ''
}

function timelineTagType(t: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const m: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
    issue: 'danger', fix: 'success', deploy: 'primary', note: 'info', change: 'warning',
  }
  return m[t] || 'info'
}

async function loadHosts() {
  if (loaded.value.hosts) return
  loading.value.hosts = true
  try {
    const res = await getHostList()
    hosts.value = res.data.data || []
    loaded.value.hosts = true
  } catch (e) {
    console.error(e)
    ElMessage.error('加载主机列表失败')
  } finally {
    loading.value.hosts = false
  }
}

async function loadServices() {
  if (loaded.value.services) return
  loading.value.services = true
  try {
    const res = await getServiceList()
    services.value = res.data.data || []
    loaded.value.services = true
  } catch (e) {
    console.error(e)
    ElMessage.error('加载服务列表失败')
  } finally {
    loading.value.services = false
  }
}

async function loadCommands() {
  if (loaded.value.commands) return
  loading.value.commands = true
  try {
    const res = await getCommandList()
    commands.value = res.data.data || []
    loaded.value.commands = true
  } catch (e) {
    console.error(e)
    ElMessage.error('加载命令列表失败')
  } finally {
    loading.value.commands = false
  }
}

async function loadTimelines() {
  if (loaded.value.timelines) return
  loading.value.timelines = true
  try {
    const res = await getTimelineList()
    timelines.value = res.data.data || []
    loaded.value.timelines = true
  } catch (e) {
    console.error(e)
    ElMessage.error('加载时间线失败')
  } finally {
    loading.value.timelines = false
  }
}

function onTabChange(name: string) {
  if (name === 'hosts') loadHosts()
  else if (name === 'services') loadServices()
  else if (name === 'commands') loadCommands()
  else if (name === 'timelines') loadTimelines()
}

async function onSearch() {
  if (!searchKeyword.value.trim()) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  try {
    const res = await searchDocs({ keyword: searchKeyword.value, limit: 50 })
    searchHits.value = res.data.data || []
    if (!searchHits.value.length) ElMessage.info('未找到匹配文档')
  } catch (e) {
    console.error(e)
    ElMessage.error('搜索失败')
  }
}

function onResetSearch() {
  searchKeyword.value = ''
  searchHits.value = []
}

function goDocDetail(row: KnSearchHit) {
  router.push(`/intelligence/docs/${row.docId}`)
}

onMounted(loadHosts)
</script>

<style scoped lang="scss">
.intelligence-entities { padding: 16px; }
.filter-card {
  :deep(.el-card__body) { padding: 12px 16px 0; }
}
.mt-3 { margin-top: 12px; }
.invalid-ip { color: #f56c6c; }
.timeline-item-card {
  :deep(.el-card__body) { padding: 10px 14px; }
}
.timeline-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  .title-text { font-weight: 600; color: #303133; }
}
.timeline-desc {
  margin-top: 6px;
  font-size: 13px;
  color: #606266;
  white-space: pre-wrap;
}
.timeline-solution {
  margin-top: 6px;
  font-size: 13px;
  color: #67c23a;
  .solution-label { font-weight: 600; }
}
</style>
