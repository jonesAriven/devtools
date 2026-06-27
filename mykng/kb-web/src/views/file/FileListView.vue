<template>
  <div class="file-list-page">
    <el-card shadow="never">
      <template #header>
        <div class="header-bar">
          <span>文件管理</span>
          <div class="header-actions">
            <el-select
              v-model="currentSpaceId"
              placeholder="选择空间"
              style="width: 160px"
              @change="loadData"
            >
              <el-option
                v-for="s in spaceStore.spaceList"
                :key="s.id"
                :label="s.name"
                :value="s.id"
              />
            </el-select>
            <el-input
              v-model="keyword"
              placeholder="搜索文件名"
              clearable
              :prefix-icon="Search"
              style="width: 200px"
            />
            <el-radio-group v-model="viewMode" size="small">
              <el-radio-button value="list">列表</el-radio-button>
              <el-radio-button value="grid">网格</el-radio-button>
            </el-radio-group>
            <el-upload
              :show-file-list="false"
              :http-request="handleUpload"
              :disabled="!currentSpaceId"
              multiple
            >
              <el-button type="primary" :icon="Upload" :disabled="!currentSpaceId">上传文件</el-button>
            </el-upload>
          </div>
        </div>
      </template>

      <el-progress
        v-if="uploading && uploadProgress > 0"
        :percentage="uploadProgress"
        :stroke-width="6"
        style="margin-bottom: 12px"
      />

      <!-- 列表视图 -->
      <el-table v-if="viewMode === 'list'" :data="filteredList" v-loading="loading" stripe>
        <el-table-column label="文件名" min-width="220">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="goDetail(row.id)">
              <el-icon class="file-icon"><Document /></el-icon>
              <span class="file-name-text">{{ row.name }}</span>
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="110">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="fileType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.fileType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="星标" width="80" align="center">
          <template #default="{ row }">
            <el-icon class="star-icon" :class="{ active: row.starred }" @click="toggleStar(row)">
              <StarFilled v-if="row.starred" />
              <Star v-else />
            </el-icon>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button text :icon="Download" @click="handleDownload(row)">下载</el-button>
            <el-button text type="danger" :icon="Delete" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 网格视图 -->
      <div v-else v-loading="loading" class="grid-view">
        <div
          v-for="row in filteredList"
          :key="row.id"
          class="grid-item"
          @click="goDetail(row.id)"
        >
          <el-icon class="grid-file-icon"><Document /></el-icon>
          <div class="grid-name" :title="row.name">{{ row.name }}</div>
          <div class="grid-meta">{{ formatFileSize(row.fileSize) }}</div>
          <el-icon
            class="grid-star"
            :class="{ active: row.starred }"
            @click.stop="toggleStar(row)"
          >
            <StarFilled v-if="row.starred" />
            <Star v-else />
          </el-icon>
        </div>
        <el-empty v-if="!loading && filteredList.length === 0" description="暂无文件" />
      </div>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Upload, Download, Delete, Document, Star, StarFilled } from '@element-plus/icons-vue'
import { getFileList, uploadFile, deleteFile, downloadFile, toggleFileStar } from '@/api/file'
import type { KbFile } from '@/types'
import type { UploadRequestOptions } from 'element-plus'
import { formatDate, formatFileSize } from '@/utils/format'
import { useSpaceStore } from '@/stores/space'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const spaceStore = useSpaceStore()

const loading = ref(false)
const list = ref<KbFile[]>([])
const keyword = ref('')
const viewMode = ref<'list' | 'grid'>('list')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const currentSpaceId = ref<number | undefined>(undefined)
const uploading = ref(false)
const uploadProgress = ref(0)

const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter((f) => f.name.toLowerCase().includes(kw))
})

async function loadData() {
  loading.value = true
  try {
    const res = await getFileList({ page: page.value, pageSize: pageSize.value, folderId: 0 })
    list.value = res.data.data.list
    total.value = res.data.data.total
  } catch {
    ElMessage.error('加载文件列表失败')
  } finally {
    loading.value = false
  }
}

async function handleUpload(options: UploadRequestOptions) {
  if (!currentSpaceId.value) {
    ElMessage.warning('请先选择空间')
    return
  }
  const file = options.file
  const formData = new FormData()
  formData.append('file', file)
  formData.append('folderId', '0')
  formData.append('spaceId', String(currentSpaceId.value))

  uploading.value = true
  uploadProgress.value = 0
  try {
    await uploadFile(formData, (progress) => {
      uploadProgress.value = progress
    })
    ElMessage.success(`${file.name} 上传成功`)
    loadData()
  } catch {
    ElMessage.error(`${file.name} 上传失败`)
  } finally {
    uploading.value = false
    uploadProgress.value = 0
  }
}

function goDetail(id: number) {
  router.push(`/file/${id}`)
}

async function toggleStar(row: any) {
  try {
    await toggleFileStar(row.id)
    row.starred = !row.starred
    ElMessage.success(row.starred ? '已标星' : '已取消标星')
  } catch {
    ElMessage.error('操作失败')
  }
}

async function handleDownload(row: any) {
  try {
    await downloadFile(row.id, row.name)
  } catch {
    ElMessage.error('下载失败')
  }
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该文件？删除后可在回收站找回。', '提示', { type: 'warning' })
    await deleteFile(id)
    ElMessage.success('删除成功')
    loadData()
  } catch {
    // 用户取消
  }
}

onMounted(async () => {
  if (!spaceStore.spaceList.length) {
    await spaceStore.fetchSpaceList()
  }
  currentSpaceId.value = spaceStore.spaceList[0]?.id
  loadData()
})
</script>

<style scoped>
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.file-icon {
  margin-right: 4px;
  color: #409eff;
}

.file-name-text {
  vertical-align: middle;
}

.star-icon {
  cursor: pointer;
  font-size: 16px;
  color: #c0c4cc;
  transition: color 0.2s;
}

.star-icon.active {
  color: #f7ba2a;
}

.star-icon:hover {
  color: #f7ba2a;
}

.grid-view {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
  min-height: 200px;
}

.grid-item {
  position: relative;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  cursor: pointer;
  text-align: center;
  transition: box-shadow 0.2s, border-color 0.2s;
}

.grid-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.15);
}

.grid-file-icon {
  font-size: 40px;
  color: #409eff;
}

.grid-name {
  margin-top: 8px;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.grid-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.grid-star {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 14px;
  color: #c0c4cc;
  cursor: pointer;
}

.grid-star.active {
  color: #f7ba2a;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .header-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions .el-select,
  .header-actions .el-input,
  .header-actions .el-radio-group {
    width: 100% !important;
  }

  .file-list-page :deep(.el-table) {
    font-size: 12px;
  }

  .grid-view {
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  }
}
</style>
