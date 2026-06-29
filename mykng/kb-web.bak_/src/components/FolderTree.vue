<template>
  <div class="folder-tree">
    <div class="tree-header">
      <span class="tree-title">目录</span>
      <el-button link type="primary" size="small" @click="handleCreateRoot">
        <el-icon><Plus /></el-icon>
      </el-button>
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
        <span class="tree-node">
          <el-icon><Folder /></el-icon>
          <span class="node-label">{{ node.label }}</span>
        </span>
      </template>
    </el-tree>

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
import { ref, reactive, onMounted, watch } from 'vue'
import { getFolderTree, createFolder, updateFolder, deleteFolder } from '@/api/folder'
import type { Folder } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'

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

onMounted(() => {
  loadTree()
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

function handleContextMenu(_event: MouseEvent, data: Folder) {
  // 右键菜单 - 简化实现，使用对话框
}

function handleCreateRoot() {
  createParentId.value = null
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

async function handleDeleteFolder(id: number) {
  await ElMessageBox.confirm('确定要删除此目录吗？目录下的资源将移至根目录。', '提示', { type: 'warning' })
  await deleteFolder(id)
  ElMessage.success('已删除')
  loadTree()
}

defineExpose({
  handleCreateRoot,
  handleDeleteFolder,
})
</script>

<style scoped lang="scss">
.folder-tree {
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

  .tree-node {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 14px;
  }

  .node-label {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
