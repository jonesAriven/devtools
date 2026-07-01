<template>
  <div class="doc-create-page">
    <div class="doc-header">
      <el-input
        v-model="doc.title"
        placeholder="请输入笔记标题"
        class="doc-title-input"
        size="large"
      />
      <div class="doc-actions">
        <el-select v-model="doc.format" placeholder="格式" style="width: 130px" size="large">
          <el-option label="富文本 HTML" value="html" />
          <el-option label="Markdown" value="markdown" />
        </el-select>
        <el-select v-model="selectedTemplate" placeholder="选择模板" style="width: 160px" size="large" @change="handleTemplateChange">
          <el-option
            v-for="tpl in DOC_TEMPLATES"
            :key="tpl.key"
            :label="tpl.label"
            :value="tpl.key"
          />
        </el-select>
        <el-button type="primary" :loading="saving" @click="handleCreate">创建</el-button>
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
          <div class="card-title">模板说明</div>
          <div class="template-desc">
            <el-tag size="small">{{ currentTemplateLabel }}</el-tag>
            <div class="desc-text">{{ currentTemplateDesc }}</div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, shallowRef, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createDoc } from '@/api/doc'
import { getFolderTree } from '@/api/folder'
import type { Folder, DocFormat } from '@/types'
import { ElMessage } from 'element-plus'
import { createEditor, createToolbar } from '@wangeditor/editor'
import type { IDomEditor, IEditorConfig, IToolbarConfig } from '@wangeditor/editor'
import '@wangeditor/editor/dist/css/style.css'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { DOC_TEMPLATES, getTemplateContent } from '@/utils/docTemplates'

const route = useRoute()
const router = useRouter()
const toolbarRef = ref<HTMLElement>()
const editorRef = ref<HTMLElement>()
const editorInstance = shallowRef<IDomEditor>()
const saving = ref(false)
const folders = ref<Folder[]>([])
const selectedTemplate = ref('blank')

const doc = reactive({
  title: '',
  content: '',
  format: 'html' as DocFormat,
  folderId: 0,
  spaceId: 0,
})

const currentTemplateLabel = computed(() => {
  return DOC_TEMPLATES.find((t) => t.key === selectedTemplate.value)?.label || ''
})
const currentTemplateDesc = computed(() => {
  return DOC_TEMPLATES.find((t) => t.key === selectedTemplate.value)?.description || ''
})

onMounted(async () => {
  doc.spaceId = Number(route.query.spaceId) || 0
  doc.folderId = Number(route.query.folderId) || 0
  await loadFolders()
  // 初始化默认模板内容
  doc.content = getTemplateContent(doc.format, selectedTemplate.value)
  initHtmlEditor()
})

onBeforeUnmount(() => {
  if (editorInstance.value) {
    editorInstance.value.destroy()
  }
})

// 切换格式时，重置编辑器内容并切换显示
watch(() => doc.format, (newFormat, oldFormat) => {
  // 切换格式：用当前模板内容覆盖（保留已编辑内容体验差，按模板填充更直观）
  // 仅在用户未输入内容时切换，避免覆盖用户已输入
  if (!doc.content || doc.content === '<p></p>' || doc.content === '') {
    doc.content = getTemplateContent(newFormat, selectedTemplate.value)
  } else if (oldFormat) {
    // 用户已输入内容，保留但需要提示切换格式可能导致显示异常
    ElMessage.info(`已切换到 ${newFormat === 'markdown' ? 'Markdown' : '富文本'} 模式，已编辑内容已保留`)
  }
  // HTML 编辑器需要重新初始化（v-if 切换会重建 DOM）
  if (newFormat === 'html') {
    // 等待 DOM 更新后初始化 wangEditor
    setTimeout(() => initHtmlEditor(), 50)
  }
})

async function loadFolders() {
  if (!doc.spaceId) return
  const res = await getFolderTree(doc.spaceId)
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

  // 已存在则先销毁
  if (editorInstance.value) {
    editorInstance.value.destroy()
    editorInstance.value = undefined as any
  }

  const editorConfig: Partial<IEditorConfig> = {
    placeholder: '开始编写笔记...',
    onChange(editor: IDomEditor) {
      doc.content = editor.getHtml()
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

function handleTemplateChange(key: string) {
  // 切换模板：用模板内容覆盖
  doc.content = getTemplateContent(doc.format, key)
  if (doc.format === 'html') {
    // 同步到 wangEditor
    if (editorInstance.value) {
      editorInstance.value.setHtml(doc.content)
    }
  }
}

async function handleCreate() {
  if (!doc.title) {
    ElMessage.warning('请输入笔记标题')
    return
  }
  if (!doc.spaceId) {
    ElMessage.warning('请选择空间')
    return
  }
  saving.value = true
  try {
    // 切换到 HTML 模式后 wangEditor 已销毁，从 doc.content 直接取
    let content = doc.content
    // Markdown 模式下编辑器 v-model 已同步到 doc.content
    if (doc.format === 'html' && editorInstance.value) {
      content = editorInstance.value.getHtml()
    }
    const res = await createDoc({
      title: doc.title,
      content: content,
      format: doc.format,
      folderId: doc.folderId,
      spaceId: doc.spaceId,
    })
    ElMessage.success('创建成功')
    router.push(`/doc/${res.data.data.id}`)
  } catch {
    // 错误已在拦截器中处理
  } finally {
    saving.value = false
  }
}
</script>

<style scoped lang="scss">
.doc-create-page {
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
    flex-wrap: wrap;
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

  .template-desc {
    display: flex;
    flex-direction: column;
    gap: 8px;

    .desc-text {
      font-size: 13px;
      color: #909399;
      line-height: 1.5;
    }
  }
}

@media (max-width: 768px) {
  .doc-create-page {
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
