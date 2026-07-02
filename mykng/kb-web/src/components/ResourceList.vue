<template>
  <div class="resource-list">
    <div class="list-header">
      <div class="breadcrumb-section">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item @click="goToFolder(null)">
            <el-icon><FolderOpened /></el-icon>
            <span>{{ spaceName || '根目录' }}</span>
          </el-breadcrumb-item>
          <el-breadcrumb-item
            v-for="item in breadcrumbs"
            :key="item.id"
            @click="goToFolder(item.id)"
          >
            {{ item.name }}
          </el-breadcrumb-item>
        </el-breadcrumb>
      </div>
      <div class="header-actions">
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="list">
            <el-icon><List /></el-icon>
          </el-radio-button>
          <el-radio-button value="grid">
            <el-icon><Grid /></el-icon>
          </el-radio-button>
        </el-radio-group>
        <el-select v-model="sortBy" size="small" class="sort-select" @change="loadResources">
          <el-option label="按名称" value="name" />
          <el-option label="按时间" value="time" />
          <el-option label="按类型" value="type" />
        </el-select>
      </div>
    </div>

    <div v-if="selectedItems.length > 0" class="batch-bar">
      <div class="batch-info">
        已选择 <span class="count">{{ selectedItems.length }}</span> 项
        <el-button link size="small" @click="clearSelection">取消选择</el-button>
      </div>
      <div class="batch-actions">
        <el-button size="small" :disabled="selectedItems.length === 0" @click="handleBatchMove">
          <el-icon><Folder /></el-icon>移动到
        </el-button>
        <el-button size="small" type="danger" :disabled="selectedItems.length === 0" @click="handleBatchDelete">
          <el-icon><Delete /></el-icon>删除
        </el-button>
      </div>
    </div>

    <div v-if="loading" class="loading-state">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <div v-else-if="resources.length === 0" class="empty-state">
      <el-icon class="empty-icon"><FolderOpened /></el-icon>
      <div class="empty-text">此目录下暂无资源</div>
      <div class="empty-tip">右键左侧资源树可新建目录、笔记、文件、网页</div>
    </div>

    <div v-else-if="viewMode === 'list'" class="list-view">
      <div class="list-table-header">
        <el-checkbox v-model="selectAll" :indeterminate="isIndeterminate" @change="handleSelectAll">
        </el-checkbox>
        <span class="header-name">名称</span>
        <span class="header-type">类型</span>
        <span class="header-time">修改时间</span>
        <span class="header-actions">操作</span>
      </div>
      <div
        v-for="item in resources"
        :key="`${item.type}-${item.id}`"
        class="resource-item"
        :class="{ selected: isSelected(item) }"
        @click="handleItemClick($event, item)"
      >
        <el-checkbox
          :model-value="isSelected(item)"
          @click.stop
          @change="(val: boolean) => toggleSelect(item, val)"
        />
        <el-icon class="resource-icon">
          <Document v-if="item.type === 'doc'" />
          <Picture v-else-if="item.type === 'file'" />
          <Link v-else />
        </el-icon>
        <div class="resource-info">
          <div class="resource-name">{{ item.name || item.title }}</div>
        </div>
        <div class="resource-type">
          <el-tag size="small" type="info">{{ typeLabel(item.type) }}</el-tag>
        </div>
        <div class="resource-time">{{ formatRelativeTime(item.updatedAt) }}</div>
        <div class="resource-actions" @click.stop>
          <StarToggle :starred="item.starred" @toggle="handleToggleStar(item)" />
          <el-dropdown trigger="click" placement="bottom-end">
            <el-button link size="small">
              <el-icon><MoreFilled /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="goToResource(item)">
                  <el-icon><View /></el-icon>打开
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleShare(item)">
                  <el-icon><Share /></el-icon>分享
                </el-dropdown-item>
                <el-dropdown-item @click="handleRename(item)" v-if="item.type === 'doc'">
                  <el-icon><Edit /></el-icon>重命名
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleDelete(item)">
                  <el-icon><Delete /></el-icon>删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </div>

    <div v-else class="grid-view">
      <div
        v-for="item in resources"
        :key="`${item.type}-${item.id}`"
        class="grid-item"
        :class="{ selected: isSelected(item) }"
        @click="handleItemClick($event, item)"
      >
        <div class="grid-checkbox" @click.stop>
          <el-checkbox
            :model-value="isSelected(item)"
            @change="(val: boolean) => toggleSelect(item, val)"
          />
        </div>
        <div class="grid-icon">
          <el-icon :size="32">
            <Document v-if="item.type === 'doc'" />
            <Picture v-else-if="item.type === 'file'" />
            <Link v-else />
          </el-icon>
        </div>
        <div class="grid-name">{{ item.name || item.title }}</div>
        <div class="grid-meta">
          <el-tag size="small" type="info">{{ typeLabel(item.type) }}</el-tag>
        </div>
      </div>
    </div>

    <div v-if="total > pageSize" class="pagination-wrapper">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadResources"
      />
    </div>

    <!-- 分享对话框 -->
    <ShareDialog
      v-if="shareResource"
      v-model:visible="showShareDialog"
      :resource-id="shareResource.id"
      :resource-type="shareResource.type"
    />

    <!-- 批量移动对话框 -->
    <el-dialog v-model="showBatchMoveDialog" title="批量移动" width="400px">
      <el-form label-width="80px">
        <el-form-item label="目标目录">
          <el-select v-model="targetFolderId" placeholder="选择目标目录" style="width: 100%">
            <el-option label="根目录" :value="0" />
            <el-option
              v-for="folder in folderOptions"
              :key="folder.id"
              :label="folder.name"
              :value="folder.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBatchMoveDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmBatchMove">移动</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { List, Grid, Document, Picture, Link, FolderOpened, Loading, Star, MoreFilled, View, Edit, Delete, Folder, HomeFilled, Share } from '@element-plus/icons-vue'
