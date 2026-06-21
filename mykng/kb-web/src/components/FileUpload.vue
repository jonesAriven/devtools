<template>
  <el-dialog v-model="dialogVisible" title="上传文件" width="600px" @close="handleClose">
    <div
      class="upload-drop-zone"
      :class="{ dragging: isDragging }"
      @dragover.prevent="isDragging = true"
      @dragleave.prevent="isDragging = false"
      @drop.prevent="handleDrop"
      @click="triggerFileInput"
    >
      <el-icon class="upload-icon"><Upload /></el-icon>
      <div class="upload-text">将文件拖拽到此处，或点击上传</div>
      <div class="upload-tip">支持批量上传，大文件自动分片</div>
    </div>

    <input
      ref="fileInputRef"
      type="file"
      multiple
      style="display: none"
      @change="handleFileSelect"
    />

    <div v-if="fileList.length > 0" class="upload-file-list">
      <div v-for="(item, index) in fileList" :key="index" class="upload-file-item">
        <div class="file-info">
          <el-icon><Document /></el-icon>
          <span class="file-name">{{ item.file.name }}</span>
          <span class="file-size">{{ formatFileSize(item.file.size) }}</span>
        </div>
        <div class="file-progress">
          <el-progress :percentage="item.progress" :status="item.status === 'success' ? 'success' : item.status === 'error' ? 'exception' : undefined" />
        </div>
        <el-button v-if="item.status !== 'uploading'" link type="danger" size="small" @click="removeFile(index)">
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button type="primary" :disabled="fileList.length === 0" :loading="uploading" @click="handleUpload">
        开始上传
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { uploadFile, initChunkUpload, uploadChunk, completeChunkUpload } from '@/api/file'
import { formatFileSize } from '@/utils/format'
import { ElMessage } from 'element-plus'

const props = withDefaults(defineProps<{
  visible: boolean
  folderId?: number
  spaceId?: number
}>(), {
  folderId: 0,
  spaceId: 0,
})

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'uploaded'): void
}>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const fileInputRef = ref<HTMLInputElement>()
const isDragging = ref(false)
const uploading = ref(false)

interface FileItem {
  file: File
  progress: number
  status: 'pending' | 'uploading' | 'success' | 'error'
}

const fileList = ref<FileItem[]>([])

watch(() => props.visible, (val) => {
  if (val) {
    fileList.value = []
  }
})

function triggerFileInput() {
  fileInputRef.value?.click()
}

function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files) {
    addFiles(Array.from(input.files))
  }
  input.value = ''
}

function handleDrop(event: DragEvent) {
  isDragging.value = false
  if (event.dataTransfer?.files) {
    addFiles(Array.from(event.dataTransfer.files))
  }
}

function addFiles(files: File[]) {
  for (const file of files) {
    fileList.value.push({
      file,
      progress: 0,
      status: 'pending',
    })
  }
}

function removeFile(index: number) {
  fileList.value.splice(index, 1)
}

async function handleUpload() {
  if (!props.spaceId) {
    ElMessage.warning('请先选择空间')
    return
  }

  uploading.value = true
  const CHUNK_SIZE = 5 * 1024 * 1024

  for (const item of fileList.value) {
    if (item.status === 'success') continue
    item.status = 'uploading'

    try {
      if (item.file.size > CHUNK_SIZE) {
        await uploadLargeFile(item, CHUNK_SIZE)
      } else {
        await uploadSmallFile(item)
      }
      item.status = 'success'
      item.progress = 100
    } catch {
      item.status = 'error'
    }
  }

  uploading.value = false
  const allSuccess = fileList.value.every((f) => f.status === 'success')
  if (allSuccess) {
    ElMessage.success('全部上传成功')
    emit('uploaded')
  }
}

async function uploadSmallFile(item: FileItem) {
  const formData = new FormData()
  formData.append('file', item.file)
  formData.append('folderId', String(props.folderId))
  formData.append('spaceId', String(props.spaceId))
  await uploadFile(formData, (progress) => {
    item.progress = progress
  })
}

async function uploadLargeFile(item: FileItem, chunkSize: number) {
  const totalChunks = Math.ceil(item.file.size / chunkSize)
  const initRes = await initChunkUpload({
    name: item.file.name,
    fileSize: item.file.size,
    folderId: props.folderId,
    spaceId: props.spaceId,
    mimeType: item.file.type,
    totalChunks,
  })
  const { uploadId, storageKey } = initRes.data.data

  for (let i = 0; i < totalChunks; i++) {
    const start = i * chunkSize
    const end = Math.min(start + chunkSize, item.file.size)
    const chunk = item.file.slice(start, end)
    const formData = new FormData()
    formData.append('file', chunk)
    formData.append('uploadId', uploadId)
    formData.append('storageKey', storageKey)
    formData.append('chunkIndex', String(i))
    await uploadChunk(formData)
    item.progress = Math.round(((i + 1) / totalChunks) * 100)
  }

  await completeChunkUpload({ uploadId, storageKey })
}

function handleClose() {
  dialogVisible.value = false
}
</script>

<style scoped lang="scss">
.upload-drop-zone {
  border: 2px dashed #dcdfe6;
  border-radius: 8px;
  padding: 40px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.3s, background-color 0.3s;

  &:hover,
  &.dragging {
    border-color: #409eff;
    background-color: #ecf5ff;
  }

  .upload-icon {
    font-size: 48px;
    color: #c0c4cc;
    margin-bottom: 8px;
  }

  .upload-text {
    font-size: 14px;
    color: #606266;
  }

  .upload-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
  }
}

.upload-file-list {
  margin-top: 16px;
  max-height: 300px;
  overflow-y: auto;
}

.upload-file-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;

  .file-info {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 1;
    min-width: 0;
  }

  .file-name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 13px;
  }

  .file-size {
    font-size: 12px;
    color: #909399;
    flex-shrink: 0;
  }

  .file-progress {
    width: 150px;
    flex-shrink: 0;
  }
}

@media (max-width: 768px) {
  .upload-drop-zone {
    padding: 24px 12px;

    .upload-icon {
      font-size: 36px;
    }
  }

  .upload-file-item {
    flex-wrap: wrap;

    .file-progress {
      width: 100%;
      order: 3;
      margin-top: 4px;
    }
  }
}
</style>
