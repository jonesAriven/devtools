<template>
  <div class="intelligence-doc-detail" v-loading="loading">
    <!-- 顶部标题栏 -->
    <el-card shadow="never" class="header-card">
      <div class="header-row">
        <div class="header-left">
          <el-button text @click="goBack"><el-icon><ArrowLeft /></el-icon> 返回</el-button>
          <span class="doc-title">{{ meta?.title || '加载中...' }}</span>
          <el-tag v-if="meta" size="small" :type="docTypeTag(meta.docType)">{{ docTypeLabel(meta.docType) }}</el-tag>
          <el-tag v-if="meta?.category" size="small" type="info">{{ meta.category }}</el-tag>
        </div>
        <div class="header-right" v-if="meta">
          <span class="meta-item">ID: {{ meta.id }}</span>
          <span class="meta-item">字数: {{ meta.wordCount }}</span>
          <span class="meta-item">章节: {{ meta.sectionCount }}</span>
        </div>
      </div>
    </el-card>

    <el-row :gutter="12" class="mt-3">
      <!-- 左侧：实体列表 -->
      <el-col :xs="24" :md="8">
        <el-card shadow="hover" class="entities-card">
          <template #header><span>文档实体</span></template>
          <el-tabs v-if="entities">
            <el-tab-pane :label="`主机 (${entities.hosts?.length || 0})`">
              <el-table :data="entities.hosts || []" size="small" stripe>
                <el-table-column prop="name" label="名称" show-overflow-tooltip />
                <el-table-column prop="ip" label="IP" width="120" show-overflow-tooltip />
                <el-table-column prop="role" label="角色" show-overflow-tooltip />
              </el-table>
            </el-tab-pane>
            <el-tab-pane :label="`服务 (${entities.services?.length || 0})`">
              <el-empty v-if="!entities.services?.length" description="无服务" :image-size="50" />
              <el-table v-else :data="entities.services" size="small" stripe>
                <el-table-column prop="name" label="名称" show-overflow-tooltip />
                <el-table-column prop="port" label="端口" width="80" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane :label="`凭据 (${entities.credentials?.length || 0})`">
              <el-table :data="entities.credentials || []" size="small" stripe>
                <el-table-column prop="credType" label="类型" width="80" />
                <el-table-column prop="username" label="用户名" />
                <el-table-column prop="passwordHint" label="密码提示" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane :label="`端口 (${entities.ports?.length || 0})`">
              <el-empty v-if="!entities.ports?.length" description="无端口" :image-size="50" />
            </el-tab-pane>
          </el-tabs>
        </el-card>

        <!-- 元数据 -->
        <el-card v-if="meta" shadow="hover" class="mt-3">
          <template #header><span>元数据</span></template>
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="sourceId">{{ meta.sourceId }}</el-descriptions-item>
            <el-descriptions-item label="filePath">{{ meta.filePath }}</el-descriptions-item>
            <el-descriptions-item label="tags">{{ meta.tags || '-' }}</el-descriptions-item>
            <el-descriptions-item label="实体数">{{ meta.entityCount }}</el-descriptions-item>
            <el-descriptions-item label="命令数">{{ meta.commandCount }}</el-descriptions-item>
            <el-descriptions-item label="导入时间">{{ meta.createdAt }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <!-- 右侧：文档内容（markdown 原文） -->
      <el-col :xs="24" :md="16">
        <el-card shadow="hover" class="content-card">
          <template #header>
            <div class="card-header">
              <span>文档内容（Markdown 原文）</span>
              <el-button text size="small" @click="copyContent">复制</el-button>
            </div>
          </template>
          <!--
            说明：用户偏好"复用 wangeditor"，但 wangeditor 是富文本编辑器，
            接受 HTML 输入，对 markdown 原文不会渲染语法（标题/加粗/代码块都不生效），
            等同于 <pre>。这里直接用 <pre> + CSS 美化，更轻量。
            如需真渲染 markdown，建议后续引入 marked + DOMPurify。
          -->
          <pre v-if="content?.plainText" class="doc-content">{{ content.plainText }}</pre>
          <el-empty v-else description="无内容" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getDocMeta, getDocEntities, getDocContent } from '@/api/intelligence'
import type { KnDoc, KnDocEntities, KnDocContent, KnDocType } from '@/types/intelligence'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const meta = ref<KnDoc | null>(null)
const entities = ref<KnDocEntities | null>(null)
const content = ref<KnDocContent | null>(null)

const DOC_TYPE_LABELS: Record<string, string> = {
  TABLE: '表格', PLAN: '方案', TIMELINE: '时间线', GRAPH: '图谱', RULE: '规则', GENERAL: '通用',
}
const DOC_TYPE_TAGS: Record<string, '' | 'success' | 'warning' | 'info' | 'danger'> = {
  TABLE: 'info', PLAN: 'success', TIMELINE: 'warning', GRAPH: 'danger', RULE: '', GENERAL: 'info',
}
function docTypeLabel(t: string) { return DOC_TYPE_LABELS[t] || t }
function docTypeTag(t: string) { return DOC_TYPE_TAGS[t] || '' }

function getDocId(): number {
  return Number(route.params.id)
}

async function loadDetail() {
  const id = getDocId()
  if (!id) return
  loading.value = true
  try {
    const [metaRes, entitiesRes, contentRes] = await Promise.all([
      getDocMeta(id),
      getDocEntities(id),
      getDocContent(id),
    ])
    meta.value = metaRes.data.data
    entities.value = entitiesRes.data.data
    content.value = contentRes.data.data
  } catch (e) {
    console.error(e)
    ElMessage.error('加载文档详情失败')
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/intelligence/docs')
}

async function copyContent() {
  if (!content.value?.plainText) return
  try {
    await navigator.clipboard.writeText(content.value.plainText)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动选择文本')
  }
}

onMounted(loadDetail)
watch(() => route.params.id, loadDetail)
</script>

<style scoped lang="scss">
.intelligence-doc-detail { padding: 16px; }
.header-card {
  :deep(.el-card__body) { padding: 12px 16px; }
}
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.header-right {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #909399;
}
.doc-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.meta-item { white-space: nowrap; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.doc-content {
  margin: 0;
  padding: 12px;
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: calc(100vh - 220px);
  overflow-y: auto;
}
.mt-3 { margin-top: 12px; }
</style>