import { getFileList, deleteFile, moveFile, toggleFileStar } from '@/api/file'
import { getDocList, deleteDoc, updateDoc, moveDoc, toggleDocStar } from '@/api/doc'
import { getWebPageList, deleteWebPage, moveWebPage, toggleWebPageStar } from '@/api/web'
import { getFolderTree } from '@/api/folder'
import { formatRelativeTime, typeLabel, navigateToResource } from '@/utils/format'
import { confirmDelete } from '@/utils/confirm'
import type { KbFile, Doc, WebPage } from '@/types'
import type { Folder as FolderType } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import StarToggle from '@/components/StarToggle.vue'
import ShareDialog from '@/components/ShareDialog.vue'

interface ResourceListItem {
  id: number
  name: string
  title: string
  type: 'file' | 'doc' | 'web'
  starred: boolean
  updatedAt: string
}

interface BreadcrumbItem {
  id: number
  name: string
}

const props = defineProps<{
  spaceId: number
  spaceName?: string
  folderId: number | null
}>()

const emit = defineEmits<{
  (e: 'refresh-tree'): void
  (e: 'folder-change', folderId: number | null): void
}>()

const router = useRouter()
const resources = ref<ResourceListItem[]>([])
const loading = ref(false)
const viewMode = ref<'list' | 'grid'>('list')
const sortBy = ref('time')
const page = ref(1)
const pageSize = 50
const total = ref(0)
const selectedItems = ref<ResourceListItem[]>([])
const breadcrumbs = ref<BreadcrumbItem[]>([])
const showShareDialog = ref(false)
const shareResource = ref<ResourceListItem | null>(null)
const showBatchMoveDialog = ref(false)
const targetFolderId = ref<number | null>(null)
const folderOptions = ref<FolderType[]>([])

const selectAll = computed({
  get: () => selectedItems.value.length === resources.value.length && resources.value.length > 0,
  set: (val: boolean) => {},
})

const isIndeterminate = computed(() => {
  return selectedItems.value.length > 0 && selectedItems.value.length < resources.value.length
})

onMounted(() => {
  loadResources()
  loadBreadcrumbs()
})

watch(() => [props.spaceId, props.folderId], () => {
  page.value = 1
  selectedItems.value = []
  loadResources()
  loadBreadcrumbs()
})

async function loadResources() {
  if (!props.spaceId) return
  loading.value = true
  try {
    const folderId = props.folderId || 0
    const params = { page: page.value, size: pageSize, folderId: props.folderId || undefined }
    const [fileRes, docRes, webRes] = await Promise.all([
      getFileList(params as any),
      getDocList(params as any),
      getWebPageList(params as any),
    ])

    const list: ResourceListItem[] = []
    for (const f of fileRes.data.data.list) {
      list.push({ id: f.id, name: f.name, title: f.name, type: 'file', starred: f.starred, updatedAt: f.updatedAt })
    }
    for (const d of docRes.data.data.list) {
      list.push({ id: d.id, name: d.title, title: d.title, type: 'doc', starred: d.starred, updatedAt: d.updatedAt })
    }
    for (const w of webRes.data.data.list) {
      list.push({ id: w.id, name: w.title, title: w.title, type: 'web', starred: w.starred, updatedAt: w.updatedAt })
    }

    total.value = fileRes.data.data.total + docRes.data.data.total + webRes.data.data.total

    if (sortBy.value === 'name') {
      list.sort((a, b) => a.name.localeCompare(b.name))
    } else if (sortBy.value === 'time') {
      list.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
    } else if (sortBy.value === 'type') {
      list.sort((a, b) => a.type.localeCompare(b.type))
    }

    resources.value = list
  } finally {
    loading.value = false
  }
}

