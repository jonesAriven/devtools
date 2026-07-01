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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getDocDetail, updateDoc } from '@/api/doc'
import { getFolderTree } from '@/api/folder'
import { formatDate } from '@/utils/format'
import { extractOutline, scrollToHeading } from '@/utils/docOutline'
import type { Doc, Folder, DocFormat, OutlineItem } from '@/types'
import TagInput from '@/components/TagInput.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
})

onBeforeUnmount(() => {
  if (editorInstance.value) {
    editorInstance.value.destroy()
  }
})

// 监听内容变化更新大纲
watch(() => doc.content, () => {
  updateOutline()
})

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
  } catch {
    // 错误已在拦截器中处理
  } finally {
    saving.value = false
  }
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
</style>
