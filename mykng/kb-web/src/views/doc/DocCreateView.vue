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
        <el-button type="primary" :loading="saving" @click="handleCreate">创建</el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="18">
        <div class="editor-container">
          <div class="toolbar-container" ref="toolbarRef"></div>
          <div class="editor-content" ref="editorRef"></div>
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
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createDoc } from '@/api/doc'
import { getFolderTree } from '@/api/folder'
import type { Folder } from '@/types'
import { ElMessage } from 'element-plus'
import { createEditor, createToolbar } from '@wangeditor/editor'
import type { IDomEditor, IEditorConfig, IToolbarConfig } from '@wangeditor/editor'
import '@wangeditor/editor/dist/css/style.css'

const route = useRoute()
const router = useRouter()
const toolbarRef = ref<HTMLElement>()
const editorRef = ref<HTMLElement>()
const editorInstance = shallowRef<IDomEditor>()
const saving = ref(false)
const folders = ref<Folder[]>([])

const doc = reactive({
  title: '',
  content: '',
  folderId: 0,
  spaceId: 0,
})

onMounted(async () => {
  doc.spaceId = Number(route.query.spaceId) || 0
  doc.folderId = Number(route.query.folderId) || 0
  await loadFolders()
  initEditor()
})

onBeforeUnmount(() => {
  if (editorInstance.value) {
    editorInstance.value.destroy()
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

function initEditor() {
  if (!toolbarRef.value || !editorRef.value) return

  const editorConfig: Partial<IEditorConfig> = {
    placeholder: '开始编写笔记...',
    onChange(editor: IDomEditor) {
      doc.content = editor.getHtml()
    },
  }

  const editor = createEditor({
    selector: editorRef.value,
    html: '<p></p>',
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
    const res = await createDoc({
      title: doc.title,
      content: doc.content,
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
  }

  .doc-title-input {
    flex: 1;

    :deep(.el-input__inner) {
      font-size: 20px;
      font-weight: 600;
    }
  }

  .doc-actions {
    flex-shrink: 0;
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