async function loadBreadcrumbs() {
  if (!props.spaceId || !props.folderId) {
    breadcrumbs.value = []
    return
  }
  try {
    const res = await getFolderTree(props.spaceId)
    const path: BreadcrumbItem[] = []
    findFolderPath(res.data.data || [], props.folderId, path)
    breadcrumbs.value = path
  } catch {
    breadcrumbs.value = []
  }
}

function findFolderPath(tree: FolderType[], targetId: number, path: BreadcrumbItem[]): boolean {
  for (const folder of tree) {
    path.push({ id: folder.id, name: folder.name })
    if (folder.id === targetId) return true
    if (folder.children && findFolderPath(folder.children, targetId, path)) return true
    path.pop()
  }
  return false
}

function goToResource(item: ResourceListItem) {
  navigateToResource(router, item.type, item.id)
}

function handleItemClick(event: MouseEvent, item: ResourceListItem) {
  const target = event.target as HTMLElement
  if (target.closest('.el-checkbox')) return
  if (target.closest('.resource-actions')) return
  goToResource(item)
}

function isSelected(item: ResourceListItem): boolean {
  return selectedItems.value.some((s) => s.id === item.id && s.type === item.type)
}

function toggleSelect(item: ResourceListItem, val: boolean) {
  if (val) {
    selectedItems.value.push(item)
  } else {
    selectedItems.value = selectedItems.value.filter((s) => !(s.id === item.id && s.type === item.type))
  }
}

function handleSelectAll(val: boolean) {
  if (val) {
    selectedItems.value = [...resources.value]
  } else {
    selectedItems.value = []
  }
}

function clearSelection() {
  selectedItems.value = []
}

async function handleToggleStar(item: ResourceListItem) {
  if (item.type === 'file') {
    await toggleFileStar(item.id)
  } else if (item.type === 'doc') {
    await toggleDocStar(item.id)
  } else if (item.type === 'web') {
    await toggleWebPageStar(item.id)
  }
  item.starred = !item.starred
}

async function handleRename(item: ResourceListItem) {
  const { value } = await ElMessageBox.prompt('请输入新名称', '重命名', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: item.name,
    inputValidator: (val: string) => (val && val.trim().length > 0) || '名称不能为空',
  })
  const newName = value.trim()
  if (newName === item.name) return
  if (item.type === 'doc') {
    await updateDoc(item.id, { title: newName } as any)
  }
  ElMessage.success('重命名成功')
  loadResources()
  emit('refresh-tree')
}

function handleShare(item: ResourceListItem) {
  shareResource.value = item
  showShareDialog.value = true
}

async function handleDelete(item: ResourceListItem) {
  try {
    const typeLabelText = typeLabel(item.type)
    await confirmDelete(
      `确定要删除${typeLabelText}"${item.name}"吗？删除后可在回收站恢复。`,
    )
    if (item.type === 'file') {
      await deleteFile(item.id)
    } else if (item.type === 'doc') {
      await deleteDoc(item.id)
    } else if (item.type === 'web') {
      await deleteWebPage(item.id)
    }
    ElMessage.success('已删除')
    loadResources()
    emit('refresh-tree')
  } catch {
    // 用户取消或错误已在拦截器处理
  }
}

async function handleBatchMove() {
  if (selectedItems.value.length === 0) {
    ElMessage.warning('请先选择要移动的资源')
    return
  }
  try {
    const res = await getFolderTree(props.spaceId)
    folderOptions.value = res.data.data || []
  } catch {
    ElMessage.error('加载目录失败')
    return
  }
  targetFolderId.value = null
  showBatchMoveDialog.value = true
}

async function confirmBatchMove() {
  if (targetFolderId.value === null) {
    ElMessage.warning('请选择目标目录')
    return
  }
  const folderId = targetFolderId.value
  try {
    const promises = selectedItems.value.map((item) => {
      if (item.type === 'file') return moveFile(item.id, folderId)
      else if (item.type === 'doc') return moveDoc(item.id, folderId)
      else if (item.type === 'web') return moveWebPage(item.id, folderId)
      return Promise.resolve()
    })
    await Promise.all(promises)
    ElMessage.success(`已移动 ${selectedItems.value.length} 项`)
    selectedItems.value = []
    showBatchMoveDialog.value = false
    loadResources()
    emit('refresh-tree')
  } catch {
    ElMessage.error('移动失败，请重试')
  }
}

