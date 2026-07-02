<template>
  <div class="dashboard-page">
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card stat-card--blue" @click="goToCurrentSpace">
        <div class="stat-info">
          <div class="stat-label">文档数</div>
          <div class="stat-value">{{ stats.docCount }}</div>
        </div>
        <div class="stat-icon stat-icon--blue">
          <el-icon :size="28"><Document /></el-icon>
        </div>
      </div>
      <div class="stat-card stat-card--green" @click="router.push('/spaces')">
        <div class="stat-info">
          <div class="stat-label">空间数</div>
          <div class="stat-value">{{ stats.spaceCount }}</div>
        </div>
        <div class="stat-icon stat-icon--green">
          <el-icon :size="28"><FolderOpened /></el-icon>
        </div>
      </div>
      <div class="stat-card stat-card--orange" @click="router.push('/tag')">
        <div class="stat-info">
          <div class="stat-label">标签数</div>
          <div class="stat-value">{{ stats.tagCount }}</div>
        </div>
        <div class="stat-icon stat-icon--orange">
          <el-icon :size="28"><PriceTag /></el-icon>
        </div>
      </div>
      <div class="stat-card stat-card--purple" @click="router.push('/share')">
        <div class="stat-info">
          <div class="stat-label">分享数</div>
          <div class="stat-value">{{ stats.shareCount }}</div>
        </div>
        <div class="stat-icon stat-icon--purple">
          <el-icon :size="28"><Share /></el-icon>
        </div>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- 最近文档 -->
      <div class="panel panel--recent">
        <div class="panel-header">
          <el-icon class="panel-icon"><Clock /></el-icon>
          <span class="panel-title">最近文档</span>
        </div>
        <div class="panel-body">
          <div v-if="recentDocs.length === 0" class="empty-text">暂无文档</div>
          <div v-else>
            <div v-for="item in recentDocs" :key="item.id" class="recent-item" @click="goToItem(item)">
              <el-icon class="recent-icon"><Document /></el-icon>
              <div class="recent-info">
                <div class="recent-name">{{ item.title }}</div>
                <div class="recent-time">{{ formatRelativeTime(item.updatedAt) }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 最近文件 -->
      <div class="panel panel--recent">
        <div class="panel-header">
          <el-icon class="panel-icon"><Files /></el-icon>
          <span class="panel-title">最近文件</span>
        </div>
        <div class="panel-body">
          <div v-if="recentFiles.length === 0" class="empty-text">暂无文件</div>
          <div v-else>
            <div v-for="item in recentFiles" :key="item.id" class="recent-item" @click="goToFile(item)">
              <el-icon class="recent-icon recent-icon--file"><Picture /></el-icon>
              <div class="recent-info">
                <div class="recent-name">{{ item.name }}</div>
                <div class="recent-time">{{ formatRelativeTime(item.updatedAt) }} · {{ formatFileSize(item.size) }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 快捷操作 -->
      <div class="panel panel--actions">
        <div class="panel-header">
          <span class="panel-title">快捷操作</span>
        </div>
        <div class="panel-body">
          <div class="quick-actions-grid">
            <div class="quick-action" @click="router.push('/doc/create')">
              <div class="quick-action__icon quick-action__icon--blue">
                <el-icon :size="24"><EditPen /></el-icon>
              </div>
              <div class="quick-action__label">新建文档</div>
            </div>
            <div class="quick-action" @click="handleCreateSpace">
              <div class="quick-action__icon">
                <el-icon :size="24"><Plus /></el-icon>
              </div>
              <div class="quick-action__label">新建空间</div>
            </div>
            <div class="quick-action" @click="router.push('/search')">
              <div class="quick-action__icon">
                <el-icon :size="24"><Search /></el-icon>
              </div>
              <div class="quick-action__label">搜索文档</div>
            </div>
            <div class="quick-action" @click="router.push('/tag')">
              <div class="quick-action__icon">
                <el-icon :size="24"><PriceTag /></el-icon>
              </div>
              <div class="quick-action__label">我的标签</div>
            </div>
            <div class="quick-action" @click="router.push('/share')">
              <div class="quick-action__icon">
                <el-icon :size="24"><Share /></el-icon>
              </div>
              <div class="quick-action__label">分享中心</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建空间对话框 -->
    <el-dialog v-model="showSpaceDialog" title="新建空间" width="90%" style="max-width: 420px">
      <el-form :model="spaceForm" label-width="80px">
        <el-form-item label="空间名称">
          <el-input v-model="spaceForm.name" placeholder="请输入空间名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="spaceForm.description" type="textarea" :rows="3" placeholder="空间描述（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSpaceDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateSpaceSubmit">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Document, FolderOpened, PriceTag, Share, Clock, Plus, Search, EditPen, Files, Picture } from '@element-plus/icons-vue'
import { useSpaceStore } from '@/stores/space'
import { createSpace, getSpaceList } from '@/api/space'
import { getMyShares } from '@/api/share'
import { getTagList } from '@/api/tag'
import { getDocList } from '@/api/doc'
import { getFileList } from '@/api/file'
import { formatRelativeTime, formatFileSize } from '@/utils/format'
import { ElMessage } from 'element-plus'

const router = useRouter()
const spaceStore = useSpaceStore()

const stats = reactive({
  docCount: 0,
  spaceCount: 0,
  tagCount: 0,
  shareCount: 0,
})

const recentDocs = ref<any[]>([])
const recentFiles = ref<any[]>([])
const showSpaceDialog = ref(false)
const spaceForm = reactive({
  name: '',
  description: '',
})

onMounted(() => {
  loadStats()
})

async function loadStats() {
  try {
    const [spacesRes, tagsRes, sharesRes, docsRes, filesRes] = await Promise.allSettled([
      getSpaceList(),
      getTagList(),
      getMyShares(),
      getDocList({ page: 1, size: 10 }),
      getFileList({ page: 1, size: 10 }),
    ])

    if (spacesRes.status === 'fulfilled' && spacesRes.value?.data?.data) {
      const list = spacesRes.value.data.data
      stats.spaceCount = Array.isArray(list) ? list.length : 0
    }
    if (tagsRes.status === 'fulfilled' && tagsRes.value?.data?.data) {
      const list = tagsRes.value.data.data
      stats.tagCount = Array.isArray(list) ? list.length : 0
    }
    if (sharesRes.status === 'fulfilled' && sharesRes.value?.data?.data) {
      const list = sharesRes.value.data.data
      stats.shareCount = Array.isArray(list) ? list.length : 0
    }
    if (docsRes.status === 'fulfilled' && docsRes.value?.data?.data) {
      const pageResult = docsRes.value.data.data
      const list = pageResult.list || []
      stats.docCount = pageResult.total || list.length
      // 前端按 updatedAt 倒序排序取前 6 条
      recentDocs.value = list
        .slice()
        .sort((a: any, b: any) => new Date(b.updatedAt || 0).getTime() - new Date(a.updatedAt || 0).getTime())
        .slice(0, 6)
    }
    if (filesRes.status === 'fulfilled' && filesRes.value?.data?.data) {
      const pageResult = filesRes.value.data.data
      const list = pageResult.list || []
      recentFiles.value = list
        .slice()
        .sort((a: any, b: any) => new Date(b.updatedAt || 0).getTime() - new Date(a.updatedAt || 0).getTime())
        .slice(0, 6)
    }
  } catch {
    // 统计加载失败不阻塞页面
  }
}

function goToItem(item: any) {
  if (item.type === 'doc') {
    router.push(`/doc/${item.id}`)
  } else {
    router.push(`/doc/${item.id}`)
  }
}

function goToFile(item: any) {
  router.push(`/file/${item.id}`)
}

function goToCurrentSpace() {
  const spaceId = spaceStore.currentSpace?.id
  if (spaceId) {
    router.push(`/space/${spaceId}`)
  } else {
    router.push('/spaces')
  }
}

function handleCreateSpace() {
  spaceForm.name = ''
  spaceForm.description = ''
  showSpaceDialog.value = true
}

async function handleCreateSpaceSubmit() {
  if (!spaceForm.name.trim()) {
    ElMessage.warning('请输入空间名称')
    return
  }
  try {
    await createSpace({ name: spaceForm.name, description: spaceForm.description })
    ElMessage.success('空间创建成功')
    showSpaceDialog.value = false
    spaceStore.fetchSpaceList()
    loadStats()
  } catch {
    // 错误已在拦截器处理
  }
}
</script>

<style scoped lang="scss">
.dashboard-page {
  padding: 20px 24px;
  min-height: 100%;
  background-color: #faf8f5;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  background: #fff;
  border-radius: 8px;
  border-left: 4px solid;
  cursor: pointer;
  transition: all 0.25s ease;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);

  &:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
    transform: translateY(-2px);
  }

  &--blue {
    border-left-color: #409eff;
    .stat-icon--blue { background-color: #ecf5ff; color: #409eff; }
  }
  &--green {
    border-left-color: #67c23a;
    .stat-icon--green { background-color: #f0f9eb; color: #67c23a; }
  }
  &--orange {
    border-left-color: #e6a23c;
    .stat-icon--orange { background-color: #fdf6ec; color: #e6a23c; }
  }
  &--purple {
    border-left-color: #9b59b6;
    .stat-icon--purple { background-color: #f3e8ff; color: #9b59b6; }
  }
}

.stat-info {
  .stat-label {
    font-size: 14px;
    color: #909399;
    margin-bottom: 8px;
  }
  .stat-value {
    font-size: 28px;
    font-weight: 700;
    color: #303133;
    line-height: 1;
  }
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.main-content {
  display: grid;
  grid-template-columns: 1fr 1fr 320px;
  gap: 20px;
}

.panel {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.panel-icon {
  color: #c9a96e;
  font-size: 18px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.panel-body {
  padding: 8px 0;
  min-height: 200px;
}

.empty-text {
  text-align: center;
  padding: 60px 0;
  color: #c0c4cc;
  font-size: 14px;
}

.recent-item {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #faf8f5;
  }
}

.recent-icon {
  font-size: 20px;
  color: #409eff;
  margin-right: 12px;
  flex-shrink: 0;

  &--file {
    color: #e6a23c;
  }
}

.recent-info {
  flex: 1;
  min-width: 0;
}

.recent-name {
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 2px;
}

.quick-actions-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  padding: 20px;
}

.quick-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px 8px;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s;

  &:hover {
    background-color: #faf8f5;

    .quick-action__icon {
      background-color: #c9a96e;
      color: #fff;
    }
  }
}

.quick-action__icon {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background-color: #2c3e50;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
  transition: all 0.25s ease;
}

.quick-action__label {
  font-size: 13px;
  color: #606266;
}

@media (max-width: 1200px) {
  .main-content {
    grid-template-columns: 1fr 320px;
  }
  .panel--recent:nth-of-type(2) {
    grid-column: 1 / -1;
  }
}

@media (max-width: 1024px) {
  .stat-cards {
    grid-template-columns: repeat(2, 1fr);
  }
  .main-content {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .dashboard-page {
    padding: 12px;
  }
  .stat-cards {
    grid-template-columns: 1fr 1fr;
    gap: 10px;
  }
  .stat-card {
    padding: 14px 16px;
  }
  .stat-value {
    font-size: 22px !important;
  }
  .stat-icon {
    width: 40px;
    height: 40px;
  }
}
</style>
