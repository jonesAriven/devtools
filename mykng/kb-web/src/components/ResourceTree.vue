<template>
  <div class="resource-tree">
    <div class="tree-header">
      <span class="tree-title" :title="spaceName">{{ spaceName || '资源' }}</span>
      <div class="tree-tools">
        <el-tooltip content="新建" placement="top">
          <el-dropdown trigger="click" @command="handleQuickCreate">
            <el-button link type="primary" size="small">
              <el-icon><Plus /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="folder">
                  <el-icon><Folder /></el-icon>新建目录
                </el-dropdown-item>
                <el-dropdown-item command="doc">
                  <el-icon><EditPen /></el-icon>新建笔记
                </el-dropdown-item>
                <el-dropdown-item command="upload">
                  <el-icon><Upload /></el-icon>上传文件
                </el-dropdown-item>
                <el-dropdown-item command="web">
                  <el-icon><Link /></el-icon>收藏网页
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-tooltip>
        <el-tooltip content="刷新" placement="top">
          <el-button link size="small" @click="loadTree">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </div>

    <div class="tree-search">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索资源..."
        size="small"
        clearable
        :prefix-icon="Search"
        @input="handleSearch"
      />
    </div>

    <div v-if="loading" class="loading-state">
      <el-icon class="is-loading" :size="16"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <div v-else-if="filteredTree.length === 0" class="empty-state">
      <el-icon><FolderOpened /></el-icon>
      <div class="empty-text">暂无资源</div>
    </div>

    <el-tree
      v-else
      ref="treeRef"
      :data="filteredTree"
      :props="treeProps"
      node-key="key"
      highlight-current
      :default-expand-all="false"
      :default-expanded-keys="defaultExpandedKeys"
      :expand-on-click-node="false"
      :filter-node-method="filterNode"
      @node-click="handleNodeClick"
      @node-contextmenu="handleContextMenu"
      class="tree-content"
    >
      <template #default="{ node, data }">
        <span
          class="tree-node"
          :class="[`type-${data.type}`, { 'is-current': data.type === 'doc' && props.currentDocId && data.id === props.currentDocId }]"
          @contextmenu.prevent="handleContextMenu($event, data)"
        >
          <el-icon class="node-icon">
            <Folder v-if="data.type === 'folder'" />
            <Document v-else-if="data.type === 'doc'" />
            <Files v-else-if="data.type === 'file'" />
            <Link v-else-if="data.type === 'web'" />
          </el-icon>
          <span class="node-label" :title="node.label">{{ node.label }}</span>
          <el-tag
            v-if="data.type === 'doc' && data.format === 'markdown'"
            size="small"
            type="success"
            effect="plain"
            class="format-tag"
          >MD</el-tag>
        </span>
      </template>
    </el-tree>

    <ul
      v-show="contextMenu.visible"
      class="context-menu"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      @click="contextMenu.visible = false"
    >
      <template v-if="contextMenu.type === 'folder'">
        <li @click="handleCreateInFolder('folder')">
          <el-icon><Folder /></el-icon>
          <span>新建子目录</span>
        </li>
        <li @click="handleCreateInFolder('doc')">
          <el-icon><EditPen /></el-icon>
          <span>新建笔记</span>
        </li>
        <li @click="handleCreateInFolder('upload')">
          <el-icon><Upload /></el-icon>
          <span>上传文件</span>
        </li>
        <li @click="handleCreateInFolder('web')">
          <el-icon><Link /></el-icon>
          <span>收藏网页</span>
        </li>
        <li class="menu-divider"></li>
        <li @click="handleRename">
          <el-icon><Edit /></el-icon>
          <span>重命名</span>
        </li>
        <li class="danger" @click="handleDelete">
          <el-icon><Delete /></el-icon>
          <span>删除</span>
        </li>
      </template>
      <template v-else-if="contextMenu.type === 'doc'">
        <li @click="handleOpen">
          <el-icon><View /></el-icon>
          <span>打开</span>
        </li>
        <li class="menu-divider"></li>
        <li @click="handleShare">
          <el-icon><Share /></el-icon>
          <span>分享</span>
        </li>
        <li @click="handleRename">
          <el-icon><Edit /></el-icon>
          <span>重命名</span>
        </li>
        <li class="danger" @click="handleDelete">
          <el-icon><Delete /></el-icon>
          <span>删除</span>
        </li>
      </template>
      <template v-else-if="contextMenu.type === 'file'">
        <li @click="handleOpen">
          <el-icon><View /></el-icon>
          <span>打开</span>
        </li>
        <li @click="handleDownload">
          <el-icon><Download /></el-icon>
          <span>下载</span>
        </li>
        <li class="menu-divider"></li>
        <li @click="handleShare">
          <el-icon><Share /></el-icon>
          <span>分享</span>
        </li>
        <li class="danger" @click="handleDelete">
          <el-icon><Delete /></el-icon>
          <span>删除</span>
        </li>
      </template>
      <template v-else-if="contextMenu.type === 'web'">
        <li @click="handleOpen">
          <el-icon><View /></el-icon>
          <span>打开</span>
        </li>
        <li class="menu-divider"></li>
        <li @click="handleShare">
          <el-icon><Share /></el-icon>
          <span>分享</span>
        </li>
        <li @click="handleRename">
          <el-icon><Edit /></el-icon>
          <span>重命名</span>
        </li>
        <li class="danger" @click="handleDelete">
          <el-icon><Delete /></el-icon>
          <span>删除</span>
        </li>
      </template>
    </ul>

    <el-dialog v-model="showCreateFolderDialog" title="新建目录" width="400px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="目录名">
          <el-input v-model="createForm.name" placeholder="请输入目录名" @keyup.enter="handleCreateFolderSubmit" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateFolderDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateFolderSubmit">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showRenameDialog" title="重命名" width="400px">
      <el-form :model="renameForm" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="renameForm.name" @keyup.enter="handleRenameSubmit" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRenameDialog = false">取消</el-button>
        <el-button type="primary" @click="handleRenameSubmit">确定</el-button>
      </template>
    </el-dialog>

    <ShareDialog
      v-model:visible="showShareDialog"
      :resource-id="contextMenu.id"
      :resource-type="contextMenu.type as 'doc' | 'file' | 'web'"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Refresh, Plus, Folder, Document, Files, Link, Edit, Delete, View, Download, EditPen, Upload, Loading, FolderOpened, Share } from '@element-plus/icons-vue'
