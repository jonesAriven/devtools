<template>
  <div class="resource-tree">
    <div class="tree-header">
      <span class="tree-title">资源树</span>
      <div class="tree-tools">
        <el-tooltip content="刷新" placement="top">
          <el-button link size="small" @click="loadTree">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </div>

    <div v-if="loading" class="loading-state">
      <el-icon class="is-loading" :size="16"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <div v-else-if="treeData.length === 0" class="empty-state">
      <el-icon><FolderOpened /></el-icon>
      <div class="empty-text">暂无资源</div>
    </div>

    <el-tree
      v-else
      ref="treeRef"
      :data="treeData"
      :props="treeProps"
      node-key="key"
      highlight-current
      :default-expand-all="false"
      :default-expanded-keys="defaultExpandedKeys"
      :expand-on-click-node="false"
      @node-click="handleNodeClick"
    >
      <template #default="{ node, data }">
        <span class="tree-node" :class="`type-${data.type}`">
          <el-icon class="node-icon">
            <Folder v-if="data.type === 'folder'" />
            <Document v-else-if="data.type === 'doc'" />
            <Files v-else-if="data.type === 'file'" />
            <Link v-else-if="data.type === 'web'" />
          </el-icon>
          <span class="node-label" :title="node.label">{{ node.label }}</span>
          <el-tag v-if="data.type === 'doc' && data.format === 'markdown'" size="small" type="success" effect="plain" class="format-tag">MD</el-tag>
        </span>
      </template>
    </el-tree>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getResourceTree } from '@/api/folder'
import type { ResourceTreeNode } from '@/types'
import { ElMessage } from 'element-plus'

interface TreeNode extends ResourceTreeNode {
  key: string
  children?: TreeNode[]
}

const props = defineProps<{
  spaceId: number
  currentFolderId?: number | null
}>()

const emit = defineEmits<{
  (e: 'select', node: ResourceTreeNode): void
}>()

const router = useRouter()
const treeRef = ref()
const treeData = ref<TreeNode[]>([])
const loading = ref(false)
const defaultExpandedKeys = ref<string[]>([])

const treeProps = {
  label: 'name',
  children: 'children',
}

onMounted(() => {
  loadTree()
})

watch(() => props.spaceId, () => {
  loadTree()
})

async function loadTree() {
  if (!props.spaceId) return
  loading.value = true
  try {
    const res = await getResourceTree(props.spaceId)
    const raw = res.data.data || []
    treeData.value = decorateTree(raw)
    // 默认展开第一层
    defaultExpandedKeys.value = treeData.value.map((n) => n.key)
  } catch (e: any) {
    ElMessage.error('加载资源树失败：' + (e?.message || ''))
  } finally {
    loading.value = false
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

function handleNodeClick(data: TreeNode) {
  emit('select', data)
  if (data.type === 'doc') {
    router.push(`/doc/${data.id}`)
  } else if (data.type === 'file') {
    router.push(`/file/${data.id}`)
  } else if (data.type === 'web') {
    router.push(`/web/${data.id}`)
  }
  // folder 不跳转，仅展开
}

defineExpose({ loadTree })
</script>

<style scoped lang="scss">
.resource-tree {
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

  .tree-tools {
    display: flex;
    gap: 4px;
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
</style>
