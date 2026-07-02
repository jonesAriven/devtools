<template>
  <div class="folder-tree">
    <div class="tree-header">
      <span class="tree-title">目录</span>
      <div class="tree-actions">
        <el-tooltip content="新建目录" placement="top">
          <el-button link type="primary" size="small" @click="handleCreateRoot">
            <el-icon><Plus /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="刷新" placement="top">
          <el-button link size="small" @click="loadTree">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </div>
    <el-tree
      ref="treeRef"
      :data="treeData"
      :props="treeProps"
      node-key="id"
      highlight-current
      default-expand-all
      :expand-on-click-node="false"
      @node-click="handleNodeClick"
      @node-contextmenu="handleContextMenu"
    >
      <template #default="{ node, data }">
        <span class="tree-node" @contextmenu.prevent="handleContextMenu($event, data)">
          <el-icon><Folder /></el-icon>
          <span class="node-label">{{ node.label }}</span>
        </span>
      </template>
    </el-tree>

    <ul
      v-show="contextMenu.visible"
      class="context-menu"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      @click="contextMenu.visible = false"
    >
      <li @click="handleCreateChild">
        <el-icon><Plus /></el-icon>
        <span>新建子目录</span>
      </li>
      <li @click="handleRename">
        <el-icon><Edit /></el-icon>
        <span>重命名</span>
      </li>
      <li class="danger" @click="handleDelete">
        <el-icon><Delete /></el-icon>
        <span>删除</span>
      </li>
    </ul>

    <el-dialog v-model="showCreateDialog" :title="createParentId ? '新建子目录' : '新建目录'" width="400px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="目录名">
          <el-input v-model="createForm.name" placeholder="请输入目录名" @keyup.enter="handleCreateFolder" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateFolder">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showRenameDialog" title="重命名" width="400px">
      <el-form :model="renameForm" label-width="80px">
        <el-form-item label="目录名">
          <el-input v-model="renameForm.name" @keyup.enter="handleRenameFolder" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRenameDialog = false">取消</el-button>
        <el-button type="primary" @click="handleRenameFolder">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch, onUnmounted } from 'vue'
import { getFolderTree, createFolder, updateFolder, deleteFolder } from '@/api/folder'
import type { Folder } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Edit, Delete } from '@element-plus/icons-vue'

const props = defineProps<{
  spaceId: number
  currentFolderId: number | null
}>()

const emit = defineEmits<{
  (e: 'select', folderId: number | null): void
}>()

const treeRef = ref()
const treeData = ref<Folder[]>([])
const treeProps = {
  label: 'name',
  children: 'children',
}

const showCreateDialog = ref(false)
const createParentId = ref<number | null>(null)
const createForm = reactive({ name: '' })

const showRenameDialog = ref(false)
const renameFolderId = ref<number>(0)
const renameForm = reactive({ name: '' })

const contextMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  folderId: 0,
  folderName: '',
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

async function loadTree() {
  if (!props.spaceId) return
  const res = await getFolderTree(props.spaceId)
  treeData.value = res.data.data
}

function handleNodeClick(data: Folder) {
  emit('select', data.id)
}

function handleContextMenu(event: MouseEvent, data: Folder) {
  event.preventDefault()
  event.stopPropagation()
  contextMenu.visible = true
  contextMenu.x = event.clientX
  contextMenu.y = event.clientY
  contextMenu.folderId = data.id
  contextMenu.folderName = data.name
}

function hideContextMenu() {
  contextMenu.visible = false
}

function handleCreateRoot() {
  createParentId.value = null
  createForm.name = ''
  showCreateDialog.value = true
}

function handleCreateChild() {
  createParentId.value = contextMenu.folderId
  createForm.name = ''
  showCreateDialog.value = true
}

async function handleCreateFolder() {
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
  showCreateDialog.value = false
  loadTree()
}

function handleRename() {
  renameFolderId.value = contextMenu.folderId
  renameForm.name = contextMenu.folderName
  showRenameDialog.value = true
}

async function handleRenameFolder() {
  if (!renameForm.name) {
    ElMessage.warning('请输入目录名')
    return
  }
  await updateFolder(renameFolderId.value, { name: renameForm.name })
  ElMessage.success('已重命名')
  showRenameDialog.value = false
  loadTree()
}

async function handleDelete() {
  await ElMessageBox.confirm(
    `确定要删除目录"${contextMenu.folderName}"吗？目录下的资源将移至根目录。`,
    '提示',
    { type: 'warning' }
  )
  await deleteFolder(contextMenu.folderId)
  ElMessage.success('已删除')
  if (props.currentFolderId === contextMenu.folderId) {
    emit('select', null)
  }
  loadTree()
}

defineExpose({
  handleCreateRoot,
  loadTree,
})
</script>

<style scoped lang="scss">
.folder-tree {
  position: relative;

  .tree-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }

  .tree-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }

  .tree-actions {
    display: flex;
    gap: 4px;
  }

  .tree-node {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 14px;
    width: 100%;
  }

  .node-label {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
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
  min-width: 140px;

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

    .el-icon {
      font-size: 16px;
    }
  }
}
</style>
