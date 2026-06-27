<template>
  <div class="web-detail-page">
    <div class="page-header">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>网页收藏详情</el-breadcrumb-item>
      </el-breadcrumb>
      <div class="page-actions">
        <StarToggle :starred="webPage?.starred || false" @toggle="handleToggleStar" />
        <el-button size="small" :icon="Share" @click="showShareDialog = true">分享</el-button>
        <el-button size="small" :icon="Refresh" @click="handleRefetch">重新抓取</el-button>
        <el-button size="small" type="danger" :icon="Delete" @click="handleDelete">删除</el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :md="16">
        <div class="info-card">
          <div class="web-title">{{ webPage?.title }}</div>
          <div class="web-url">
            <el-icon><Link /></el-icon>
            <a :href="webPage?.url" target="_blank" rel="noopener">{{ webPage?.url }}</a>
          </div>
        </div>

        <div class="info-card">
          <div class="card-title">正文内容</div>
          <div class="web-content" v-html="webPage?.content"></div>
        </div>
      </el-col>

      <el-col :xs="24" :md="8">
        <div class="info-card">
          <div class="card-title">标签</div>
          <TagInput
            :resource-id="Number(id)"
            resource-type="web"
          />
        </div>

        <div class="info-card" style="margin-top: 16px">
          <div class="card-title">信息</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="收藏时间">{{ formatDate(webPage?.createdAt || '') }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDate(webPage?.updatedAt || '') }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>
    </el-row>

    <ShareDialog
      v-model:visible="showShareDialog"
      :resource-id="Number(id)"
      resource-type="web"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Share, Refresh, Delete } from '@element-plus/icons-vue'
import { getWebPageDetail, deleteWebPage, toggleWebPageStar, refetchWebPage } from '@/api/web'
import { formatDate } from '@/utils/format'
import type { WebPage } from '@/types'
import StarToggle from '@/components/StarToggle.vue'
import TagInput from '@/components/TagInput.vue'
import ShareDialog from '@/components/ShareDialog.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps<{
  id: string
}>()

const router = useRouter()
const webPage = ref<WebPage | null>(null)
const showShareDialog = ref(false)

onMounted(() => {
  loadWebPage()
})

async function loadWebPage() {
  const res = await getWebPageDetail(Number(props.id))
  webPage.value = res.data.data
}

async function handleToggleStar() {
  await toggleWebPageStar(Number(props.id))
  if (webPage.value) {
    webPage.value.starred = !webPage.value.starred
  }
}

async function handleRefetch() {
  const res = await refetchWebPage(Number(props.id))
  webPage.value = res.data.data
  ElMessage.success('已重新抓取')
}

async function handleDelete() {
  await ElMessageBox.confirm('确定要删除此网页收藏吗？', '提示', { type: 'warning' })
  await deleteWebPage(Number(props.id))
  ElMessage.success('已删除')
  router.back()
}
</script>

<style scoped lang="scss">
.web-detail-page {
  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
    flex-wrap: wrap;
    gap: 8px;
  }

  .page-actions {
    flex-wrap: wrap;
  }

  .page-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .web-title {
    font-size: 20px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 8px;
  }

  .web-url {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 13px;
    color: #909399;

    a {
      color: #409eff;
      word-break: break-all;
    }
  }

  .web-content {
    padding: 16px;
    line-height: 1.8;
    color: #303133;
    font-size: 14px;

    :deep(img) {
      max-width: 100%;
      height: auto;
    }

    :deep(a) {
      color: #409eff;
    }
  }
}
</style>
