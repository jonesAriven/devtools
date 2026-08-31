<template>
  <el-card>
    <div class="bar">
      <h3>业务词库</h3>
      <div class="bar-actions">
        <el-input v-model="q" placeholder="搜索术语" style="width:200px" clearable
                  aria-label="搜索术语" @keyup.enter="reload" @clear="reload" />
        <el-select v-model="categoryId" placeholder="全部分类" style="width:170px" clearable
                   aria-label="按分类筛选" @change="reload">
          <el-option v-for="c in categories" :key="c.id" :label="`${c.name} (${c.term_count})`"
                     :value="c.id" />
        </el-select>
        <el-button v-if="isAdmin()" type="primary" :loading="mining" @click="runMine">
          <el-icon style="margin-right:4px"><MagicStick /></el-icon>立即挖掘
        </el-button>
      </div>
    </div>

    <!-- 词库体检：此前词库是张「死表」，这几个指标能直接看出挖掘有没有生效 -->
    <div class="stat-row">
      <div class="stat"><span class="k">词条总数</span><span class="v">{{ stats.total ?? '—' }}</span></div>
      <div class="stat"><span class="k">待审候选</span><span class="v" style="color:var(--c-warning)">{{ stats.by_status?.candidate ?? '—' }}</span></div>
      <div class="stat"><span class="k">零频次词</span><span class="v" style="color:var(--c-text-3)">{{ stats.zero_freq ?? '—' }}</span></div>
      <div class="stat"><span class="k">上次挖掘</span><span class="v" style="font-size:var(--fs-md)">{{ minedAt || '从未' }}</span></div>
    </div>

    <el-alert v-if="mineReport" closable type="success" style="margin-bottom:var(--sp-3)"
              :title="`挖掘完成：扫描 ${mineReport.scanned} 个术语，新增候选 ${mineReport.new} 条，刷新词频 ${mineReport.updated} 条，回灌字段池 ${mineReport.pools} 组`"
              @close="mineReport = null" />

    <el-tabs v-model="status" @tab-change="reload">
      <el-tab-pane label="已确认" name="confirmed" />
      <el-tab-pane :label="`待审候选 (${stats.by_status?.candidate ?? 0})`" name="candidate" />
      <el-tab-pane label="已驳回" name="rejected" />
    </el-tabs>

    <el-table :data="list" v-loading="loading" size="small" row-key="id"
              @selection-change="sel = $event">
      <el-table-column v-if="status === 'candidate'" type="selection" width="46" />
      <el-table-column prop="term" label="术语" min-width="220" show-overflow-tooltip />
      <el-table-column prop="frequency" label="频次" width="90" sortable align="right" />
      <el-table-column prop="category_name" label="分类" width="150" show-overflow-tooltip />
      <el-table-column prop="source" label="来源" width="100">
        <template #default="s">
          <el-tag size="small" effect="plain"
                  :type="s.row.source === 'mined' ? 'warning' : 'info'">{{ s.row.source }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center">
        <template #default="s">
          <template v-if="status === 'candidate'">
            <el-button size="small" type="success" link @click="act([s.row.id], 'confirm')">确认</el-button>
            <el-button size="small" type="danger" link @click="act([s.row.id], 'reject')">驳回</el-button>
          </template>
          <el-button v-else-if="status === 'rejected'" size="small" type="primary" link
                     @click="restore(s.row.id)">恢复候选</el-button>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty :description="error || (status === 'candidate'
          ? '暂无候选词，点「立即挖掘」从编写库 / 归档库回采术语'
          : '没有匹配的术语')" />
      </template>
    </el-table>

    <div class="bar" style="margin-top:var(--sp-3)" v-if="status === 'candidate' && sel.length">
      <span class="muted">已选 {{ sel.length }} 条</span>
      <div class="bar-actions">
        <el-button size="small" type="success" :loading="acting"
                   @click="act(sel.map(r => r.id), 'confirm')">批量确认</el-button>
        <el-button size="small" type="danger" :loading="acting"
                   @click="act(sel.map(r => r.id), 'reject')">批量驳回</el-button>
      </div>
    </div>

    <div class="pager">
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
                     :total="total" :page-sizes="PAGER_SIZES" :layout="PAGER_LAYOUT"
                     :disabled="loading" background />
    </div>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api, { isAdmin } from '../api'
import { PAGER_LAYOUT, PAGER_SIZES, usePaged } from '../composables/usePaged'

const q = ref('')
const status = ref('confirmed')
const categoryId = ref(null)
const categories = ref([])
const stats = ref({})
const sel = ref([])
const mining = ref(false)
const acting = ref(false)
const mineReport = ref(null)

// 服务端分页。此前这里是 limit:100 硬编码，后端只有 LIMIT 没有 offset，
// 6379 条词永远只能看到第一页。
const {
  list, total, page, pageSize, loading, error, reset,
} = usePaged(
  p => api.get('/studio/vocab', {
    params: { ...p, q: q.value, status: status.value, category_id: categoryId.value || undefined },
  }),
  { pageSize: 20, immediate: true }
)

const minedAt = computed(() => {
  const t = stats.value.last_mined_at
  return t ? new Date(t).toLocaleString('zh-CN', { hour12: false }) : ''
})

function reload() { sel.value = []; reset(); loadStats() }

async function loadStats() {
  try {
    const [s, c] = await Promise.all([
      api.get('/studio/vocab/stats'),
      api.get('/studio/vocab/categories'),
    ])
    stats.value = s.data
    categories.value = c.data
  } catch { /* 统计拉不到不影响主表 */ }
}

async function runMine() {
  mining.value = true
  try {
    mineReport.value = (await api.post('/studio/vocab/mine')).data
    ElMessage.success('挖掘完成')
    reload()
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || '挖掘失败')
  } finally { mining.value = false }
}

async function act(ids, kind) {
  if (!ids?.length) return
  acting.value = true
  try {
    const ep = kind === 'confirm' ? '/studio/vocab/confirm' : '/studio/vocab/reject'
    const { data } = await api.post(ep, { ids })
    ElMessage.success(kind === 'confirm' ? `已确认 ${data.confirmed} 条` : `已驳回 ${data.rejected} 条`)
    sel.value = []
    reload()
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || '操作失败')
  } finally { acting.value = false }
}

async function restore(id) {
  try {
    await api.post(`/studio/vocab/${id}/status?status=candidate`)
    ElMessage.success('已恢复为候选')
    reload()
  } catch (e) { ElMessage.error(e.response?.data?.detail || '操作失败') }
}

onMounted(loadStats)
</script>
