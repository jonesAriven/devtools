<template>
  <div class="conflicts-view">
    <el-card shadow="never">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="keyword"
            placeholder="搜索矛盾描述"
            clearable
            style="width: 240px"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-select v-model="levelFilter" placeholder="级别" clearable style="width: 120px" @change="handleSearch">
            <el-option label="高危" value="HIGH" />
            <el-option label="中危" value="MEDIUM" />
            <el-option label="低危" value="LOW" />
          </el-select>
          <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 120px" @change="handleSearch">
            <el-option label="未解决" value="OPEN" />
            <el-option label="已解决" value="RESOLVED" />
          </el-select>
        </div>
        <div class="toolbar-right">
          <el-button @click="loadData">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
          <el-button type="warning" @click="handleScan">
            <el-icon><Scan /></el-icon>
            扫描检测
          </el-button>
        </div>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="conflictType" label="类型" width="140" />
        <el-table-column label="级别" width="100">
          <template #default="{ row }">
            <el-tag :type="levelType(row.level)" size="small">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="resourceA" label="资源A" width="140" show-overflow-tooltip />
        <el-table-column prop="resourceB" label="资源B" width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'RESOLVED' ? 'success' : 'warning'" size="small">
              {{ row.status === 'RESOLVED' ? '已解决' : '未解决' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="发现时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleView(row)">查看</el-button>
            <el-button v-if="row.status !== 'RESOLVED'" link type="success" size="small" @click="handleResolve(row)">解决</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无数据" />
        </template>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadData"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="矛盾详情" width="600px">
      <el-descriptions v-if="currentConflict" :column="1" border>
        <el-descriptions-item label="ID">{{ currentConflict.id }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ currentConflict.conflictType }}</el-descriptions-item>
        <el-descriptions-item label="级别">
          <el-tag :type="levelType(currentConflict.level)" size="small">{{ currentConflict.level }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="描述">{{ currentConflict.description }}</el-descriptions-item>
        <el-descriptions-item label="资源A">{{ currentConflict.resourceA }}</el-descriptions-item>
        <el-descriptions-item label="资源B">{{ currentConflict.resourceB }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="currentConflict.status === 'RESOLVED' ? 'success' : 'warning'" size="small">
            {{ currentConflict.status === 'RESOLVED' ? '已解决' : '未解决' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发现时间">{{ formatDate(currentConflict.createdAt) }}</el-descriptions-item>
        <el-descriptions-item v-if="currentConflict.resolvedAt" label="解决时间">
          {{ formatDate(currentConflict.resolvedAt) }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resolveVisible" title="解决矛盾" width="500px">
      <el-form label-width="100px">
        <el-form-item label="解决说明">
          <el-input v-model="resolveRemark" type="textarea" :rows="4" placeholder="请输入解决说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resolveVisible = false">取消</el-button>
        <el-button type="primary" :loading="resolveLoading" @click="submitResolve">确认解决</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getConflictList, deleteConflict, resolveConflict, scanConflicts } from '@/api/conflict'
import type { OpsConflict } from '@/types'
import { formatDate } from '@/utils/format'

const loading = ref(false)
const list = ref<OpsConflict[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const keyword = ref('')
const levelFilter = ref('')
const statusFilter = ref('')

const detailVisible = ref(false)
const currentConflict = ref<OpsConflict | null>(null)
const resolveVisible = ref(false)
const resolveId = ref<number | null>(null)
const resolveRemark = ref('')
const resolveLoading = ref(false)

function levelType(level: string): 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'info',
  }
  return map[level] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getConflictList({
      page: page.value,
      size: pageSize.value,
      keyword: keyword.value || undefined,
      level: levelFilter.value || undefined,
      status: statusFilter.value || undefined,
    })
    list.value = res.data.data.list
    total.value = res.data.data.total
  } catch {
    ElMessage.error('加载列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadData()
}

function handleSizeChange() {
  page.value = 1
  loadData()
}

function handleView(row: OpsConflict) {
  currentConflict.value = row
  detailVisible.value = true
}

function handleResolve(row: OpsConflict) {
  resolveId.value = row.id
  resolveRemark.value = ''
  resolveVisible.value = true
}

async function submitResolve() {
  if (!resolveId.value) return
  resolveLoading.value = true
  try {
    await resolveConflict(resolveId.value, resolveRemark.value)
    ElMessage.success('已标记为已解决')
    resolveVisible.value = false
    loadData()
  } catch {
  } finally {
    resolveLoading.value = false
  }
}

async function handleScan() {
  try {
    await ElMessageBox.confirm('确定重新扫描所有矛盾吗？扫描可能需要一些时间。', '提示', {
      type: 'warning',
    })
    await scanConflicts()
    ElMessage.success('扫描完成')
    loadData()
  } catch {
  }
}

async function handleDelete(row: OpsConflict) {
  try {
    await ElMessageBox.confirm(`确定删除该矛盾记录吗？`, '提示', {
      type: 'warning',
    })
    await deleteConflict(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
  }
}

onMounted(loadData)
</script>

<style scoped>
.conflicts-view {
  :deep(.el-table) {
    margin-top: 0;
  }
}
</style>
