<template>
  <div class="intel-docs">
    <el-card shadow="never">
      <template #header>
        <div class="header-bar">
          <span>文档库</span>
          <div class="header-actions">
            <el-input
              v-model="filter.title"
              placeholder="按标题筛选"
              clearable
              style="width: 200px"
              @keyup.enter="handleSearch"
            />
            <el-select
              v-model="filter.docType"
              placeholder="文档类型"
              clearable
              style="width: 140px"
            >
              <el-option label="TABLE" value="TABLE" />
              <el-option label="PLAN" value="PLAN" />
              <el-option label="TIMELINE" value="TIMELINE" />
              <el-option label="GRAPH" value="GRAPH" />
              <el-option label="RULE" value="RULE" />
              <el-option label="GENERAL" value="GENERAL" />
            </el-select>
            <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" stripe @row-click="handleRowClick">
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button text type="primary" @click.stop="handleRowClick(row)">{{ row.title }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="docType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="docTypeColor(row.docType)">{{ row.docType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120" show-overflow-tooltip />
        <el-table-column prop="entityCount" label="实体数" width="80" align="right" />
        <el-table-column prop="commandCount" label="命令数" width="80" align="right" />
        <el-table-column prop="wordCount" label="字数" width="100" align="right" />
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="filter.page"
          v-model:page-size="filter.size"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadDocs"
          @current-change="loadDocs"
        />
      </div>
    </el-card>

    <el-drawer
      v-model="drawerVisible"
      size="60%"
      :title="currentDoc?.title || '文档详情'"
      destroy-on-close
    >
      <div v-if="currentDoc" class="doc-meta">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="类型">
            <el-tag :type="docTypeColor(currentDoc.docType)" size="small">{{ currentDoc.docType }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="分类">{{ currentDoc.category || '-' }}</el-descriptions-item>
          <el-descriptions-item label="标签" :span="2">
            <el-tag
              v-for="tag in splitTags(currentDoc.tags)"
              :key="tag"
              size="small"
              class="mr-1"
            >{{ tag }}</el-tag>
            <span v-if="!splitTags(currentDoc.tags).length">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="摘要" :span="2">{{ currentDoc.summary || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <el-tabs v-model="activeTab" class="doc-tabs">
        <el-tab-pane label="文档内容" name="content">
          <div v-loading="contentLoading">
            <template v-if="docContent">
              <div v-if="docContent.sections.length">
                <div v-for="(sec, idx) in docContent.sections" :key="idx">
                  <h4 class="section-title">{{ sec.title }}</h4>
                  <pre class="section-content">{{ sec.content }}</pre>
                  <el-divider v-if="idx < docContent.sections.length - 1" />
                </div>
              </div>
              <pre v-else class="section-content">{{ docContent.plainText }}</pre>
            </template>
            <el-empty v-else description="暂无内容" :image-size="60" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="关联实体" name="entities">
          <div v-loading="entitiesLoading">
            <template v-if="docEntities">
              <div class="entity-summary">
                <el-tag type="primary">总实体数: {{ docEntities.totalEntities }}</el-tag>
              </div>

              <el-collapse v-model="activeCollapse">
                <el-collapse-item
                  v-if="docEntities.hosts?.length"
                  :title="`主机（${docEntities.hosts.length}）`"
                  name="hosts"
                >
                  <el-table :data="docEntities.hosts" size="small" stripe>
                    <el-table-column prop="name" label="名称" width="120" />
                    <el-table-column prop="ip" label="IP" width="140" />
                    <el-table-column prop="role" label="角色" width="100" />
                    <el-table-column prop="status" label="状态" width="80" />
                  </el-table>
                </el-collapse-item>

                <el-collapse-item
                  v-if="docEntities.services?.length"
                  :title="`服务（${docEntities.services.length}）`"
                  name="services"
                >
                  <el-table :data="docEntities.services" size="small" stripe>
                    <el-table-column prop="name" label="名称" width="140" />
                    <el-table-column prop="serviceType" label="类型" width="100" />
                    <el-table-column prop="version" label="版本" width="100" />
                    <el-table-column prop="status" label="状态" width="80" />
                  </el-table>
                </el-collapse-item>

                <el-collapse-item
                  v-if="docEntities.ports?.length"
                  :title="`端口（${docEntities.ports.length}）`"
                  name="ports"
                >
                  <el-table :data="docEntities.ports" size="small" stripe>
                    <el-table-column prop="port" label="端口" width="80" />
                    <el-table-column prop="protocol" label="协议" width="80" />
                    <el-table-column prop="accessUrl" label="访问地址" show-overflow-tooltip />
                    <el-table-column prop="exposed" label="暴露" width="80" />
                  </el-table>
                </el-collapse-item>

                <el-collapse-item
                  v-if="docEntities.credentials?.length"
                  :title="`凭据（${docEntities.credentials.length}）`"
                  name="credentials"
                >
                  <el-table :data="docEntities.credentials" size="small" stripe>
                    <el-table-column prop="credType" label="类型" width="100" />
                    <el-table-column prop="username" label="用户名" width="140" />
                    <el-table-column prop="passwordHint" label="密码提示" show-overflow-tooltip />
                  </el-table>
                </el-collapse-item>

                <el-collapse-item
                  v-if="docEntities.commands?.length"
                  :title="`命令（${docEntities.commands.length}）`"
                  name="commands"
                >
                  <el-table :data="docEntities.commands" size="small" stripe>
                    <el-table-column prop="command" label="命令" min-width="200" show-overflow-tooltip />
                    <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
                    <el-table-column prop="category" label="分类" width="100" />
                    <el-table-column prop="riskLevel" label="风险" width="80" />
                  </el-table>
                </el-collapse-item>

                <el-collapse-item
                  v-if="docEntities.timelines?.length"
                  :title="`时间线（${docEntities.timelines.length}）`"
                  name="timelines"
                >
                  <el-table :data="docEntities.timelines" size="small" stripe>
                    <el-table-column prop="eventTime" label="时间" width="160" />
                    <el-table-column prop="title" label="事件" min-width="160" show-overflow-tooltip />
                    <el-table-column prop="severity" label="级别" width="80" />
                    <el-table-column prop="status" label="状态" width="80" />
                  </el-table>
                </el-collapse-item>
              </el-collapse>

              <el-empty
                v-if="docEntities.totalEntities === 0"
                description="暂无关联实体"
                :image-size="60"
              />
            </template>
            <el-empty v-else description="暂无数据" :image-size="60" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getDocList, getDocContent, getDocEntities } from '@/api/intelligence'
import type { IntelDoc, IntelDocContent, IntelDocEntities } from '@/types'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'

const loading = ref(false)
const list = ref<IntelDoc[]>([])
const total = ref(0)
const filter = reactive({
  title: '',
  docType: '',
  page: 1,
  size: 20,
})

const drawerVisible = ref(false)
const activeTab = ref<'content' | 'entities'>('content')
const activeCollapse = ref<string[]>([])
const currentDoc = ref<IntelDoc | null>(null)
const docContent = ref<IntelDocContent | null>(null)
const docEntities = ref<IntelDocEntities | null>(null)
const contentLoading = ref(false)
const entitiesLoading = ref(false)

function docTypeColor(type: string) {
  const map: Record<string, string> = {
    TABLE: 'warning',
    PLAN: 'primary',
    TIMELINE: 'success',
    GRAPH: 'info',
    RULE: 'danger',
    GENERAL: 'info',
  }
  return (map[type] || 'info') as any
}

function splitTags(tags?: string) {
  if (!tags) return []
  return tags
    .split(',')
    .map(t => t.trim())
    .filter(Boolean)
}

async function loadDocs() {
  loading.value = true
  try {
    const res = await getDocList({
      docType: filter.docType || undefined,
      page: filter.page,
      size: filter.size,
    })
    const data = res.data.data
    let rows = data.records
    // API 暂无 title 参数，按标题做客户端筛选
    if (filter.title) {
      const kw = filter.title.toLowerCase()
      rows = rows.filter(d => d.title.toLowerCase().includes(kw))
    }
    list.value = rows
    total.value = filter.title ? rows.length : data.total
    filter.page = data.current
  } catch {
    ElMessage.error('加载文档列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  filter.page = 1
  loadDocs()
}

async function handleRowClick(row: IntelDoc) {
  currentDoc.value = row
  drawerVisible.value = true
  activeTab.value = 'content'
  docContent.value = null
  docEntities.value = null
  await loadDocDetail(row.id)
}

async function loadDocDetail(docId: number) {
  contentLoading.value = true
  entitiesLoading.value = true

  getDocContent(docId)
    .then(res => {
      docContent.value = res.data.data
    })
    .catch(err => {
      const msg = err?.response?.data?.message || err?.message || '未知错误'
      console.error('加载文档内容失败:', err)
      ElMessage.error('加载文档内容失败: ' + msg)
    })
    .finally(() => {
      contentLoading.value = false
    })

  getDocEntities(docId)
    .then(res => {
      docEntities.value = res.data.data
      if (docEntities.value) {
        const expand: string[] = []
        const e = docEntities.value
        if (e.hosts?.length) expand.push('hosts')
        if (e.services?.length) expand.push('services')
        if (e.ports?.length) expand.push('ports')
        if (e.credentials?.length) expand.push('credentials')
        if (e.commands?.length) expand.push('commands')
        if (e.timelines?.length) expand.push('timelines')
        activeCollapse.value = expand
      }
    })
    .catch(err => {
      const msg = err?.response?.data?.message || err?.message || '未知错误'
      console.error('加载关联实体失败:', err)
      ElMessage.error('加载关联实体失败: ' + msg)
    })
    .finally(() => {
      entitiesLoading.value = false
    })
}

onMounted(loadDocs)
</script>

<style scoped>
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.doc-meta {
  margin-bottom: 16px;
}
.doc-tabs {
  margin-top: 8px;
}
.section-title {
  margin: 12px 0 6px;
  font-size: 15px;
  font-weight: 600;
  color: #c9a96e;
}
.section-content {
  margin: 0;
  font-family: 'Menlo', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  color: #303133;
}
.entity-summary {
  margin-bottom: 12px;
}
.mr-1 {
  margin-right: 4px;
}

@media (max-width: 768px) {
  .header-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  .header-actions {
    flex-wrap: wrap;
    width: 100%;
  }
  .intel-docs :deep(.el-table) {
    font-size: 12px;
  }
}
</style>
