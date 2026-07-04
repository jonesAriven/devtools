<template>
  <div class="file-preview">
    <!-- 加载中 -->
    <div v-if="loading" class="preview-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>正在加载文件预览...</span>
    </div>

    <template v-else>
      <!-- 加载错误 -->
      <div v-if="error" class="preview-error">
        <el-icon><WarningFilled /></el-icon>
        <span>{{ error }}</span>
      </div>

      <!-- docx 预览：用 v-show 而非 v-if，避免 docx-preview 写入 DOM 后 Vue 重新 patch 导致冲突 -->
      <div v-show="!error && previewType === 'docx'" ref="docxContainer" class="docx-preview-container"></div>

      <!-- xlsx 预览 -->
      <div v-if="!error && previewType === 'xlsx'" class="xlsx-preview-container">
        <div class="xlsx-toolbar">
          <el-select v-model="activeSheet" placeholder="选择工作表" size="small" @change="renderSheet">
            <el-option v-for="name in sheetNames" :key="name" :label="name" :value="name" />
          </el-select>
        </div>
        <div class="xlsx-table-wrapper" v-html="sanitizeTable(xlsxHtml)"></div>
      </div>

      <!-- pdf 预览 -->
      <iframe v-if="!error && previewType === 'pdf'" :src="blobUrl" class="pdf-preview-iframe"></iframe>

      <!-- 图片预览 -->
      <div v-if="!error && previewType === 'image'" class="image-preview-container">
        <img :src="blobUrl" :alt="fileName" class="preview-image" />
      </div>

      <!-- Markdown 预览 -->
      <div
        v-if="!error && previewType === 'markdown'"
        class="markdown-preview"
        v-html="renderedMarkdown"
      ></div>

      <!-- 纯文本预览（txt/json/xml/csv/log 等） -->
      <pre v-if="!error && previewType === 'text'" class="text-preview">{{ textContent }}</pre>

      <!-- 不支持的类型：回退到 Tika 解析的文本内容 -->
      <div v-if="!error && previewType === 'unsupported'" class="unsupported-preview">
        <el-icon><Document /></el-icon>
        <p>该文件类型暂不支持可视化预览</p>
        <p class="unsupported-tip">以下为 Tika 提取的文本内容：</p>
        <pre class="fallback-text">{{ textContent }}</pre>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { Loading, WarningFilled, Document } from '@element-plus/icons-vue'
import { getFileBlob, getFileContent } from '@/api/file'
import { sanitizeTable } from '@/utils/sanitize'
import { configureMarkdownIt } from '@/utils/markdownConfig'
import MarkdownIt from 'markdown-it'
import 'katex/dist/katex.min.css'
import '@/styles/markdown.scss'

const props = defineProps<{
  fileId: number
  fileName: string
  fileType?: string
}>()

const loading = ref(false)
const error = ref('')
const blobUrl = ref('')
const textContent = ref('')
const xlsxHtml = ref('')
const sheetNames = ref<string[]>([])
const activeSheet = ref('')
const docxContainer = ref<HTMLElement | null>(null)

const mdRenderer = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
})
configureMarkdownIt(mdRenderer)

const renderedMarkdown = computed(() => {
  if (!textContent.value) return ''
  return mdRenderer.render(textContent.value)
})

const isMarkdownFile = computed(() => {
  const name = (props.fileName || '').toLowerCase()
  return name.endsWith('.md') || name.endsWith('.markdown')
})

const previewType = computed(() => {
  const name = (props.fileName || '').toLowerCase()
  if (name.endsWith('.docx')) return 'docx'
  if (name.endsWith('.xlsx') || name.endsWith('.xls')) return 'xlsx'
  if (name.endsWith('.pdf')) return 'pdf'
  if (name.endsWith('.jpg') || name.endsWith('.jpeg') || name.endsWith('.png')
      || name.endsWith('.gif') || name.endsWith('.webp') || name.endsWith('.svg')
      || name.endsWith('.bmp')) return 'image'
  if (name.endsWith('.md') || name.endsWith('.markdown')) return 'markdown'
  if (name.endsWith('.txt') || name.endsWith('.json')
      || name.endsWith('.xml') || name.endsWith('.csv') || name.endsWith('.log')
      || name.endsWith('.html') || name.endsWith('.js') || name.endsWith('.ts')
      || name.endsWith('.java') || name.endsWith('.py') || name.endsWith('.go')
      || name.endsWith('.sql') || name.endsWith('.yml') || name.endsWith('.yaml')) return 'text'
  return 'unsupported'
})

