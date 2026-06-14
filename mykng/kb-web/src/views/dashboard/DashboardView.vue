<template>
  <div class="dashboard-page">
    <div class="page-title">仪表盘</div>

    <el-row :gutter="16">
      <el-col :span="6">
        <div class="stat-card" @click="$router.push('/kb/space/' + spaceStore.currentSpace?.id)">
          <el-icon :size="32" color="#409eff"><Folder /></el-icon>
          <div class="stat-info">
            <div class="stat-value">{{ spaceStore.spaceList.length }}</div>
            <div class="stat-label">知识空间</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <el-icon :size="32" color="#67c23a"><Document /></el-icon>
          <div class="stat-info">
            <div class="stat-value">{{ stats.fileCount }}</div>
            <div class="stat-label">文件</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <el-icon :size="32" color="#e6a23c"><EditPen /></el-icon>
          <div class="stat-info">
            <div class="stat-value">{{ stats.docCount }}</div>
            <div class="stat-label">笔记</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <el-icon :size="32" color="#f56c6c"><Link /></el-icon>
          <div class="stat-info">
            <div class="stat-value">{{ stats.webCount }}</div>
            <div class="stat-label">网页收藏</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="16">
        <div class="info-card">
          <div class="card-title">最近访问</div>
          <div v-if="recentItems.length === 0" class="empty-state">
            <el-icon class="empty-icon"><Clock /></el-icon>
            <div class="empty-text">暂无最近访问记录</div>
          </div>
          <div v-else>
            <div v-for="item in recentItems" :key="item.id" class="resource-item" @click="goToResource(item)">
              <el-icon class="resource-icon">
                <Document v-if="item.type === 'doc'" />
                <Picture v-else-if="item.type === 'file'" />
                <Link v-else />
              </el-icon>
              <div class="resource-info">
                <div class="resource-name">{{ item.title }}</div>
                <div class="resource-meta">{{ formatRelativeTime(item.updatedAt) }}</div>
              </div>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :span="8">
        <div class="info-card">
          <div class="card-title">快捷操作</div>
          <div class="quick-actions">
            <el-button type="primary" :icon="Upload" @click="showUpload = true">上传文件</el-button>
            <el-button type="success" :icon="EditPen" @click="$router.push('/kb/doc/create')">新建笔记</el-button>
            <el-button type="warning" :icon="Link" @click="showWebDialog = true">收藏网页</el-button>
          </div>
        </div>

        <div class="info-card" style="margin-top: 16px">
          <div class="card-title">星标资源</div>
          <div v-if="starredItems.length === 0" class="empty-state">
            <el-icon class="empty-icon"><Star /></el-icon>
            <div class="empty-text">暂无星标资源</div>
          </div>
          <div v-else>
            <div v-for="item in starredItems" :key="item.id" class="resource-item" @click="goToResource(item)">
              <el-icon class="resource-icon" color="#e6a23c"><Star /></el-icon>
              <div class="resource-info">
                <div class="resource-name">{{ item.title }}</div>
                <div class="resource-meta">{{ item.type === 'doc' ? '笔记' : item.type === 'file' ? '文件' : '网页' }}</div>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <FileUpload v-model:visible="showUpload" />

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
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Upload, EditPen, Link } from '@element-plus/icons-vue'
import { useSpaceStore } from '@/stores/space'
import { createWebPage } from '@/api/web'
import { formatRelativeTime } from '@/utils/format'
import type { ResourceItem } from '@/types'
import FileUpload from '@/components/FileUpload.vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const spaceStore = useSpaceStore()

const stats = reactive({
  fileCount: 0,
  docCount: 0,
  webCount: 0,
})

const recentItems = ref<ResourceItem[]>([])
const starredItems = ref<ResourceItem[]>([])
const showUpload = ref(false)
const showWebDialog = ref(false)

const webForm = reactive({
  url: '',
})

onMounted(() => {
  loadDashboard()
})

function loadDashboard() {
  // 后续对接后端API获取统计数据
  stats.fileCount = 0
  stats.docCount = 0
  stats.webCount = 0
}

function goToResource(item: ResourceItem) {
  if (item.type === 'file') {
    router.push(`/kb/file/${item.id}`)
  } else if (item.type === 'doc') {
    router.push(`/kb/doc/${item.id}`)
  } else if (item.type === 'web') {
    router.push(`/kb/web/${item.id}`)
  }
}

async function handleAddWeb() {
  if (!webForm.url) {
    ElMessage.warning('请输入网页地址')
    return
  }
  const spaceId = spaceStore.currentSpace?.id
  if (!spaceId) {
    ElMessage.warning('请先选择一个空间')
    return
  }
  try {
    await createWebPage({ url: webForm.url, folderId: 0, spaceId })
    ElMessage.success('收藏成功')
    showWebDialog.value = false
    webForm.url = ''
  } catch {
    // 错误已在拦截器中处理
  }
}
</script>

<style scoped lang="scss">
.dashboard-page {
  .stat-card {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 20px;
    background-color: #fff;
    border-radius: 4px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
    cursor: pointer;
    transition: box-shadow 0.3s;

    &:hover {
      box-shadow: 0 4px 16px 0 rgba(0, 0, 0, 0.1);
    }
  }

  .stat-info {
    .stat-value {
      font-size: 24px;
      font-weight: 600;
      color: #303133;
    }

    .stat-label {
      font-size: 13px;
      color: #909399;
      margin-top: 2px;
    }
  }

  .quick-actions {
    display: flex;
    flex-direction: column;
    gap: 12px;

    .el-button {
      width: 100%;
    }
  }
}
</style>
