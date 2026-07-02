<template>
  <div class="intel-credentials">
    <el-alert
      type="warning"
      :closable="false"
      title="凭据来源于知识引擎解析，密码为明文展示（私有知识库，仅管理员可访问），请注意信息安全"
      show-icon
      class="top-alert"
    />
    <el-card shadow="never">
      <template #header>
        <div class="header-bar">
          <span class="card-title">凭据总览</span>
          <div class="header-actions">
            <el-input
              v-model="keyword"
              placeholder="按用户名筛选"
              clearable
              style="width: 220px"
              @keyup.enter="handleSearch"
            />
            <el-select
              v-model="credTypeFilter"
              placeholder="凭据类型筛选"
              clearable
              style="width: 160px"
            >
              <el-option
                v-for="t in credTypeOptions"
                :key="t"
                :label="t"
                :value="t"
              />
            </el-select>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-button :icon="Refresh" @click="loadData">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table :data="filteredList" v-loading="loading" stripe>
        <el-table-column prop="credType" label="凭据类型" width="130">
          <template #default="{ row }">
            <el-tag>{{ row.credType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" min-width="140" show-overflow-tooltip />
        <el-table-column prop="password" label="密码" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="password-text">{{ row.password || row.passwordHint || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="hostId" label="主机ID" width="100" />
      <template #empty>
        <el-empty description="暂无数据" />
      </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getCredentialList } from '@/api/intelligence'
import type { IntelCredential } from '@/types'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

const loading = ref(false)
const list = ref<IntelCredential[]>([])
const keyword = ref('')
const appliedKeyword = ref('')
const credTypeFilter = ref('')

const credTypeOptions = computed(() => {
  const set = new Set<string>()
  list.value.forEach(c => { if (c.credType) set.add(c.credType) })
  return Array.from(set)
})

const filteredList = computed(() => {
  const kw = appliedKeyword.value.trim().toLowerCase()
  return list.value.filter(c => {
    if (credTypeFilter.value && c.credType !== credTypeFilter.value) return false
    if (kw) {
      const username = (c.username || '').toLowerCase()
      if (!username.includes(kw)) return false
    }
    return true
  })
})

function handleSearch() {
  appliedKeyword.value = keyword.value
}

async function loadData() {
  loading.value = true
  try {
    const res = await getCredentialList()
    list.value = res.data.data
  } catch {
    ElMessage.error('加载凭据列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.intel-credentials .top-alert {
  margin-bottom: 12px;
}
.intel-credentials .header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.intel-credentials .card-title {
  font-weight: 600;
  color: #303133;
}
.intel-credentials .header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.intel-credentials .password-text {
  font-family: 'Courier New', Courier, monospace;
  color: #606266;
}

@media (max-width: 768px) {
  .intel-credentials .header-bar {
    flex-wrap: wrap;
    gap: 8px;
  }
  .intel-credentials .header-actions {
    flex-wrap: wrap;
  }
  .intel-credentials :deep(.el-table) {
    font-size: 12px;
  }
}
</style>