onMounted(() => {
  loadPreview()
})

// 文件 ID 变化时重新加载
watch(() => props.fileId, () => {
  loadPreview()
})

onUnmounted(() => {
  cleanupBlob()
})

function cleanupBlob() {
  if (blobUrl.value) {
    window.URL.revokeObjectURL(blobUrl.value)
    blobUrl.value = ''
  }
}

async function loadPreview() {
  loading.value = true
  error.value = ''
  cleanupBlob()
  textContent.value = ''
  xlsxHtml.value = ''
  sheetNames.value = []

  try {
    const type = previewType.value

    if (type === 'docx') {
      // docx 需要先让容器渲染到 DOM（关闭 loading），再执行 renderAsync
      const buffer = await getFileBlob(props.fileId)
      loading.value = false
      await renderDocx(buffer)
    } else if (type === 'xlsx' || type === 'pdf' || type === 'image') {
      const buffer = await getFileBlob(props.fileId)
      if (type === 'xlsx') {
        renderXlsx(buffer)
      } else {
        const blob = new Blob([buffer], { type: getMimeType(type) })
        blobUrl.value = window.URL.createObjectURL(blob)
      }
    } else if (type === 'text') {
      const res = await getFileContent(props.fileId)
      textContent.value = res.data.data || ''
    } else {
      const res = await getFileContent(props.fileId)
      textContent.value = res.data.data || ''
    }
  } catch (e: any) {
    console.error('加载文件预览失败', e)
    error.value = '加载文件预览失败：' + (e.message || '未知错误')
    // 失败时尝试回退到文本内容
    try {
      const res = await getFileContent(props.fileId)
      textContent.value = res.data.data || ''
    } catch {
      // 忽略
    }
  } finally {
    loading.value = false
  }
}

async function renderDocx(buffer: ArrayBuffer) {
  await nextTick()
  if (!docxContainer.value) {
    await nextTick()
  }
  if (!docxContainer.value) return

  // 动态导入 docx-preview 避免影响首屏加载
  const { renderAsync } = await import('docx-preview')
  await renderAsync(buffer, docxContainer.value, null, {
    className: 'docx',
    inWrapper: true,
    ignoreWidth: false,
    ignoreHeight: false,
    breakPages: true,
  })
}

function renderXlsx(buffer: ArrayBuffer) {
  // 动态导入 xlsx 库
  import('xlsx').then((XLSX) => {
    const workbook = XLSX.read(buffer, { type: 'array' })
    sheetNames.value = workbook.SheetNames
    if (sheetNames.value.length > 0) {
      activeSheet.value = sheetNames.value[0]
      renderSheet(activeSheet.value)
    }
  })
}

function renderSheet(name: string) {
  import('xlsx').then((XLSX) => {
    // 重新读取 buffer（因为上次读取后可能已释放）
    getFileBlob(props.fileId).then((buffer) => {
      const workbook = XLSX.read(buffer, { type: 'array' })
      const sheet = workbook.Sheets[name]
      if (sheet) {
        xlsxHtml.value = XLSX.utils.sheet_to_html(sheet, { editable: false })
      }
    })
  })
}

function getMimeType(type: string): string {
  switch (type) {
    case 'pdf': return 'application/pdf'
    case 'image': return 'image/*'
    default: return 'application/octet-stream'
  }
}
</script>

