<template>
  <div class="share-access-page">
    <div v-if="needExtractCode && !verified" class="extract-code-card">
      <div class="extract-title">请输入提取码</div>
      <el-form @submit.prevent="handleVerify">
        <el-input
          v-model="extractCode"
          placeholder="请输入提取码"
          size="large"
          maxlength="6"
          style="width: 300px"
        />
        <el-button type="primary" size="large" style="margin-top: 16px; width: 300px" :loading="verifying" @click="handleVerify">
          验证
        </el-button>
      </el-form>
    </div>

    <div v-else class="share-content-card">
      <div class="share-resource-title">{{ shareInfo?.title || '分享内容' }}</div>
      <div class="share-meta">
        <span>分享时间：{{ formatDate(shareInfo?.createdAt || '') }}</span>
      </div>

      <el-divider />

      <div v-if="shareResource" class="share-resource-content">
        <div v-if="shareResource.type === 'doc'" v-html="sanitizeDoc(shareResource.content)"></div>
        <div v-else-if="shareResource.type === 'web'" v-html="sanitizeWeb(shareResource.content)"></div>
        <div v-else-if="shareResource.type === 'file'">
          <p>文件名：{{ shareResource.name }}</p>
          <p>文件大小：{{ formatFileSize(shareResource.size || 0) }}</p>
          <el-button type="primary" @click="handleDownload">下载文件</el-button>
        </div>
      </div>
      <div v-else-if="!loading" class="empty-hint">
        <el-empty description="分享内容加载失败或已过期" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getShareByCode, verifyExtractCode, getShareContent } from '@/api/share'
import { getFileDownloadUrl } from '@/api/file'
import { formatDate, formatFileSize } from '@/utils/format'
import { sanitizeDoc, sanitizeWeb } from '@/utils/sanitize'
import type { Share } from '@/types'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  code: string
}>()

const route = useRoute()
const shareInfo = ref<Share | null>(null)
const shareResource = ref<any>(null)
const needExtractCode = ref(false)
const extractCode = ref('')
const verified = ref(false)
const verifying = ref(false)
const loading = ref(false)

onMounted(async () => {
  await loadShare()
})

async function loadShare() {
  loading.value = true
  try {
    const res = await getShareByCode(props.code)
    shareInfo.value = res.data.data
    if (shareInfo.value?.extractCode) {
      needExtractCode.value = true
    } else {
      await loadContent()
    }
  } catch {
    ElMessage.error('分享不存在或已过期')
  } finally {
    loading.value = false
  }
}

async function handleVerify() {
  if (!extractCode.value) {
    ElMessage.warning('请输入提取码')
    return
  }
  verifying.value = true
  try {
    const res = await verifyExtractCode(props.code, extractCode.value)
    if (res.data.data) {
      verified.value = true
      await loadContent()
    } else {
      ElMessage.error('提取码错误')
    }
  } catch {
    ElMessage.error('验证失败')
  } finally {
    verifying.value = false
  }
}

async function loadContent() {
  try {
    const res = await getShareContent(props.code, extractCode.value || undefined)
    shareResource.value = res.data.data
  } catch {
    // 错误已在拦截器处理
  }
}

async function handleDownload() {
  if (!shareResource.value) return
  try {
    const res = await getFileDownloadUrl(shareResource.value.id)
    const url = res.data.data
    if (url) {
      const link = document.createElement('a')
      link.href = url
      link.download = shareResource.value.name || 'download'
      link.target = '_blank'
      link.click()
    }
  } catch {
    ElMessage.error('下载失败')
  }
}
</script>

<style scoped lang="scss">
.share-access-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 40px 20px;
  min-height: 100vh;
  background-color: #faf8f5;
}

.extract-code-card {
  background-color: #fff;
  border-radius: 8px;
  padding: 48px;
  text-align: center;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}

.extract-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 24px;
}

.share-content-card {
  background-color: #fff;
  border-radius: 8px;
  padding: 32px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}

.share-resource-title {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.share-meta {
  font-size: 13px;
  color: #909399;

  span + span {
    margin-left: 16px;
  }
}

.share-resource-content {
  line-height: 1.8;
  color: #303133;
  font-size: 14px;

  :deep(img) {
    max-width: 100%;
    height: auto;
  }
}

.empty-hint {
  padding: 40px 0;
}

@media (max-width: 768px) {
  .share-access-page {
    max-width: 100%;
    padding: 20px 12px;
  }

  .extract-code-card {
    padding: 24px 16px;
  }

  .share-content-card {
    padding: 16px;
  }

  .share-resource-title {
    font-size: 18px;
  }
}
</style>