import { getResourceTree, createFolder, updateFolder, deleteFolder } from '@/api/folder'
import { createWebPage, deleteWebPage } from '@/api/web'
import { deleteDoc } from '@/api/doc'
import { deleteFile, downloadFile } from '@/api/file'
import type { ResourceTreeNode } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import FileUpload from '@/components/FileUpload.vue'
import ShareDialog from '@/components/ShareDialog.vue'

interface TreeNode extends ResourceTreeNode {
  key: string
  children?: TreeNode[]
}

const props = defineProps<{
  spaceId: number
  spaceName?: string
  currentFolderId?: number | null
  currentDocId?: number | null
}>()

const emit = defineEmits<{
  (e: 'select', node: ResourceTreeNode): void
  (e: 'folder-change', folderId: number | null): void
  (e: 'refresh'): void
}>()

const router = useRouter()
const treeRef = ref()
const treeData = ref<TreeNode[]>([])
const loading = ref(false)
const searchKeyword = ref('')
const defaultExpandedKeys = ref<string[]>([])

const showUpload = ref(false)
const uploadFolderId = ref<number | undefined>(undefined)

const showCreateFolderDialog = ref(false)
const createParentId = ref<number | null>(null)
const createForm = reactive({ name: '' })

const showRenameDialog = ref(false)
const renameForm = reactive({ name: '' })
const renameTarget = reactive({ type: '', id: 0 })

const contextMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  id: 0,
  type: '',
  name: '',
})

const showShareDialog = ref(false)

const treeProps = {
  label: 'name',
  children: 'children',
}

const filteredTree = computed(() => {
  if (!searchKeyword.value) return treeData.value
  return treeData.value
})

onMounted(() => {
  loadTree()
  document.addEventListener('click', hideContextMenu)
})

onUnmounted(() => {
  document.removeEventListener('click', hideContextMenu)
})

watch(() => props.spaceId, () => {
  loadTree()
})

watch(() => props.currentDocId, (newId) => {
  if (newId && treeData.value.length > 0) {
    nextTick(() => {
      expandToDoc(newId)
    })
  }
})

