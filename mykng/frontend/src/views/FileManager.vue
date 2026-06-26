<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Database, FileText, RefreshCw } from 'lucide-vue-next'
import { bucketApi, fileApi } from '@/api/file'
import type { Bucket, KbFile } from '@/types/api'
import { formatDateTime, formatSize } from '@/utils/format'

const bucketLoading = ref(false)
const fileLoading = ref(false)
const buckets = ref<Bucket[]>([])
const activeBucketId = ref<number | undefined>(undefined)
const files = ref<KbFile[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const activeBucket = computed(() => buckets.value.find((b) => b.id === activeBucketId.value))

const usedPercent = computed(() => {
  if (!activeBucket.value) return 0
  const { used, capacity } = activeBucket.value
  if (!capacity) return 0
  return Math.min(100, Math.round((used / capacity) * 100))
})

async function loadBuckets() {
  bucketLoading.value = true
  try {
    buckets.value = (await bucketApi.list()) || []
    if (buckets.value.length && activeBucketId.value === undefined) {
      activeBucketId.value = buckets.value[0].id
      await loadFiles()
    }
  } finally {
    bucketLoading.value = false
  }
}

async function loadFiles() {
  if (activeBucketId.value === undefined) return
  fileLoading.value = true
  try {
    const res = await fileApi.list({ bucketId: activeBucketId.value, page: page.value, size: size.value })
    files.value = res?.list || []
    total.value = res?.total || 0
  } finally {
    fileLoading.value = false
  }
}

async function selectBucket(id: number) {
  activeBucketId.value = id
  page.value = 1
  await loadFiles()
}

function onPageChange(p: number) {
  page.value = p
  loadFiles()
}

function onSizeChange(s: number) {
  size.value = s
  page.value = 1
  loadFiles()
}

function fileTypeLabel(type: string) {
  const map: Record<string, string> = {
    image: '图片', video: '视频', audio: '音频', doc: '文档', archive: '压缩包', other: '其他',
  }
  return map[type] || type
}

function statusMeta(status: number) {
  if (status === 1) return { text: '正常', type: 'success' as const }
  if (status === 0) return { text: '处理中', type: 'warning' as const }
  return { text: '异常', type: 'danger' as const }
}

function bucketStatusType(status: number) {
  if (status === 1) return 'success' as const
  if (status === 0) return 'info' as const
  return 'danger' as const
}

onMounted(loadBuckets)
</script>

<template>
  <div class="file-page">
    <div class="page-header">
      <div>
        <h2 class="title">文件管理</h2>
        <p class="desc">管理存储桶及桶内文件</p>
      </div>
      <el-button :icon="RefreshCw" @click="loadBuckets">刷新</el-button>
    </div>

    <div class="file-layout">
      <!-- 左侧：存储桶 -->
      <el-card shadow="never" class="bucket-panel" v-loading="bucketLoading">
        <template #header><span class="panel-title"><Database :size="16" /> 存储桶</span></template>
        <el-menu :default-active="String(activeBucketId || '')" class="bucket-menu" @select="(idx: string) => selectBucket(Number(idx))">
          <el-menu-item v-for="b in buckets" :key="b.id" :index="String(b.id)">
            <div class="bucket-item">
              <div class="bucket-head">
                <span class="bucket-name">{{ b.name }}</span>
                <el-tag size="small" :type="bucketStatusType(b.status)">{{ b.type }}</el-tag>
              </div>
              <div class="bucket-cap">
                <el-progress :percentage="b.capacity ? Math.round((b.used / b.capacity) * 100) : 0" :stroke-width="6" />
                <span class="cap-text">{{ formatSize(b.used) }} / {{ formatSize(b.capacity) }}</span>
              </div>
            </div>
          </el-menu-item>
          <el-empty v-if="!buckets.length" :image-size="60" description="暂无存储桶" />
        </el-menu>
      </el-card>

      <!-- 右侧：文件列表 -->
      <el-card shadow="never" class="file-panel" v-loading="fileLoading">
        <template #header>
          <div class="panel-head">
            <span class="panel-title"><FileText :size="16" /> 文件列表</span>
            <span v-if="activeBucket" class="cap-summary">
              已用 {{ formatSize(activeBucket.used) }} / {{ formatSize(activeBucket.capacity) }}（{{ usedPercent }}%）
            </span>
          </div>
        </template>
        <el-table :data="files" stripe style="width: 100%">
          <el-table-column label="文件名" prop="name" min-width="220" show-overflow-tooltip />
          <el-table-column label="大小" width="100">
            <template #default="{ row }">{{ formatSize(row.size) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="100" align="center">
            <template #default="{ row }">{{ fileTypeLabel(row.type) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusMeta(row.status).type" size="small">{{ statusMeta(row.status).text }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="上传时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <template #empty>
            <el-empty description="该桶暂无文件" />
          </template>
        </el-table>
        <div class="pager">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="total"
            :current-page="page"
            :page-size="size"
            :page-sizes="[20, 50, 100]"
            @current-change="onPageChange"
            @size-change="onSizeChange"
          />
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped lang="scss">
.file-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;

  .title { font-size: 20px; font-weight: 600; color: #2c3e50; }
  .desc { margin-top: 4px; font-size: 13px; color: #7f8c8d; }
}

.file-layout {
  display: flex;
  gap: 16px;
  align-items: stretch;
}

.bucket-panel {
  width: 280px;
  flex-shrink: 0;
  border-radius: 8px;
}

.file-panel {
  flex: 1;
  border-radius: 8px;
  min-width: 0;
}

.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #2c3e50;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .cap-summary { font-size: 12px; color: #7f8c8d; }
}

.bucket-menu {
  border-right: none;
}

.bucket-item {
  width: 100%;

  .bucket-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .bucket-name {
    font-weight: 600;
    color: #2c3e50;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .bucket-cap {
    margin-top: 8px;

    .cap-text {
      display: block;
      margin-top: 4px;
      font-size: 12px;
      color: #7f8c8d;
    }
  }
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
