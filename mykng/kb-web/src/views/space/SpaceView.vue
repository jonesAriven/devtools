<template>
  <div class="space-page">
    <div class="space-header">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: `${CONTEXT_PATH}/dashboard` }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>{{ spaceStore.currentSpace?.name || '空间' }}</el-breadcrumb-item>
      </el-breadcrumb>
      <div class="space-actions">
        <el-button type="primary" :icon="Upload" size="small" @click="showUpload = true">上传文件</el-button>
        <el-button type="success" :icon="EditPen" size="small" @click="goCreateDoc">新建笔记</el-button>
        <el-button type="warning" :icon="Link" size="small" @click="showWebDialog = true">收藏网页</el-button>
      </div>
    </div>

    <div class="space-body">
      <div class="space-sidebar">
        <FolderTree
          :space-id="Number(spaceId)"
          :current-folder-id="currentFolderId"
          @select="handleFolderSelect"
        />
      </div>
      <div class="space-content">
        <ResourceList
          :space-id="Number(spaceId)"
          :folder-id="currentFolderId"
        />
      </div>
    </div>

    <FileUpload v-model:visible="showUpload" :folder-id="currentFolderId ?? undefined" :space-id="Number(spaceId)" />

    <el-dialog v-model="showWebDialog" title="收藏网页" width="500px">
      <el-form :model="webForm" label-width="80px">
        <el-form-item label="网页地址">
          <el-input v-model="webForm.url" placeholder="请输入网页URL" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showWebDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddWeb">收藏</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Upload, EditPen, Link } from '@element-plus/icons-vue'
import { useSpaceStore } from '@/stores/space'
import { createWebPage } from '@/api/web'
import FolderTree from '@/components/FolderTree.vue'
import ResourceList from '@/components/ResourceList.vue'
import FileUpload from '@/components/FileUpload.vue'
import { ElMessage } from 'element-plus'
import { CONTEXT_PATH } from '@/config'

const props = defineProps<{
  spaceId: string
}>()

const router = useRouter()
const spaceStore = useSpaceStore()

const currentFolderId = ref<number | null>(null)
const showUpload = ref(false)
const showWebDialog = ref(false)

const webForm = reactive({
  url: '',
})

onMounted(() => {
  loadSpace()
})

watch(() => props.spaceId, () => {
  loadSpace()
})

function loadSpace() {
  const id = Number(props.spaceId)
  if (spaceStore.currentSpace?.id !== id) {
    const space = spaceStore.spaceList.find((s) => s.id === id)
    if (space) {
      spaceStore.setCurrentSpace(space)
    }
  }
  currentFolderId.value = null
}

function handleFolderSelect(folderId: number | null) {
  currentFolderId.value = folderId
}

function goCreateDoc() {
  router.push({
    path: `${CONTEXT_PATH}/doc/create`,
    query: { spaceId: props.spaceId, folderId: currentFolderId.value?.toString() },
  })
}

async function handleAddWeb() {
  if (!webForm.url) {
    ElMessage.warning('请输入网页地址')
    return
  }
  try {
    await createWebPage({
      url: webForm.url,
      folderId: currentFolderId.value || 0,
      spaceId: Number(props.spaceId),
    })
    ElMessage.success('收藏成功')
    showWebDialog.value = false
    webForm.url = ''
  } catch {
    // 错误已在拦截器中处理
  }
}
</script>


<style scoped lang="scss">
.space-page {
  height: calc(100vh - 82px);
  display: flex;
  flex-direction: column;
}

.space-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.space-actions {
  display: flex;
  gap: 8px;
}

.space-body {
  flex: 1;
  display: flex;
  gap: 16px;
  min-height: 0;
}

.space-sidebar {
  width: 260px;
  flex-shrink: 0;
  background-color: #fff;
  border-radius: 4px;
  padding: 12px;
  overflow-y: auto;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}

.space-content {
  flex: 1;
  min-width: 0;
  background-color: #fff;
  border-radius: 4px;
  padding: 12px;
  overflow-y: auto;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}

@media (max-width: 768px) {
  .space-page {
    height: auto;
    min-height: calc(100vh - 82px);
  }

  .space-body {
    flex-direction: column;
  }

  .space-sidebar {
    width: 100%;
    max-height: 240px;
    margin-bottom: 12px;
  }
}
</style>
