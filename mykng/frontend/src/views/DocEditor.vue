<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Star, Trash2 } from 'lucide-vue-next'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { docApi } from '@/api/knowledge'
import type { Doc } from '@/types/api'

const route = useRoute()
const router = useRouter()
const docId = Number(route.params.id)

const loading = ref(false)
const title = ref('')
const content = ref('')
const starred = ref(false)
const saveTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const saving = ref(false)
const saved = ref(true)

async function loadDoc() {
  loading.value = true
  try {
    const doc = (await docApi.get(docId)) as Doc
    title.value = doc.title
    content.value = doc.content
    starred.value = doc.starred === 1
  } finally {
    loading.value = false
  }
}

function scheduleSave() {
  saved.value = false
  if (saveTimer.value) clearTimeout(saveTimer.value)
  saveTimer.value = setTimeout(doSave, 2000)
}

async function doSave() {
  if (!title.value.trim()) return
  saving.value = true
  try {
    await docApi.update(docId, { title: title.value, content: content.value })
    saved.value = true
  } finally {
    saving.value = false
  }
}

async function toggleStar() {
  await docApi.star(docId)
  starred.value = !starred.value
  ElMessage.success(starred.value ? '已收藏' : '已取消收藏')
}

async function handleDelete() {
  await ElMessageBox.confirm('确认删除该文档？该操作不可恢复', '删除确认', { type: 'warning' })
  await doSave()
  await docApi.delete(docId)
  ElMessage.success('删除成功')
  router.back()
}

watch(content, scheduleSave)
watch(title, scheduleSave)

onBeforeUnmount(() => {
  if (saveTimer.value) {
    clearTimeout(saveTimer.value)
    if (!saved.value && title.value.trim()) doSave()
  }
})

onMounted(loadDoc)
</script>

<template>
  <div class="doc-editor" v-loading="loading">
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button :icon="ArrowLeft" text @click="router.back()">返回</el-button>
        <el-input v-model="title" class="title-input" placeholder="请输入标题" maxlength="100" />
      </div>
      <div class="toolbar-right">
        <span class="save-status">
          {{ saving ? '保存中...' : saved ? '已保存' : '未保存' }}
        </span>
        <el-button
          :type="starred ? 'warning' : 'default'"
          :icon="Star"
          @click="toggleStar"
        >{{ starred ? '已收藏' : '收藏' }}</el-button>
        <el-button type="danger" :icon="Trash2" @click="handleDelete">删除</el-button>
      </div>
    </div>

    <MdEditor v-model="content" class="editor" :preview="false" :toolbarsExclude="['github']" />
  </div>
</template>

<style scoped lang="scss">
.doc-editor {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 56px - 48px);
  margin: -24px;
  background: #fff;
}

.toolbar {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.title-input {
  max-width: 500px;

  :deep(.el-input__wrapper) {
    box-shadow: none;
    border-bottom: 1px solid transparent;

    &:hover {
      border-bottom-color: #d4a574;
    }
    &.is-focus {
      border-bottom-color: #d4a574;
    }
  }

  :deep(.el-input__inner) {
    font-size: 18px;
    font-weight: 600;
    color: #2c3e50;
  }
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.save-status {
  font-size: 12px;
  color: #95a5a6;
  min-width: 60px;
}

.editor {
  flex: 1;
  height: 100% !important;

  :deep(.md-editor) {
    height: 100% !important;
    border: none;
  }
}
</style>
