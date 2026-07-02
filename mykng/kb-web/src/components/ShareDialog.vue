<template>
  <el-dialog v-model="dialogVisible" title="分享" width="500px">
    <div v-if="!shareResult" class="share-form">
      <el-form :model="shareForm" label-width="100px">
        <el-form-item label="提取码">
          <el-input v-model="shareForm.extractCode" placeholder="留空则不设提取码" maxlength="6" />
        </el-form-item>
        <el-form-item label="有效期">
          <el-select v-model="shareForm.expireType" style="width: 100%">
            <el-option label="永久有效" value="never" />
            <el-option label="1天" value="1d" />
            <el-option label="7天" value="7d" />
            <el-option label="30天" value="30d" />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <div v-else class="share-result">
      <el-alert type="success" :closable="false" show-icon class="share-tip">
        分享链接已创建，可发送给他人访问
      </el-alert>
      <el-form label-width="100px">
        <el-form-item label="分享链接">
          <el-input :model-value="shareLink" readonly>
            <template #append>
              <el-button @click="handleCopyLink">
                <el-icon v-if="linkCopied" class="copy-success-icon"><Check /></el-icon>
                <span>{{ linkCopied ? '已复制' : '复制链接' }}</span>
              </el-button>
              <el-button @click="handleOpenLink" title="在新窗口打开">
                <el-icon><Link /></el-icon>
              </el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item v-if="shareResult.extractCode" label="提取码">
          <el-input :model-value="shareResult.extractCode" readonly>
            <template #append>
              <el-button @click="handleCopyCode">
                <el-icon v-if="codeCopied" class="copy-success-icon"><Check /></el-icon>
                <span>{{ codeCopied ? '已复制' : '复制' }}</span>
              </el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item v-if="shareResult.expireAt" label="有效期至">
          <span class="expire-text">{{ formatExpireAt(shareResult.expireAt) }}</span>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button v-if="!shareResult" type="primary" :loading="creating" @click="handleCreateShare">
        创建分享
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch } from 'vue'
import { createShare } from '@/api/share'
import type { Share } from '@/types'
import { ElMessage } from 'element-plus'
import { Check, Link } from '@element-plus/icons-vue'
import { CONTEXT_PATH } from '@/config'
import { copyToClipboard } from '@/utils/clipboard'

const props = defineProps<{
  visible: boolean
  resourceId: number
  resourceType: 'file' | 'doc' | 'web'
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const creating = ref(false)
const shareResult = ref<Share | null>(null)
const linkCopied = ref(false)
const codeCopied = ref(false)

const shareForm = reactive({
  extractCode: '',
  expireType: 'never',
})

watch(() => props.visible, (val) => {
  if (val) {
    shareResult.value = null
    shareForm.extractCode = ''
    shareForm.expireType = 'never'
    linkCopied.value = false
    codeCopied.value = false
  }
})

const shareLink = computed(() => {
  if (!shareResult.value) return ''
  return `${window.location.origin}${CONTEXT_PATH}/share/${shareResult.value.code}`
})

async function handleCreateShare() {
  creating.value = true
  try {
    let expireAt: string | undefined
    if (shareForm.expireType !== 'never') {
      const days = parseInt(shareForm.expireType)
      const date = new Date()
      date.setDate(date.getDate() + days)
      expireAt = date.toISOString()
    }

    const res = await createShare({
      resourceId: props.resourceId,
      resourceType: props.resourceType,
      extractCode: shareForm.extractCode || undefined,
      expireAt,
    })
    shareResult.value = res.data.data
  } catch {
    // 错误已在拦截器中处理
  } finally {
    creating.value = false
  }
}

async function handleCopyLink() {
  const ok = await copyToClipboard(shareLink.value)
  if (ok) {
    linkCopied.value = true
    ElMessage.success('链接已复制')
    setTimeout(() => { linkCopied.value = false }, 2000)
  } else {
    ElMessage.error('复制失败，请手动选择复制')
  }
}

async function handleCopyCode() {
  const ok = await copyToClipboard(shareResult.value?.extractCode || '')
  if (ok) {
    codeCopied.value = true
    ElMessage.success('提取码已复制')
    setTimeout(() => { codeCopied.value = false }, 2000)
  } else {
    ElMessage.error('复制失败，请手动选择复制')
  }
}

function handleOpenLink() {
  window.open(shareLink.value, '_blank', 'noopener,noreferrer')
}

/** 格式化有效期显示 */
function formatExpireAt(expireAt: string): string {
  if (!expireAt) return '永久有效'
  const d = new Date(expireAt)
  if (isNaN(d.getTime())) return '永久有效'
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${day} ${h}:${min}`
}

function handleClose() {
  dialogVisible.value = false
}
</script>

<style scoped lang="scss">
.share-result {
  margin-top: 8px;
}

.share-tip {
  margin-bottom: 16px;
}

.copy-success-icon {
  color: var(--el-color-success);
}

.expire-text {
  color: #909399;
  font-size: 13px;
}

@media (max-width: 768px) {
  .share-form,
  .share-result {
    :deep(.el-form-item__label) {
      width: 70px !important;
    }
  }
}
</style>
