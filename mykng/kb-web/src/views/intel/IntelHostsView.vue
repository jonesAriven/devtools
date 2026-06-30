<template>
  <div class="intel-hosts">
    <el-alert
      type="info"
      :closable="false"
      title="数据来源：知识引擎自动解析，只读视图"
      show-icon
      class="top-alert"
    />
    <el-card shadow="never">
      <template #header>
        <div class="header-bar">
          <span class="card-title">主机总览</span>
          <div class="header-actions">
            <el-input
              v-model="keyword"
              placeholder="按 IP 或名称筛选"
              clearable
              style="width: 220px"
              @keyup.enter="handleSearch"
            />
            <el-select
              v-model="roleFilter"
              placeholder="角色筛选"
              clearable
              style="width: 150px"
            >
              <el-option
                v-for="r in roleOptions"
                :key="r"
                :label="r"
                :value="r"
              />
            </el-select>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-button :icon="Refresh" @click="loadData">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table :data="filteredList" v-loading="loading" stripe>
        <el-table-column prop="name" label="主机名" min-width="140" show-overflow-tooltip />
        <el-table-column prop="ip" label="内网 IP" width="140" />
        <el-table-column prop="tailscaleIp" label="Tailscale IP" width="140" />
        <el-table-column prop="sshPort" label="SSH 端口" width="90" />
        <el-table-column prop="username" label="用户名" width="100" />
        <el-table-column prop="role" label="角色" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="osType" label="操作系统" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getHostList } from '@/api/intelligence'
import type { IntelHost } from '@/types'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

const loading = ref(false)
const list = ref<IntelHost[]>([])
const keyword = ref('')
const appliedKeyword = ref('')
const roleFilter = ref('')

const roleOptions = computed(() => {
  const set = new Set<string>()
  list.value.forEach(h => { if (h.role) set.add(h.role) })
  return Array.from(set)
})

const filteredList = computed(() => {
  const kw = appliedKeyword.value.trim().toLowerCase()
  return list.value.filter(h => {
    if (roleFilter.value && h.role !== roleFilter.value) return false
    if (kw) {
      const ip = (h.ip || '').toLowerCase()
      const name = (h.name || '').toLowerCase()
      if (!ip.includes(kw) && !name.includes(kw)) return false
    }
    return true
  })
})

function statusType(status: string) {
  const s = (status || '').toLowerCase()
  if (s === 'running') return 'success'
  if (s === 'stopped') return 'danger'
  return 'info'
}

function handleSearch() {
  appliedKeyword.value = keyword.value
}

async function loadData() {
  loading.value = true
  try {
    const res = await getHostList()
    list.value = res.data.data
  } catch {
    ElMessage.error('加载主机列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.intel-hosts .top-alert {
  margin-bottom: 12px;
}
.intel-hosts .header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.intel-hosts .card-title {
  font-weight: 600;
  color: #303133;
}
.intel-hosts .header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

@media (max-width: 768px) {
  .intel-hosts .header-bar {
    flex-wrap: wrap;
    gap: 8px;
  }
  .intel-hosts .header-actions {
    flex-wrap: wrap;
  }
  .intel-hosts :deep(.el-table) {
    font-size: 12px;
  }
}
</style>
