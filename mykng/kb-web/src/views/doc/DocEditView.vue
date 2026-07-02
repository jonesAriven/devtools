<template>
  <div class="doc-edit-layout">
    <div class="doc-sidebar">
      <ResourceTree
        ref="resourceTreeRef"
        :space-id="doc.spaceId"
        :space-name="spaceName"
        :current-folder-id="doc.folderId"
        :current-doc-id="Number(id)"
        @select="handleTreeSelect"
        @refresh="handleRefreshTree"
      />
    </div>
    <div class="doc-edit-content">
      <div class="doc-header">
        <el-input
          v-model="doc.title"
          placeholder="请输入笔记标题"
          class="doc-title-input"
          size="large"
        />
        <div class="doc-actions">
          <el-select v-model="doc.format" placeholder="格式" style="width: 120px" size="default" @change="handleFormatChange">
            <el-option label="富文本 HTML" value="html" />
            <el-option label="Markdown" value="markdown" />
          </el-select>
          <el-dropdown trigger="click">
            <el-button size="default">
              <el-icon><MoreFilled /></el-icon>
              <span>更多</span>
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleShare">
                  <el-icon><Share /></el-icon>分享
                </el-dropdown-item>
                <el-dropdown-item divided @click="showVersionDrawer = true; loadVersions()">
                  <el-icon><Clock /></el-icon>历史版本
                </el-dropdown-item>
                <el-dropdown-item divided @click.stop="handleExport('markdown')">
                  <el-icon><Download /></el-icon>导出 Markdown
                </el-dropdown-item>
                <el-dropdown-item @click.stop="handleExport('html')">
                  <el-icon><Download /></el-icon>导出 HTML
                </el-dropdown-item>
                <el-dropdown-item v-if="doc.format === 'markdown'" divided @click="openLinkPicker">
                  <el-icon><Link /></el-icon>插入双向链接
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button type="primary" :loading="saving" size="default" class="save-btn" @click="handleSave">
            <el-icon><Check /></el-icon>
            <span>保存</span>
          </el-button>
        </div>
      </div>

      <div class="doc-body">
        <div class="editor-area">
          <div class="editor-container">
            <!-- Markdown 编辑器 -->
            <MdEditor
              v-if="doc.format === 'markdown'"
              v-model="doc.content"
              :preview="true"
              language="zh-CN"
              :toolbars-exclude="['github', 'save']"
              :markdown-it-config="configMarkdownIt"
              placeholder="开始编写 Markdown 笔记..."
              style="height: 100%"
              @on-click="handlePreviewClick"
            />
            <!-- HTML 富文本编辑器 -->
            <template v-else>
              <div class="toolbar-container" ref="toolbarRef"></div>
              <div class="editor-content" ref="editorRef"></div>
            </template>
          </div>
        </div>
        <div class="info-area">
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
            <div class="card-title">反向链接</div>
            <div v-if="backlinksLoading" class="backlink-loading">加载中...</div>
            <div v-else-if="backlinks.length === 0" class="backlink-empty">暂无反向链接</div>
            <ul v-else class="backlink-list">
              <li
                v-for="bl in backlinks"
                :key="bl.id"
                class="backlink-item"
                @click="goToDoc(bl.id)"
              >
                <div class="backlink-item__title">{{ bl.title }}</div>
                <div class="backlink-item__preview">{{ bl.preview }}</div>
              </li>
            </ul>
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
        </div>
      </div>
    </div>

    <!-- 分享对话框 -->
    <ShareDialog
      v-model:visible="showShareDialog"
      :resource-id="Number(id)"
      resource-type="doc"
    />

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

    <!-- 双向链接文档选择器 -->
    <el-dialog v-model="showLinkPicker" title="选择文档" width="500px">
      <el-input
        v-model="linkPickerKeyword"
        placeholder="搜索文档标题..."
        :prefix-icon="Search"
        clearable
        style="margin-bottom: 12px"
      />
      <div v-if="linkPickerLoading" style="text-align: center; padding: 24px">加载中...</div>
      <div v-else-if="filteredLinkDocs.length === 0" style="text-align: center; padding: 24px; color: #909399">
        无匹配文档
      </div>
      <ul v-else class="link-doc-list">
        <li
          v-for="d in filteredLinkDocs"
          :key="d.id"
          class="link-doc-item"
          @click="insertBiLink(d)"
        >
          <el-icon><Document /></el-icon>
          <span class="link-doc-item__title">{{ d.title }}</span>
          <span class="link-doc-item__id">#{{ d.id }}</span>
        </li>
      </ul>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, shallowRef, watch, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getDocDetail, updateDoc, getDocVersions, getDocList } from '@/api/doc'