async function loadTree() {
  if (!props.spaceId) return
  loading.value = true
  try {
    const res = await getResourceTree(props.spaceId)
    const raw = res.data.data || []
    treeData.value = decorateTree(raw)
    defaultExpandedKeys.value = treeData.value.map((n) => n.key)
    nextTick(() => {
      if (props.currentDocId) {
        expandToDoc(props.currentDocId)
      }
    })
  } catch (e: any) {
    ElMessage.error('加载资源树失败：' + (e?.message || ''))
  } finally {
    loading.value = false
  }
}

function findDocPath(nodes: TreeNode[], docId: number, path: string[] = []): string[] | null {
  for (const node of nodes) {
    if (node.type === 'doc' && node.id === docId) {
      return path
    }
    if (node.children && node.children.length > 0) {
      const result = findDocPath(node.children, docId, [...path, node.key])
      if (result) return result
    }
  }
  return null
}

function expandToDoc(docId: number) {
  const path = findDocPath(treeData.value, docId)
  if (path && path.length > 0) {
    path.forEach((key) => {
      const node = treeRef.value?.getNode(key)
      if (node) {
        node.expanded = true
      }
    })
  }
}

function decorateTree(nodes: ResourceTreeNode[]): TreeNode[] {
  return nodes.map((n) => {
    const decorated: TreeNode = {
      ...n,
      key: `${n.type}-${n.id}`,
    }
    if (n.children && n.children.length > 0) {
      decorated.children = decorateTree(n.children)
    }
    return decorated
  })
}

function handleSearch() {
  if (treeRef.value) {
    treeRef.value.filter(searchKeyword.value)
  }
}

function filterNode(value: string, data: TreeNode) {
  if (!value) return true
  return (data.name || '').toLowerCase().includes(value.toLowerCase())
}

function handleNodeClick(data: TreeNode) {
  emit('select', data)
  if (data.type === 'folder') {
    nextTick(() => {
      const node = treeRef.value?.getNode(data.key)
      if (node) {
        node.expanded = !node.expanded
      }
    })
  }
}

function handleContextMenu(event: MouseEvent, data: TreeNode) {
  event.preventDefault()
  event.stopPropagation()
  contextMenu.visible = true
  contextMenu.x = event.clientX
  contextMenu.y = event.clientY
  contextMenu.id = data.id
  contextMenu.type = data.type
  contextMenu.name = data.name
}

function hideContextMenu() {
  contextMenu.visible = false
}

function handleQuickCreate(command: string) {
  if (command === 'folder') {
    createParentId.value = null
    createForm.name = ''
    showCreateFolderDialog.value = true
  } else if (command === 'doc') {
    router.push({
      path: '/doc/create',
      query: { spaceId: props.spaceId?.toString(), folderId: '0' },
    })
  } else if (command === 'upload') {
    uploadFolderId.value = undefined
    showUpload.value = true
  } else if (command === 'web') {
    handleCreateWeb(0)
  }
}

function handleCreateInFolder(type: string) {
  const folderId = contextMenu.id
  if (type === 'folder') {
    createParentId.value = folderId
    createForm.name = ''
    showCreateFolderDialog.value = true
  } else if (type === 'doc') {
    router.push({
      path: '/doc/create',
      query: { spaceId: props.spaceId?.toString(), folderId: folderId.toString() },
    })
  } else if (type === 'upload') {
    uploadFolderId.value = folderId
    showUpload.value = true
  } else if (type === 'web') {
    handleCreateWeb(folderId)
  }
}

async function handleCreateFolderSubmit() {
  if (!createForm.name) {
    ElMessage.warning('请输入目录名')
    return
  }
  await createFolder({
    name: createForm.name,
    parentId: createParentId.value,
    spaceId: props.spaceId,
  })
  ElMessage.success('创建成功')
  showCreateFolderDialog.value = false
  loadTree()
  emit('refresh')
}

function handleRename() {
  renameTarget.id = contextMenu.id
  renameTarget.type = contextMenu.type
  renameForm.name = contextMenu.name
  showRenameDialog.value = true
}

async function handleRenameSubmit() {
  if (!renameForm.name) {
    ElMessage.warning('请输入名称')
    return
  }
  if (renameTarget.type === 'folder') {
    await updateFolder(renameTarget.id, { name: renameForm.name })
  } else {
    ElMessage.info('暂不支持重命名')
    return
  }
  ElMessage.success('已重命名')
  showRenameDialog.value = false
  loadTree()
  emit('refresh')
}

