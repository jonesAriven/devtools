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
      <el-form label-width="100px">
        <el-form-item label="分享链接">
          <el-input :model-value="shareLink" readonly>
            <template #append>
              <el-button @click="handleCopyLink">复制</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item v-if="shareResult.extractCode" label="提取码">
          <el-input :model-value="shareResult.extractCode" readonly>
            <template #append>
              <el-button @click="handleCopyCode">复制</el-button>
            </template>
          </el-input>
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
import { CONTEXT_PATH } from '@/config'

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

const shareForm = reactive({
  extractCode: '',
  expireType: 'never',
})

watch(() => props.visible, (val) => {
  if (val) {
    shareResult.value = null
    shareForm.extractCode = ''
    shareForm.expireType = 'never'
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
  try {
    await navigator.clipboard.writeText(shareLink.value)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

async function handleCopyCode() {
  try {
    await navigator.clipboard.writeText(shareResult.value?.extractCode || '')
    ElMessage.success('提取码已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

function handleClose() {
  dialogVisible.value = false
}
</script>

<style scoped lang="scss">
.share-result {
  margin-top: 8px;
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