<style scoped lang="scss">
.file-preview {
  min-height: 200px;

  .preview-loading,
  .preview-error {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
    padding: 48px;
    color: #909399;

    .el-icon {
      font-size: 32px;
    }
  }

  .preview-error {
    color: #f56c6c;
  }

  .docx-preview-container {
    :deep(.docx-wrapper) {
      background: #f5f7fa;
      padding: 16px;

      .docx {
        box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
        margin-bottom: 16px;
      }
    }
  }

  .xlsx-preview-container {
    .xlsx-toolbar {
      margin-bottom: 12px;
      padding: 8px 0;
    }

    .xlsx-table-wrapper {
      overflow: auto;
      max-height: 600px;
      border: 1px solid #ebeef5;
      border-radius: 4px;

      :deep(table) {
        border-collapse: collapse;
        width: 100%;
        font-size: 13px;

        td, th {
          border: 1px solid #ebeef5;
          padding: 6px 10px;
          text-align: left;
          white-space: nowrap;
        }

        th {
          background-color: #f5f7fa;
          font-weight: 600;
        }

        tr:hover td {
          background-color: var(--el-color-primary-light-9);
        }
      }
    }
  }

  .pdf-preview-iframe {
    width: 100%;
    height: 600px;
    border: 1px solid #ebeef5;
    border-radius: 4px;
  }

  .image-preview-container {
    display: flex;
    justify-content: center;
    padding: 16px;
    background: #f5f7fa;
    border-radius: 4px;

    .preview-image {
      max-width: 100%;
      max-height: 600px;
      object-fit: contain;
    }
  }

  .markdown-preview {
    padding: 24px;
    background-color: #fff;
    border: 1px solid #ebeef5;
    border-radius: 4px;
    max-height: 800px;
    overflow-y: auto;
    font-size: 14px;
    line-height: 1.8;
    color: #303133;

    h1, h2, h3, h4, h5, h6 {
      margin-top: 1.5em;
      margin-bottom: 0.5em;
      font-weight: 600;
      color: #1a1a1a;
    }

    h1 { font-size: 24px; }
    h2 { font-size: 20px; }
    h3 { font-size: 18px; }
    h4 { font-size: 16px; }

    p {
      margin: 1em 0;
    }

    ul, ol {
      padding-left: 24px;
      margin: 1em 0;
    }

    li {
      margin: 0.3em 0;
    }

    code {
      background-color: #f5f7fa;
      padding: 2px 6px;
      border-radius: 3px;
      font-size: 13px;
      font-family: 'Consolas', 'Monaco', monospace;
    }

    pre {
      background-color: #f5f7fa;
      padding: 16px;
      border-radius: 4px;
      overflow-x: auto;
      margin: 1em 0;

      code {
        background: none;
        padding: 0;
      }
    }

    blockquote {
      border-left: 4px solid #dcdfe6;
      padding-left: 16px;
      color: #909399;
      margin: 1em 0;
    }

    table {
      border-collapse: collapse;
      width: 100%;
      margin: 1em 0;

      th, td {
        border: 1px solid #ebeef5;
        padding: 8px 12px;
        text-align: left;
      }

      th {
        background-color: #f5f7fa;
        font-weight: 600;
      }
    }

    a {
      color: #409eff;
      text-decoration: none;

      &:hover {
        text-decoration: underline;
      }
    }

    img {
      max-width: 100%;
      border-radius: 4px;
    }

    hr {
      border: none;
      border-top: 1px solid #ebeef5;
      margin: 2em 0;
    }
  }

  .text-preview {
    white-space: pre-wrap;
    word-break: break-word;
    font-size: 13px;
    line-height: 1.6;
    margin: 0;
    padding: 12px;
    background-color: #f5f7fa;
    border-radius: 4px;
    max-height: 600px;
    overflow-y: auto;
    font-family: 'Consolas', 'Monaco', 'Microsoft YaHei', sans-serif;
  }

  .unsupported-preview {
    text-align: center;
    padding: 32px;
    color: #909399;

    .el-icon {
      font-size: 48px;
      margin-bottom: 12px;
    }

    p {
      margin: 4px 0;
    }

    .unsupported-tip {
      font-size: 12px;
      color: #c0c4cc;
      margin-top: 16px;
    }

    .fallback-text {
      text-align: left;
      white-space: pre-wrap;
      word-break: break-word;
      font-size: 13px;
      line-height: 1.6;
      margin: 8px 0 0;
      padding: 12px;
      background-color: #f5f7fa;
      border-radius: 4px;
      max-height: 400px;
      overflow-y: auto;
      font-family: 'Consolas', 'Monaco', 'Microsoft YaHei', sans-serif;
    }
  }
}
</style>