import { getFolderTree } from '@/api/folder'
import { getSpaceDetail } from '@/api/space'
import { search as searchApi } from '@/api/search'
import { formatDate } from '@/utils/format'
import { extractOutline, scrollToHeading } from '@/utils/docOutline'
import type { Doc, Folder, DocFormat, OutlineItem, DocVersion } from '@/types'
import TagInput from '@/components/TagInput.vue'
import ResourceTree from '@/components/ResourceTree.vue'
import ShareDialog from '@/components/ShareDialog.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Clock, Link, Search, Document, MoreFilled, Download, Check, Share } from '@element-plus/icons-vue'
import { createEditor, createToolbar } from '@wangeditor/editor'
import type { IDomEditor, IEditorConfig, IToolbarConfig } from '@wangeditor/editor'

import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

const props = defineProps<{
  id: string
}>()

const route = useRoute()
const router = useRouter()
const toolbarRef = ref<HTMLElement>()
const editorRef = ref<HTMLElement>()
const editorInstance = shallowRef<IDomEditor>()
const saving = ref(false)
const folders = ref<Folder[]>([])
const outline = ref<OutlineItem[]>([])
const dirty = ref(false)
const lastSavedTitle = ref('')
const resourceTreeRef = ref()
const spaceName = ref('')
const showShareDialog = ref(false)
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

// 双向链接
const showLinkPicker = ref(false)
const linkPickerKeyword = ref('')
const linkPickerLoading = ref(false)
const allDocs = ref<Doc[]>([])
const filteredLinkDocs = computed(() => {
  const kw = linkPickerKeyword.value.trim().toLowerCase()
  if (!kw) return allDocs.value.slice(0, 20)
  return allDocs.value
    .filter(d => d.id !== Number(props.id))
    .filter(d => (d.title || '').toLowerCase().includes(kw))
    .slice(0, 20)
})

// 反向链接
const backlinks = ref<{ id: number; title: string; preview: string }[]>([])
const backlinksLoading = ref(false)

const doc = reactive<Partial<Doc> & { format: DocFormat }>({
  title: '',
  content: '',
  format: 'html',
  folderId: 0,
  spaceId: 0,
  wordCount: 0,
})

onMounted(async () => {
  // 即使文档加载失败，也要注册定时器和事件监听，确保草稿保护不丢失
  try {
    await loadDoc()
  } catch {
    // 文档加载失败，错误已在拦截器处理
  }
  try {
    await loadFolders()
  } catch {
    // 文件夹加载失败，不影响编辑
  }
  initHtmlEditor()
  updateOutline()
  lastSavedTitle.value = doc.title || ''
  // 加载反向链接
  loadBacklinks()
  // 每 30 秒保存草稿到 localStorage
  draftTimer = setInterval(saveDraft, 30_000)
  // 每 2 分钟自动保存到服务器
  autoSaveTimer = setInterval(autoSave, 120_000)
  // 离开页面前提示
  window.addEventListener('beforeunload', handleBeforeUnload)
  // Ctrl+S 手动保存
  window.addEventListener('keydown', handleEditorKeydown)
})

onBeforeUnmount(() => {
  if (editorInstance.value) {
    editorInstance.value.destroy()
  }
  if (draftTimer) clearInterval(draftTimer)
  if (autoSaveTimer) clearInterval(autoSaveTimer)
  window.removeEventListener('beforeunload', handleBeforeUnload)
  window.removeEventListener('keydown', handleEditorKeydown)
})

// Ctrl+S 手动保存（业界编辑器标配快捷键）
function handleEditorKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault()
    handleSave()
  }
}

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
      savedAt: Date.now(),
    }
    localStorage.setItem(`doc-draft-${props.id}`, JSON.stringify(draft))
  } catch {
    // localStorage 不可用时静默忽略
  }
}