async function handleBatchDelete() {
  const count = selectedItems.value.length
  try {
    await confirmDelete(
      `确定要删除选中的 ${count} 项吗？删除后可在回收站恢复。`,
      { confirmButtonText: '确定批量删除' },
    )
    const promises = selectedItems.value.map((item) => {
      if (item.type === 'file') return deleteFile(item.id)
      else if (item.type === 'doc') return deleteDoc(item.id)
      else if (item.type === 'web') return deleteWebPage(item.id)
      return Promise.resolve()
    })
    await Promise.all(promises)
    ElMessage.success(`已删除 ${count} 项`)
    selectedItems.value = []
    loadResources()
    emit('refresh-tree')
  } catch {
    // 错误已在拦截器处理
  }
}

function goToFolder(id: number | null) {
  emit('folder-change', id)
}
</script>

<style scoped lang="scss">
.resource-list {
  display: flex;
  flex-direction: column;
  height: 100%;

  .list-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
    gap: 8px;
    flex-wrap: wrap;
    flex-shrink: 0;

    .breadcrumb-section {
      flex: 1;
      min-width: 0;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-shrink: 0;
    }
  }

  .sort-select {
    width: 120px;
  }

  .batch-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 16px;
    margin-bottom: 12px;
    background: #ecf5ff;
    border: 1px solid #d9ecff;
    border-radius: 6px;
    flex-shrink: 0;

    .batch-info {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      color: #606266;

      .count {
        color: #409eff;
        font-weight: 600;
      }
    }

    .batch-actions {
      display: flex;
      gap: 8px;
    }
  }

  .loading-state {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 48px 0;
    color: #909399;
  }

  .empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 60px 20px;
    color: #c0c4cc;
    gap: 8px;

    .empty-icon {
      font-size: 48px;
      margin-bottom: 8px;
    }

    .empty-text {
      font-size: 15px;
      color: #909399;
    }

    .empty-tip {
      font-size: 13px;
      color: #c0c4cc;
    }
  }

  .list-view {
    flex: 1;
    overflow-y: auto;

    .list-table-header {
      display: flex;
      align-items: center;
      padding: 10px 12px;
      background: #f5f7fa;
      border-radius: 4px;
      font-size: 13px;
      font-weight: 500;
      color: #606266;
      margin-bottom: 4px;

      .header-name {
        flex: 1;
        margin-left: 12px;
      }

      .header-type {
        width: 80px;
        text-align: center;
      }

      .header-time {
        width: 160px;
        text-align: right;
        color: #909399;
      }

      .header-actions {
        width: 80px;
        text-align: right;
      }
    }

    .resource-item {
      display: flex;
      align-items: center;
      padding: 10px 12px;
      border-bottom: 1px solid #f0f0f0;
      cursor: pointer;
      transition: background-color 0.2s;

      &:hover {
        background-color: #f5f7fa;
      }

      &.selected {
        background-color: #ecf5ff;
      }

      .resource-icon {
        font-size: 22px;
        margin-left: 12px;
        margin-right: 12px;
        color: #909399;
      }

      .resource-info {
        flex: 1;
        min-width: 0;

        .resource-name {
          font-size: 14px;
          color: #303133;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }

      .resource-type {
        width: 80px;
        text-align: center;
      }

      .resource-time {
        width: 160px;
        text-align: right;
        font-size: 13px;
        color: #909399;
      }

      .resource-actions {
        width: 80px;
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 4px;
      }
    }
  }

  .grid-view {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    gap: 12px;
    flex: 1;
    overflow-y: auto;
    align-content: start;

    .grid-item {
      position: relative;
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 16px 8px;
      border: 2px solid transparent;
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.2s;

      &:hover {
        border-color: #dcdfe6;
        background-color: #f5f7fa;
      }

      &.selected {
        border-color: #409eff;
        background-color: #ecf5ff;
      }

      .grid-checkbox {
        position: absolute;
        top: 8px;
        left: 8px;
      }

      .grid-icon {
        color: #909399;
        margin-bottom: 8px;
      }

      .grid-name {
        font-size: 13px;
        color: #303133;
        text-align: center;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        max-width: 100%;
      }

      .grid-meta {
        margin-top: 4px;
      }
    }
  }

  .pagination-wrapper {
    display: flex;
    justify-content: center;
    margin-top: 16px;
    flex-shrink: 0;
  }
}

@media (max-width: 768px) {
  .resource-list {
    .list-header {
      flex-direction: column;
      align-items: stretch;

      .sort-select {
        width: 100%;
      }
    }

    .grid-view {
      grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
      gap: 8px;

      .grid-item {
        padding: 12px 4px;
      }
    }
  }
}
</style>