async function handleDelete() {
  const name = contextMenu.name
  const type = contextMenu.type
  const typeLabel: Record<string, string> = { folder: '目录', doc: '笔记', file: '文件', web: '网页' }
  await ElMessageBox.confirm(
    `确定要删除${typeLabel[type]}"${name}"吗？${type === 'folder' ? '目录下的内容将移至根目录。' : '此操作可在回收站恢复。'}`,
    '提示',
    { type: 'warning' }
  )
  if (type === 'folder') {
    await deleteFolder(contextMenu.id)
  } else if (type === 'doc') {
    await deleteDoc(contextMenu.id)
  } else if (type === 'file') {
    await deleteFile(contextMenu.id)
  } else if (type === 'web') {
    await deleteWebPage(contextMenu.id)
  }
  ElMessage.success('已删除')
  loadTree()
  emit('refresh')
}

function handleOpen() {
  if (contextMenu.type === 'doc') {
    router.push(`/doc/${contextMenu.id}`)
  } else if (contextMenu.type === 'file') {
    router.push(`/file/${contextMenu.id}`)
  } else if (contextMenu.type === 'web') {
    router.push(`/web/${contextMenu.id}`)
  }
}

function handleShare() {
  showShareDialog.value = true
}

async function handleDownload() {
  try {
    await downloadFile(contextMenu.id, contextMenu.name)
    ElMessage.success('下载成功')
  } catch {
    ElMessage.error('下载失败')
  }
}

async function handleCreateWeb(folderId: number) {
  const { value: url } = await ElMessageBox.prompt('请输入网页地址', '收藏网页', {
    confirmButtonText: '收藏',
    cancelButtonText: '取消',
    inputPattern: /^https?:\/\/.+/,
    inputErrorMessage: '请输入有效的 URL',
  })
  try {
    const res = await createWebPage({
      url: url as string,
      folderId,
      spaceId: props.spaceId,
    })
    ElMessage.success('收藏成功')
    loadTree()
    emit('refresh')
    if (res.data?.id) {
      router.push(`/web/${res.data.id}`)
    }
  } catch {
    // 错误已在拦截器处理
  }
}

defineExpose({ loadTree, expandToDoc })
</script>

<style scoped lang="scss">
.resource-tree {
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;

  .tree-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
    flex-shrink: 0;
  }

  .tree-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }

  .tree-tools {
    display: flex;
    gap: 2px;
  }

  .tree-search {
    margin-bottom: 8px;
    flex-shrink: 0;
  }

  .loading-state,
  .empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    padding: 24px 0;
    color: #c0c4cc;
    font-size: 13px;
  }

  .tree-content {
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
  }

  .tree-node {
    display: flex;
    align-items: center;
    gap: 6px;
    flex: 1;
    min-width: 0;
    font-size: 14px;
    padding-right: 6px;

    .node-icon {
      color: #909399;
      flex-shrink: 0;
    }

    .node-label {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .format-tag {
      flex-shrink: 0;
      transform: scale(0.85);
    }

    &.type-folder .node-icon {
      color: #e6a23c;
    }

    &.is-current {
      background-color: #ecf5ff;
      border-radius: 4px;
      margin-right: 4px;

      .node-label {
        color: #409eff;
        font-weight: 600;
      }

      .node-icon {
        color: #409eff;
      }
    }
    &.type-doc .node-icon {
      color: #409eff;
    }
    &.type-file .node-icon {
      color: #67c23a;
    }
    &.type-web .node-icon {
      color: #f56c6c;
    }
  }
}

.context-menu {
  position: fixed;
  z-index: 9999;
  list-style: none;
  margin: 0;
  padding: 6px 0;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  min-width: 160px;

  li {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 14px;
    font-size: 14px;
    color: #606266;
    cursor: pointer;
    transition: background-color 0.15s;

    &:hover {
      background-color: #f5f7fa;
      color: #409eff;
    }

    &.danger {
      color: #f56c6c;

      &:hover {
        background-color: #fef0f0;
        color: #f56c6c;
      }
    }

    &.menu-divider {
      padding: 0;
      margin: 4px 0;
      height: 1px;
      background-color: #ebeef5;
      cursor: default;

      &:hover {
        background-color: #ebeef5;
      }
    }

    .el-icon {
      font-size: 16px;
    }
  }
}
</style>