async function checkDraftRecovery() {
  const key = `doc-draft-${props.id}`
  let draft: { title?: string; content?: string; format?: string; folderId?: number; savedAt?: number } | null = null
  try {
    const raw = localStorage.getItem(key)
    if (raw) draft = JSON.parse(raw)
  } catch {
    // 草稿解析失败直接清除
    localStorage.removeItem(key)
    return
  }
  if (!draft || !draft.content) return
  // 比较草稿保存时间与服务端更新时间，判断草稿是否更新
  const serverUpdated = doc.updatedAt ? new Date(doc.updatedAt).getTime() : 0
  const draftSaved = draft.savedAt || 0
  // 草稿早于或等于服务端更新，说明已保存过，清理草稿
  if (draftSaved <= serverUpdated) {
    localStorage.removeItem(key)
    return
  }
  try {
    await ElMessageBox.confirm(
      '检测到未保存的草稿，是否恢复？',
      '草稿恢复',
      { confirmButtonText: '恢复草稿', cancelButtonText: '丢弃草稿', type: 'warning' }
    )
    // 用户选择恢复
    if (draft.title) doc.title = draft.title
    if (typeof draft.content === 'string') doc.content = draft.content
    if (draft.format === 'html' || draft.format === 'markdown') doc.format = draft.format
    if (typeof draft.folderId === 'number') doc.folderId = draft.folderId
    dirty.value = true
    ElMessage.success('已恢复草稿')
  } catch {
    // 用户选择丢弃，清理草稿
    localStorage.removeItem(key)
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
    // 自动保存成功后清理本地草稿
    localStorage.removeItem(`doc-draft-${props.id}`)
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
  // 加载空间名称
  if (doc.spaceId) {
    try {
      const spaceRes = await getSpaceDetail(doc.spaceId)
      spaceName.value = spaceRes.data.data?.name || ''
    } catch {
      // 忽略空间详情加载失败
    }
  }
  // 检查是否有未保存的本地草稿
  await checkDraftRecovery()
}

async function loadFolders() {
  const spaceId = doc.spaceId || Number(route.query.spaceId)
  if (!spaceId) return
  try {
    const res = await getFolderTree(spaceId)
    folders.value = flattenFolders(res.data.data)
  } catch {
    // 文件夹树加载失败，不影响文档编辑
  }
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
    // 保存成功后清理本地草稿
    localStorage.removeItem(`doc-draft-${props.id}`)
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

// ===== 双向链接功能 =====

/**
 * 配置 markdown-it，注册 [[docId|标题]] 双向链接语法解析
 * 预览时将 [[docId|标题]] 渲染为可点击的内部链接
 */
function configMarkdownIt(md: any) {
  // 注册 inline 规则：匹配 [[数字|文本]]
  md.inline.ruler.before('emphasis', 'bi_link', (state: any, silent: boolean) => {
    const src = state.src.slice(state.pos)
    const match = /^\[\[(\d+)\|([^\]]+)\]\]/.exec(src)
    if (!match) return false
    if (!silent) {
      const token = state.push('bi_link', '', 0)
      token.markup = ''
      token.content = match[0]
      token.meta = { docId: match[1], title: match[2] }
    }
    state.pos += match[0].length
    return true
  })
  // 渲染规则
  md.renderer.rules.bi_link = (tokens: any[], idx: number) => {
    const token = tokens[idx]
    const { docId, title } = token.meta
    const escaped = md.utils.escapeHtml(title)
    return `<a class="bi-link" data-doc-id="${docId}" href="#/doc/${docId}" title="跳转到: ${escaped}">${escaped}</a>`
  }
}

/** 打开文档选择器 */
async function openLinkPicker() {
  showLinkPicker.value = true
  linkPickerKeyword.value = ''
  if (allDocs.value.length === 0) {
    linkPickerLoading.value = true
    try {
      const res = await getDocList({ page: 1, size: 100 })
      allDocs.value = res.data.data?.list || []
    } catch {
      // 错误已在拦截器中处理
    } finally {
      linkPickerLoading.value = false
    }
  }
}

/** 插入双向链接到 Markdown 内容 */
function insertBiLink(d: Doc) {
  const link = `[[${d.id}|${d.title}]]`
  // 追加到内容末尾（md-editor-v3 v-model 同步）
  doc.content = (doc.content || '') + '\n' + link
  dirty.value = true
  showLinkPicker.value = false
  ElMessage.success(`已插入双向链接: ${d.title}`)
}

/** 预览区域点击事件，处理双向链接跳转 */
function handlePreviewClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (target.classList?.contains('bi-link')) {
    e.preventDefault()
    const docId = target.getAttribute('data-doc-id')
    if (docId) {
      goToDoc(Number(docId))
    }
  }
}

