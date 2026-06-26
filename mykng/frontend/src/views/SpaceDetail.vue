<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type Node from 'element-plus/es/components/tree/src/model/node'
import { Plus, FolderPlus, FileText, Trash2, ArrowLeft } from 'lucide-vue-next'
import { folderApi, docApi } from '@/api/knowledge'
import type { Folder, Doc } from '@/types/api'
import { timeAgo } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const spaceId = Number(route.params.id)

const treeLoading = ref(false)
const folders = ref<Folder[]>([])
const treeProps = { label: 'name', children: 'children' }

const docsLoading = ref(false)
const docs = ref<Doc[]>([])
const currentFolderId = ref<number | null>(null)

const folderDialog = reactive({ visible: false, name: '', parentId: 0, submitting: false })

async function loadTree() {
  treeLoading.value = true
  try {
    folders.value = (await folderApi.tree(spaceId)) as Folder[]
  } finally {
    treeLoading.value = false
  }
}

async function loadDocs(folderId?: number) {
  docsLoading.value = true
  try {
    const res = await docApi.list({ folderId, page: 1, size: 100 })
    docs.value = res.list
  } finally {
    docsLoading.value = false
  }
}

function handleNodeClick(data: Folder) {
  currentFolderId.value = data.id
  loadDocs(data.id)
}

async function openCreateFolder(parent: Folder | null) {
  folderDialog.parentId = parent?.id || 0
  folderDialog.name = ''
  folderDialog.visible = true
}

async function submitFolder() {
  if (!folderDialog.name.trim()) {
    ElMessage.warning('请输入目录名称')
    return
  }
  folderDialog.submitting = true
  try {
    await folderApi.create({ spaceId, parentId: folderDialog.parentId, name: folderDialog.name })
    ElMessage.success('创建成功')
    folderDialog.visible = false
    await loadTree()
  } finally {
    folderDialog.submitting = false
  }
}

async function handleFolderDelete(node: Node, data: Folder) {
  await ElMessageBox.confirm(`确认删除目录「${data.name}」？`, '删除确认', { type: 'warning' })
  await folderApi.delete(data.id)
  ElMessage.success('删除成功')
  await loadTree()
}

async function createDoc() {
  const folderId = currentFolderId.value || folders.value[0]?.id || 0
  if (!folderId) {
    ElMessage.warning('请先创建目录')
    return
  }
  const doc = (await docApi.create({ folderId, title: '未命名文档', content: '' })) as Doc
  router.push(`/doc/${doc.id}`)
}

function openDoc(doc: Doc) {
  router.push(`/doc/${doc.id}`)
}

onMounted(async () => {
  await loadTree()
  if (folders.value.length > 0) {
    currentFolderId.value = folders.value[0].id
    await loadDocs(folders.value[0].id)
  } else {
    await loadDocs()
  }
})
</script>

<template>
  <div class="space-detail">
    <!-- 左栏：目录树 -->
    <aside class="left-panel" v-loading="treeLoading">
      <div class="panel-head">
        <span class="panel-title">目录</span>
        <el-button size="small" :icon="FolderPlus" @click="openCreateFolder(null)" />
      </div>
      <div class="tree-wrap">
        <el-tree
          :data="folders"
          :props="treeProps"
          node-key="id"
          default-expand-all
          highlight-current
          @node-click="handleNodeClick"
        >
          <template #default="{ node, data }">
            <span class="tree-node">
              <span class="tree-node-label">
                <FileText :size="14" /> {{ node.label }}
                <el-tag v-if="data.docCount" size="small" round>{{ data.docCount }}</el-tag>
              </span>
              <span class="tree-node-actions" @click.stop>
                <el-icon @click="openCreateFolder(data)"><Plus /></el-icon>
                <el-icon @click="handleFolderDelete(node, data)"><Trash2 /></el-icon>
              </span>
            </span>
          </template>
        </el-tree>
      </div>
    </aside>

    <!-- 中栏：文档列表 -->
    <main class="center-panel" v-loading="docsLoading">
      <div class="panel-head">
        <el-button :icon="ArrowLeft" text @click="router.push('/space')">返回空间</el-button>
        <span class="panel-title">文档</span>
        <el-button type="primary" :icon="Plus" @click="createDoc">新建文档</el-button>
      </div>
      <div class="doc-list">
        <div v-for="doc in docs" :key="doc.id" class="doc-item" @click="openDoc(doc)">
          <div class="doc-main">
            <span class="doc-title">{{ doc.title }}</span>
            <span class="doc-time">{{ timeAgo(doc.updatedAt) }}</span>
          </div>
          <FileText :size="16" class="doc-icon" />
        </div>
        <el-empty v-if="docs.length === 0" description="该目录下暂无文档" />
      </div>
    </main>

    <el-dialog v-model="folderDialog.visible" title="新建目录" width="400px">
      <el-input v-model="folderDialog.name" placeholder="请输入目录名称" maxlength="30" />
      <template #footer>
        <el-button @click="folderDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="folderDialog.submitting" @click="submitFolder">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.space-detail {
  display: flex;
  height: 100%;
  gap: 12px;
}

.left-panel {
  width: 240px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.center-panel {
  flex: 1;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-head {
  height: 48px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border-bottom: 1px solid #f0f0f0;
}

.panel-title {
  font-weight: 600;
  color: #2c3e50;
  flex: 1;
  font-size: 14px;
}

.tree-wrap {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 8px;

  .tree-node-label {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .tree-node-actions {
    display: none;
    gap: 6px;
    color: #95a5a6;
    .el-icon {
      cursor: pointer;
      &:hover {
        color: #d4a574;
      }
    }
  }

  &:hover .tree-node-actions {
    display: inline-flex;
  }
}

.doc-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.doc-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #faf8f5;
  }
}

.doc-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.doc-title {
  font-size: 14px;
  color: #2c3e50;
}

.doc-time {
  font-size: 12px;
  color: #95a5a6;
}

.doc-icon {
  color: #d4a574;
}
</style>
