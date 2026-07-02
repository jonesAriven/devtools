<template>
  <div class="doc-edit-page">
    <div class="doc-header">
      <el-input
        v-model="doc.title"
        placeholder="请输入笔记标题"
        class="doc-title-input"
        size="large"
      />
      <div class="doc-actions">
        <el-select v-model="doc.format" placeholder="格式" style="width: 130px" size="large" @change="handleFormatChange">
          <el-option label="富文本 HTML" value="html" />
          <el-option label="Markdown" value="markdown" />
        </el-select>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        <el-dropdown @command="handleExport" trigger="click">
          <el-button size="large">
            导出<el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="markdown">导出 Markdown (.md)</el-dropdown-item>
              <el-dropdown-item command="html">导出 HTML (.html)</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button @click="showVersionDrawer = true; loadVersions()" size="large">
          <el-icon><Clock /></el-icon>&nbsp;历史版本
        </el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="18">
        <div class="editor-container">
          <!-- Markdown 编辑器 -->
          <MdEditor
            v-if="doc.format === 'markdown'"
            v-model="doc.content"
            :preview="true"
            language="zh-CN"
            :toolbars-exclude="['github', 'save']"
            placeholder="开始编写 Markdown 笔记..."
            style="height: 600px"
          />
          <!-- HTML 富文本编辑器 -->
          <template v-else>
            <div class="toolbar-container" ref="toolbarRef"></div>
            <div class="editor-content" ref="editorRef"></div>
          </template>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="info-card">
          <div class="card-title">目录</div>
          <el-select v-model="doc.folderId" placeholder="选择目录" style="width: 100%">
            <el-option
              v-for="folder in folders"
              :key="folder.id"
              :label="folder.name"
              :value="folder.id"
            />
          </el-select>
        </div>

        <div class="info-card" style="margin-top: 16px">
          <div class="card-title">文档大纲</div>
          <div v-if="outline.length === 0" class="outline-empty">
            <span>暂无大纲</span>
          </div>
          <ul v-else class="outline-list">
            <li
              v-for="item in outline"
              :key="item.id"
              :class="`outline-item level-${item.level}`"
              @click="handleOutlineClick(item.id)"
            >
              {{ item.text }}
            </li>
          </ul>
        </div>

        <div class="info-card" style="margin-top: 16px">
          <div class="card-title">标签</div>
          <TagInput
            :resource-id="Number(id)"
            resource-type="doc"
          />
        </div>

        <div class="info-card" style="margin-top: 16px">
          <div class="card-title">信息</div>
          <div class="doc-meta">
            <div>字数：{{ doc.wordCount || 0 }}</div>
            <div>格式：{{ doc.format === 'markdown' ? 'Markdown' : '富文本' }}</div>
            <div>创建：{{ formatDate(doc.createdAt || '') }}</div>
            <div>更新：{{ formatDate(doc.updatedAt || '') }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 版本历史抽屉 -->
    <el-drawer v-model="showVersionDrawer" title="历史版本" size="70%" direction="rtl">
      <div class="version-drawer">
        <div class="version-list-panel">
          <div v-if="versionsLoading" class="version-loading">加载中...</div>
          <div v-else-if="versions.length === 0" class="version-empty">暂无历史版本</div>
          <div
            v-for="v in versions"
            :key="v.id"
            class="version-item"
            :class="{ active: selectedVersionId === v.id }"
            @click="selectVersion(v)"
          >
            <div class="version-item__header">
              <span class="version-item__num">v{{ v.version }}</span>
              <el-tag v-if="v.isCurrent" size="small" type="success">当前</el-tag>
            </div>
            <div class="version-item__time">{{ v.createdAt }}</div>
            <div class="version-item__preview">{{ (v.content || '').replace(/<[^>]+>/g, '').slice(0, 60) }}...</div>
          </div>
        </div>
        <div class="version-diff-panel">
          <div v-if="!selectedVersion" class="diff-empty">请选择左侧的版本查看差异</div>
          <div v-else>
            <div class="diff-header">
              <span>v{{ selectedVersion.version }} 与当前版本对比</span>
              <el-button size="small" @click="diffMode = diffMode === 'inline' ? 'split' : 'inline'">
                {{ diffMode === 'inline' ? '切换分栏' : '切换内联' }}
              </el-button>
            </div>
            <div class="diff-content">
              <div
                v-for="(line, idx) in diffLines"
                :key="idx"
                class="diff-line"
                :class="{ 'diff-add': line.type === 'add', 'diff-del': line.type === 'del', 'diff-same': line.type === 'same' }"
              >
                <span class="diff-line__sign">{{ line.type === 'add' ? '+' : line.type === 'del' ? '-' : ' ' }}</span>
                <span class="diff-line__text">{{ line.text }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getDocDetail, updateDoc, getDocVersions } from '@/api/doc'
import { getFolderTree } from '@/api/folder'
import { formatDate } from '@/utils/format'
import { extractOutline, scrollToHeading } from '@/utils/docOutline'
import type { Doc, Folder, DocFormat, OutlineItem, DocVersion } from '@/types'
import TagInput from '@/components/TagInput.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Clock } from '@element-plus/icons-vue'
import { createEditor, createToolbar } from '@wangeditor/editor'
import type { IDomEditor, IEditorConfig, IToolbarConfig } from '@wangeditor/editor'
import '@wangeditor/editor/dist/css/style.css'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

const props = defineProps<{
  id: string
}>()

const route = useRoute()
const toolbarRef = ref<HTMLElement>()
const editorRef = ref<HTMLElement>()
const editorInstance = shallowRef<IDomEditor>()
const saving = ref(false)
const folders = ref<Folder[]>([])
const outline = ref<OutlineItem[]>([])
const dirty = ref(false)
const lastSavedTitle = ref('')
let autoSaveTimer: ReturnType<typeof setInterval> | null = null
let draftTimer: ReturnType<typeof setInterval> | null = null

// 版本历史
const showVersionDrawer = ref(false)
const versions = ref<DocVersion[]>([])
const versionsLoading = ref(false)
const selectedVersion = ref<DocVersion | null>(null)
const selectedVersionId = ref<string>('')
const diffLines = ref<{ type: 'add' | 'del' | 'same'; text: string }[]>([])
const diffMode = ref<'inline' | 'split'>('inline')

const doc = reactive<Partial<Doc> & { format: DocFormat }>({
  title: '',
  content: '',
  format: 'html',
  folderId: 0,
  spaceId: 0,
  wordCount: 0,
})

onMounted(async () => {
  await loadDoc()
  await loadFolders()
  initHtmlEditor()
  updateOutline()
  lastSavedTitle.value = doc.title || ''
  // 每 30 秒保存草稿到 localStorage
  draftTimer = setInterval(saveDraft, 30_000)
  // 每 2 分钟自动保存到服务器
  autoSaveTimer = setInterval(autoSave, 120_000)
  // 离开页面前提示
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  if (editorInstance.value) {
    editorInstance.value.destroy()
  }
  if (draftTimer) clearInterval(draftTimer)
  if (autoSaveTimer) clearInterval(autoSaveTimer)
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

// 监听内容变化更新大纲 + 标记脏数据
watch(() => doc.content, () => {
  updateOutline()
  dirty.value = true
})

watch(() => doc.title, () => {
  dirty.value = true
})

function handleBeforeUnload(e: BeforeUnloadEvent) {
  if (dirty.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}

function saveDraft() {
  if (!dirty.value) return
  try {
    const draft = {
      title: doc.title,
      content: doc.content,
      format: doc.format,
      folderId: doc.folderId,
    }
    localStorage.setItem(`doc-draft-${props.id}`, JSON.stringify(draft))
  } catch {
    // localStorage 不可用时静默忽略
  }
}

async function autoSave() {
  if (!dirty.value) return
  if (!doc.title) return
  try {
    let content = doc.content
    if (doc.format === 'html' && editorInstance.value) {
      content = editorInstance.value.getHtml()
    }
    await updateDoc(Number(props.id), {
      title: doc.title,
      content: content,
      format: doc.format,
      folderId: doc.folderId,
    })
    dirty.value = false
    ElMessage.success({ message: '已自动保存', duration: 1500 })
  } catch {
    // 自动保存失败不弹窗，下次重试
  }
}

async function loadDoc() {
  const res = await getDocDetail(Number(props.id))
  const data = res.data.data
  Object.assign(doc, data)
  // 兼容旧数据：format 为空时按 html 处理
  if (!doc.format) doc.format = 'html'
  if (!doc.content) doc.content = ''
}

async function loadFolders() {
  const spaceId = doc.spaceId || Number(route.query.spaceId)
  if (!spaceId) return
  const res = await getFolderTree(spaceId)
  folders.value = flattenFolders(res.data.data)
}

function flattenFolders(tree: Folder[]): Folder[] {
  const result: Folder[] = []
  for (const folder of tree) {
    result.push(folder)
    if (folder.children) {
      result.push(...flattenFolders(folder.children))
    }
  }
  return result
}

function initHtmlEditor() {
  if (doc.format !== 'html') return
  if (!toolbarRef.value || !editorRef.value) return

  if (editorInstance.value) {
    editorInstance.value.destroy()
    editorInstance.value = undefined as any
  }

  const editorConfig: Partial<IEditorConfig> = {
    placeholder: '开始编写笔记...',
    onChange(editor: IDomEditor) {
      doc.content = editor.getHtml()
      doc.wordCount = editor.getText().length
    },
  }

  const editor = createEditor({
    selector: editorRef.value,
    html: doc.content || '<p></p>',
    config: editorConfig,
  })

  editorInstance.value = editor

  const toolbarConfig: Partial<IToolbarConfig> = {}
  createToolbar({
    editor,
    selector: toolbarRef.value,
    config: toolbarConfig,
  })
}

async function handleFormatChange(newFormat: DocFormat) {
  // 切换格式提示
  try {
    await ElMessageBox.confirm(
      `切换到 ${newFormat === 'markdown' ? 'Markdown' : '富文本 HTML'} 模式？已编辑的内容将保留，但可能因格式差异显示异常。`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    // 用户取消，恢复原格式
    // 注意：v-model 已经更新，需要回退
    doc.format = newFormat === 'markdown' ? 'html' : 'markdown'
    return
  }
  // HTML 编辑器需要重新初始化（v-if 切换会重建 DOM）
  if (newFormat === 'html') {
    setTimeout(() => initHtmlEditor(), 50)
  }
}

function updateOutline() {
  outline.value = extractOutline(doc.content || '', doc.format)
}

function handleOutlineClick(id: string) {
  if (doc.format === 'markdown') {
    // Markdown 模式下无 DOM 锚点，提示使用编辑器目录
    ElMessage.info('Markdown 模式请使用编辑器内置大纲')
    return
  }
  scrollToHeading(id)
}

async function handleSave() {
  if (!doc.title) {
    ElMessage.warning('请输入笔记标题')
    return
  }
  saving.value = true
  try {
    let content = doc.content
    if (doc.format === 'html' && editorInstance.value) {
      content = editorInstance.value.getHtml()
    }
    await updateDoc(Number(props.id), {
      title: doc.title,
      content: content,
      format: doc.format,
      folderId: doc.folderId,
    })
    ElMessage.success('保存成功')
    dirty.value = false
  } catch {
    // 错误已在拦截器中处理
  } finally {
    saving.value = false
  }
}

async function loadVersions() {
  versionsLoading.value = true
  try {
    const res = await getDocVersions(Number(props.id))
    versions.value = res.data.data || []
  } catch {
    // 错误已在拦截器中处理
  } finally {
    versionsLoading.value = false
  }
}

function selectVersion(v: DocVersion) {
  selectedVersion.value = v
  selectedVersionId.value = v.id
  const currentContent = getCurrentContent()
  diffLines.value = computeDiff(v.content || '', currentContent)
}

function computeDiff(oldText: string, newText: string): { type: 'add' | 'del' | 'same'; text: string }[] {
  const oldLines = oldText.split('\n')
  const newLines = newText.split('\n')
  const result: { type: 'add' | 'del' | 'same'; text: string }[] = []
  const maxLen = Math.max(oldLines.length, newLines.length)
  for (let i = 0; i < maxLen; i++) {
    const oldLine = i < oldLines.length ? oldLines[i] : undefined
    const newLine = i < newLines.length ? newLines[i] : undefined
    if (oldLine === newLine) {
      result.push({ type: 'same', text: oldLine || '' })
    } else {
      if (oldLine !== undefined) {
        result.push({ type: 'del', text: oldLine })
      }
      if (newLine !== undefined) {
        result.push({ type: 'add', text: newLine })
      }
    }
  }
  return result
}

function handleExport(command: string) {
  const title = doc.title || '未命名文档'
  if (command === 'markdown') {
    exportMarkdown(title)
  } else if (command === 'html') {
    exportHtml(title)
  }
}

function getCurrentContent(): string {
  let content = doc.content || ''
  if (doc.format === 'html' && editorInstance.value) {
    content = editorInstance.value.getHtml()
  }
  return content
}

function downloadBlob(content: string, filename: string, mime: string) {
  const blob = new Blob([content], { type: `${mime};charset=utf-8` })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function exportMarkdown(title: string) {
  let content = getCurrentContent()
  // 如果当前是 HTML 格式，做简单的 HTML→Markdown 转换
  if (doc.format === 'html') {
    content = htmlToMarkdown(content)
  }
  const filename = `${title}.md`
  downloadBlob(content, filename, 'text/markdown')
  ElMessage.success(`已导出 ${filename}`)
}

function exportHtml(title: string) {
  let bodyContent = getCurrentContent()
  // 如果当前是 Markdown 格式，转换为 HTML
  if (doc.format === 'markdown') {
    bodyContent = markdownToHtml(bodyContent)
  }
  const fullHtml = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${title}</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 800px; margin: 40px auto; padding: 0 20px; color: #333; line-height: 1.8; }
    h1, h2, h3, h4, h5, h6 { color: #1a1a1a; margin-top: 1.5em; }
    img { max-width: 100%; }
    pre { background: #f5f5f5; padding: 16px; border-radius: 4px; overflow-x: auto; }
    code { background: #f5f5f5; padding: 2px 6px; border-radius: 3px; font-family: 'Consolas', monospace; }
    blockquote { border-left: 4px solid #ddd; padding-left: 16px; color: #666; margin: 1em 0; }
    table { border-collapse: collapse; width: 100%; }
    th, td { border: 1px solid #ddd; padding: 8px 12px; }
    th { background: #f5f5f5; }
    a { color: #409eff; }
  </style>
</head>
<body>
${bodyContent}
</body>
</html>`
  const filename = `${title}.html`
  downloadBlob(fullHtml, filename, 'text/html')
  ElMessage.success(`已导出 ${filename}`)
}

function htmlToMarkdown(html: string): string {
  let md = html
  md = md.replace(/<h1[^>]*>(.*?)<\/h1>/gi, '\n# $1\n')
  md = md.replace(/<h2[^>]*>(.*?)<\/h2>/gi, '\n## $1\n')
  md = md.replace(/<h3[^>]*>(.*?)<\/h3>/gi, '\n### $1\n')
  md = md.replace(/<h4[^>]*>(.*?)<\/h4>/gi, '\n#### $1\n')
  md = md.replace(/<h5[^>]*>(.*?)<\/h5>/gi, '\n##### $1\n')
  md = md.replace(/<h6[^>]*>(.*?)<\/h6>/gi, '\n###### $1\n')
  md = md.replace(/<strong[^>]*>(.*?)<\/strong>/gi, '**$1**')
  md = md.replace(/<b[^>]*>(.*?)<\/b>/gi, '**$1**')
  md = md.replace(/<em[^>]*>(.*?)<\/em>/gi, '*$1*')
  md = md.replace(/<i[^>]*>(.*?)<\/i>/gi, '*$1*')
  md = md.replace(/<li[^>]*>(.*?)<\/li>/gi, '- $1\n')
  md = md.replace(/<ul[^>]*>/gi, '\n').replace(/<\/ul>/gi, '\n')
  md = md.replace(/<ol[^>]*>/gi, '\n').replace(/<\/ol>/gi, '\n')
  md = md.replace(/<p[^>]*>(.*?)<\/p>/gi, '$1\n\n')
  md = md.replace(/<br\s*\/?>/gi, '\n')
  md = md.replace(/<a[^>]*href="(.*?)"[^>]*>(.*?)<\/a>/gi, '[$2]($1)')
  md = md.replace(/<img[^>]*src="(.*?)"[^>]*alt="(.*?)"[^>]*\/?>/gi, '![$2]($1)')
  md = md.replace(/<code[^>]*>(.*?)<\/code>/gi, '`$1`')
  md = md.replace(/<pre[^>]*>([\s\S]*?)<\/pre>/gi, '\n```\n$1\n```\n')
  md = md.replace(/<blockquote[^>]*>([\s\S]*?)<\/blockquote>/gi, '> $1\n')
  md = md.replace(/<[^>]+>/g, '')
  md = md.replace(/&nbsp;/g, ' ').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&')
  md = md.replace(/\n{3,}/g, '\n\n').trim()
  return md + '\n'
}

function markdownToHtml(md: string): string {
  let html = md
  html = html.replace(/^### (.*$)/gm, '<h3>$1</h3>')
  html = html.replace(/^## (.*$)/gm, '<h2>$1</h2>')
  html = html.replace(/^# (.*$)/gm, '<h1>$1</h1>')
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*(.+?)\*/g, '<em>$1</em>')
  html = html.replace(/`(.+?)`/g, '<code>$1</code>')
  html = html.replace(/!\[(.+?)\]\((.+?)\)/g, '<img src="$2" alt="$1">')
  html = html.replace(/\[(.+?)\]\((.+?)\)/g, '<a href="$2">$1</a>')
  html = html.replace(/^- (.*$)/gm, '<li>$1</li>')
  html = html.replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>')
  html = html.replace(/^> (.*$)/gm, '<blockquote>$1</blockquote>')
  html = html.replace(/\n\n([^<].*)/g, '<p>$1</p>')
  return html
}
</script>

<style scoped lang="scss">
.doc-edit-page {
  .doc-header {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 16px;
    flex-wrap: wrap;
  }

  .doc-title-input {
    flex: 1;
    min-width: 200px;

    :deep(.el-input__inner) {
      font-size: 20px;
      font-weight: 600;
    }
  }

  .doc-actions {
    flex-shrink: 0;
    display: flex;
    gap: 8px;
  }

  .editor-container {
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    overflow: hidden;
    background-color: #fff;

    .toolbar-container {
      border-bottom: 1px solid #dcdfe6;
    }

    .editor-content {
      min-height: 500px;
    }
  }

  .doc-meta {
    font-size: 13px;
    color: #909399;
    line-height: 2;
  }

  .outline-empty {
    color: #c0c4cc;
    font-size: 13px;
    padding: 8px 0;
    text-align: center;
  }

  .outline-list {
    list-style: none;
    padding: 0;
    margin: 0;
    max-height: 320px;
    overflow-y: auto;

    .outline-item {
      padding: 4px 8px;
      font-size: 13px;
      color: #606266;
      cursor: pointer;
      border-radius: 2px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      transition: background-color 0.2s;

      &:hover {
        background-color: #f5f7fa;
        color: #409eff;
      }

      &.level-1 { font-weight: 600; padding-left: 8px; }
      &.level-2 { padding-left: 16px; }
      &.level-3 { padding-left: 24px; }
      &.level-4 { padding-left: 32px; font-size: 12px; }
      &.level-5 { padding-left: 40px; font-size: 12px; }
      &.level-6 { padding-left: 48px; font-size: 12px; }
    }
  }
}

@media (max-width: 768px) {
  .doc-edit-page {
    .doc-header {
      flex-direction: column;
      gap: 8px;
      align-items: stretch;
    }

    .doc-title-input {
      :deep(.el-input__inner) {
        font-size: 16px;
      }
    }

    .doc-actions {
      .el-button {
        width: 100%;
      }
    }

    .editor-container {
      .editor-content {
        min-height: 300px;
      }
    }

    .info-card {
      margin-top: 12px;
    }
  }
}

.version-drawer {
  display: flex;
  height: 100%;
  gap: 16px;

  .version-list-panel {
    width: 280px;
    overflow-y: auto;
    border-right: 1px solid #ebeef5;
    padding-right: 12px;
  }

  .version-diff-panel {
    flex: 1;
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }

  .version-loading, .version-empty, .diff-empty {
    text-align: center;
    color: #909399;
    padding: 48px 0;
  }

  .version-item {
    padding: 10px 12px;
    border: 1px solid #ebeef5;
    border-radius: 4px;
    margin-bottom: 8px;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      border-color: #409eff;
      background: #f5f7fa;
    }

    &.active {
      border-color: #409eff;
      background: #ecf5ff;
    }

    &__header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 4px;
    }

    &__num {
      font-weight: 600;
      color: #303133;
    }

    &__time {
      font-size: 12px;
      color: #909399;
      margin-bottom: 4px;
    }

    &__preview {
      font-size: 12px;
      color: #606266;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .diff-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 8px;
    border-bottom: 1px solid #ebeef5;
    margin-bottom: 8px;
    font-weight: 600;
  }

  .diff-content {
    flex: 1;
    overflow-y: auto;
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 13px;
    line-height: 1.6;
  }

  .diff-line {
    display: flex;
    padding: 2px 8px;

    &__sign {
      width: 20px;
      flex-shrink: 0;
      text-align: center;
    }

    &__text {
      flex: 1;
      white-space: pre-wrap;
      word-break: break-all;
    }

    &.diff-add {
      background: #e6ffed;
      .diff-line__text { color: #22863a; }
    }

    &.diff-del {
      background: #ffeef0;
      .diff-line__text { color: #cb2431; }
    }

    &.diff-same {
      .diff-line__text { color: #606266; }
    }
  }
}
</style>