/** 跳转到文档 */
function goToDoc(docId: number) {
  router.push(`/doc/${docId}`)
}

async function handleTreeSelect(node: any) {
  if (node.type === 'doc') {
    if (dirty.value) {
      const confirm = await ElMessageBox.confirm(
        '当前文档有未保存的更改，切换将丢失内容，是否继续？',
        '提示',
        { type: 'warning', confirmButtonText: '继续切换', cancelButtonText: '取消' }
      ).catch(() => false)
      if (!confirm) return
    }
    if (node.id === Number(props.id)) return
    router.replace(`/doc/${node.id}`)
  }
}

function handleShare() {
  if (dirty.value) {
    ElMessage.warning('请先保存再分享')
    return
  }
  showShareDialog.value = true
}

function handleRefreshTree() {
  resourceTreeRef.value?.loadTree()
}

/** 加载反向链接：搜索引用了当前文档的其他文档 */
async function loadBacklinks() {
  if (!doc.title) return
  backlinksLoading.value = true
  try {
    // 搜索包含当前文档 ID 或标题的文档
    const res = await searchApi({ keyword: String(props.id), type: 'doc', size: 10 })
    const results = res.data.data?.list || []
    // 过滤掉自身，构建预览
    backlinks.value = results
      .filter((r: any) => r.id !== Number(props.id))
      .map((r: any) => ({
        id: r.id,
        title: r.title || r.name || '未命名',
        preview: (r.highlight || r.content || '').replace(/<[^>]+>/g, '').slice(0, 80),
      }))
  } catch {
    // 搜索失败不阻塞编辑
  } finally {
    backlinksLoading.value = false
  }
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
.doc-edit-layout {
  display: flex;
  height: 100%;
  background-color: #faf8f5;
}

.doc-sidebar {
  width: 260px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #ebeef5;
  padding: 16px 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.doc-edit-content {
  flex: 1;
  min-width: 0;
  padding: 16px 20px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.doc-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.doc-body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 16px;
}

.editor-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.info-area {
  width: 280px;
  flex-shrink: 0;
  overflow-y: auto;
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
  align-items: center;
}

.save-btn {
  font-weight: 600;
  padding-left: 16px;
  padding-right: 16px;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.3);
}

.editor-container {
  flex: 1;
  min-height: 0;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
  background-color: #fff;
  display: flex;
  flex-direction: column;

  .toolbar-container {
    border-bottom: 1px solid #dcdfe6;
    flex-shrink: 0;
  }

  .editor-content {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
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

@media (max-width: 768px) {
  .doc-edit-layout {
    flex-direction: column;

    .doc-sidebar {
      width: 100%;
      height: auto;
      max-height: 50%;
      border-right: none;
      border-bottom: 1px solid #ebeef5;
    }
  }

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

// 双向链接文档选择器
.link-doc-list {
  list-style: none;
  padding: 0;
  margin: 0;
  max-height: 360px;
  overflow-y: auto;

  .link-doc-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    border-radius: 4px;
    cursor: pointer;
    transition: background-color 0.2s;

    &:hover {
      background-color: #f5f7fa;
    }

    &__title {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: #303133;
    }

    &__id {
      font-size: 12px;
      color: #c0c4cc;
    }
  }
}

// 反向链接面板
.backlink-loading, .backlink-empty {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding: 12px 0;
}

.backlink-list {
  list-style: none;
  padding: 0;
  margin: 0;

  .backlink-item {
    padding: 8px;
    border-radius: 4px;
    cursor: pointer;
    transition: background-color 0.2s;
    margin-bottom: 4px;

    &:hover {
      background-color: #f5f7fa;
    }

    &__title {
      font-size: 13px;
      font-weight: 500;
      color: #409eff;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__preview {
      font-size: 12px;
      color: #909399;
      margin-top: 4px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}
</style>
