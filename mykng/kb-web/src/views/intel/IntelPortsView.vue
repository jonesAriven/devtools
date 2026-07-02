<template>
  <div class="intel-ports">
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
          <span class="card-title">端口总览</span>
          <div class="header-actions">
            <el-input
              v-model="keyword"
              placeholder="按端口号筛选"
              clearable
              style="width: 180px"
              @keyup.enter="handleSearch"
            />
            <el-select
              v-model="protocolFilter"
              placeholder="协议筛选"
              clearable
              style="width: 130px"
            >
              <el-option
                v-for="p in protocolOptions"
                :key="p"
                :label="p"
                :value="p"
              />
            </el-select>
            <el-select
              v-model="exposedFilter"
              placeholder="是否暴露"
              clearable
              style="width: 130px"
            >
              <el-option label="已暴露" :value="1" />
              <el-option label="未暴露" :value="0" />
            </el-select>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-button :icon="Refresh" @click="loadData">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table :data="filteredList" v-loading="loading" stripe>
        <el-table-column prop="port" label="端口号" width="100" />
        <el-table-column prop="protocol" label="协议" width="100">
          <template #default="{ row }">
            <el-tag>{{ row.protocol }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="exposed" label="是否暴露" width="110">
          <template #default="{ row }">
            <el-tag :type="row.exposed === 1 ? 'danger' : 'success'">
              {{ row.exposed === 1 ? '已暴露' : '未暴露' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hostId" label="主机ID" width="100" />
        <el-table-column prop="serviceId" label="服务ID" width="100" />
        <el-table-column prop="accessUrl" label="访问URL" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <a
              v-if="row.accessUrl"
              :href="row.accessUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="access-link"
            >
              {{ row.accessUrl }}
            </a>
            <span v-else>-</span>
          </template>
        </el-table-column>
      <template #empty>
        <el-empty description="暂无数据" />
      </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getPortList } from '@/api/intelligence'
import type { IntelPort } from '@/types'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

const loading = ref(false)
const list = ref<IntelPort[]>([])
const keyword = ref('')
const appliedKeyword = ref('')
const protocolFilter = ref('')
const exposedFilter = ref<number | ''>('')

const protocolOptions = computed(() => {
  const set = new Set<string>()
  list.value.forEach(p => { if (p.protocol) set.add(p.protocol) })
  return Array.from(set)
})

const filteredList = computed(() => {
  const kw = appliedKeyword.value.trim()
  return list.value.filter(p => {
    if (protocolFilter.value && p.protocol !== protocolFilter.value) return false
    if (exposedFilter.value !== '' && p.exposed !== exposedFilter.value) return false
    if (kw) {
      const portStr = String(p.port || '')
      if (!portStr.includes(kw)) return false
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
    const res = await getPortList()
    list.value = res.data.data
  } catch {
    ElMessage.error('加载端口列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.intel-ports .top-alert {
  margin-bottom: 12px;
}
.intel-ports .header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.intel-ports .card-title {
  font-weight: 600;
  color: #303133;
}
.intel-ports .header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.intel-ports .access-link {
  color: #409eff;
  text-decoration: none;
}
.intel-ports .access-link:hover {
  text-decoration: underline;
}

@media (max-width: 768px) {
  .intel-ports .header-bar {
    flex-wrap: wrap;
    gap: 8px;
  }
  .intel-ports .header-actions {
    flex-wrap: wrap;
  }
  .intel-ports :deep(.el-table) {
    font-size: 12px;
  }
}
</style>
