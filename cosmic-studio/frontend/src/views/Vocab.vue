<template>
  <el-card>
    <div class="bar">
      <h3>业务词库</h3>
      <div class="bar-actions">
        <el-input v-model="q" placeholder="搜索术语" style="width:200px" clearable
                  aria-label="搜索术语" @keyup.enter="reload" @clear="reload" />
        <el-button @click="reload" aria-label="查询">
          <el-icon style="margin-right:4px"><Search /></el-icon>查询
        </el-button>
        <el-select v-model="categoryId" placeholder="全部分类" style="width:170px" clearable
                   aria-label="按分类筛选" @change="reload">
          <el-option v-for="c in categories" :key="c.id" :label="`${c.name} (${c.term_count})`"
                     :value="c.id" />
        </el-select>
        <el-button v-if="isAdmin()" type="primary" :loading="mining" @click="runMine">
          <el-icon style="margin-right:4px"><MagicStick /></el-icon>立即挖掘
        </el-button>
        <el-button v-if="canEdit()" @click="showImport = true">
          <el-icon style="margin-right:4px"><Upload /></el-icon>批量导入
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

    <!-- 选择工具栏：纯选择，不执行操作 -->
    <div class="bar" style="margin:var(--sp-3) 0">
      <span class="muted">{{ selectAllMatched ? '已全选' : '本页已选' }} {{ selectAllMatched ? total : sel.length }} 条</span>
      <div class="bar-actions">
        <el-button size="small" @click="selectPageAll">
          <el-icon style="margin-right:4px"><Check /></el-icon>全选本页
        </el-button>
        <el-button size="small" @click="invertPage">
          <el-icon style="margin-right:4px"><RefreshLeft /></el-icon>反选
        </el-button>
        <el-button size="small" @click="clearSel">
          <el-icon style="margin-right:4px"><Close /></el-icon>取消选择
        </el-button>
        <el-button size="small" :type="selectAllMatched ? 'primary' : 'info'"
                   :plain="!selectAllMatched" @click="toggleSelectAllMatched">
          <el-icon style="margin-right:4px"><Grid /></el-icon>{{ selectAllMatched ? '取消全选匹配' : '全选所有匹配' }} ({{ total }})
        </el-button>

        <!-- 操作区：仅在有选中项（本页勾选 或 跨页全选）时显示，与选择功能独立 -->
        <template v-if="sel.length || selectAllMatched">
          <el-divider direction="vertical" />
          <template v-if="status === 'candidate'">
            <el-button size="small" type="success" :loading="acting"
                       @click="doAct('confirm')">批量确认</el-button>
            <el-button size="small" type="danger" :loading="acting"
                       @click="doAct('reject')">批量驳回</el-button>
          </template>
          <el-button size="small" type="danger" :loading="deleting" @click="doDelete">批量删除</el-button>
        </template>
      </div>
    </div>

    <el-table ref="tableRef" :data="list" v-loading="loading" size="small" row-key="id"
              @selection-change="sel = $event">
      <el-table-column type="selection" width="46" />
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

    <div class="pager">
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
                     :total="total" :page-sizes="PAGER_SIZES" :layout="PAGER_LAYOUT"
                     :disabled="loading" background />
    </div>

    <!-- 批量导入对话框 -->
    <el-dialog v-model="showImport" title="批量导入术语" width="560px" @closed="importReport = null">
      <el-form label-width="80px">
        <el-form-item label="目标分类">
          <el-select v-model="importCat" placeholder="默认 原子业务词元" clearable style="width:100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <div class="muted" style="font-size:12px;margin-top:4px">
            每行一词；可选「词,分类名」或「词,分类ID」指定分类，留空用上方默认。
          </div>
        </el-form-item>
        <el-form-item label="术语列表">
          <el-input v-model="importText" type="textarea" :rows="8"
                    placeholder="已订购业务套餐&#10;展示时段,结构参考&#10;资费标签" />
        </el-form-item>
        <el-form-item label="CSV 文件">
          <el-button @click="fileInput?.click()">选择文件</el-button>
          <input ref="fileInput" type="file" accept=".csv,.txt" style="display:none" @change="onCsvFile" />
          <span class="muted" style="font-size:12px;margin-left:8px">上传会把内容填入上方文本框，可编辑后导入</span>
        </el-form-item>
      </el-form>
      <el-alert v-if="importReport" type="success" :closable="false"
                :title="`导入 ${importReport.imported} 条，跳过重复 ${importReport.skipped} 条，共处理 ${importReport.total} 条`" />
      <el-alert v-if="importReport && importReport.errors && importReport.errors.length" type="warning"
                :closable="false" style="margin-top:8px"
                :title="`${importReport.errors.length} 条解析/入库失败（未计入导入数）`">
        <div v-for="(er, i) in importReport.errors.slice(0, 20)" :key="i" style="font-size:12px; line-height:1.6">{{ er }}</div>
        <div v-if="importReport.errors.length > 20" class="muted">…其余 {{ importReport.errors.length - 20 }} 条已省略</div>
      </el-alert>
      <template #footer>
        <el-button @click="showImport = false">关闭</el-button>
        <el-button type="primary" :loading="importing" @click="doImport">导入</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onActivated, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api, { isAdmin, role, batchDeleteByFilter, batchConfirmByFilter, batchRejectByFilter } from '../api'
import { PAGER_LAYOUT, PAGER_SIZES, usePaged } from '../composables/usePaged'
import { usePersistentState } from '../composables/usePersistentState'

const q = usePersistentState('q', '')
const status = usePersistentState('status', 'confirmed')
const categoryId = usePersistentState('categoryId', null)
const categories = ref([])
const stats = ref({})
const sel = ref([])
const mining = ref(false)
const acting = ref(false)
const deleting = ref(false)
const mineReport = ref(null)
const canEdit = () => ['admin', 'editor'].includes(role())

// 批量导入态
const showImport = ref(false)
const importText = ref('')
const importCat = ref(null)
const importing = ref(false)
const importReport = ref(null)
const fileInput = ref(null)

// 跨页/整页选择控制
const tableRef = ref(null)
const selectAllMatched = ref(false)  // 跨页全选标记（纯选择，不执行操作）
// 注意：deleting ref 已在上方声明（用于本页批量删除），跨页操作复用同一个

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

function reload() {
  sel.value = []
  selectAllMatched.value = false  // 切 tab/查询时清掉跨页全选标记，避免误作用到新筛选条件
  reset()
  loadStats()
}

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

function selectPageAll() {
  selectAllMatched.value = false
  const t = tableRef.value
  if (!t) return
  t.clearSelection()
  list.value.forEach(r => t.toggleRowSelection(r, true))
}

function clearSel() {
  selectAllMatched.value = false
  tableRef.value?.clearSelection()
}

function invertPage() {
  selectAllMatched.value = false
  const t = tableRef.value
  if (!t) return
  const selIds = new Set(sel.value.map(r => r.id))
  const toSelect = list.value.filter(r => !selIds.has(r.id))
  t.clearSelection()
  toSelect.forEach(r => t.toggleRowSelection(r, true))
}

function toggleSelectAllMatched() {
  if (selectAllMatched.value) {
    // 取消跨页全选 → 回到本页选择模式
    selectAllMatched.value = false
    tableRef.value?.clearSelection()
  } else {
    // 跨页全选：清空本页勾选，设标记，操作时走筛选条件
    tableRef.value?.clearSelection()
    selectAllMatched.value = true
  }
}

/** 构建当前筛选条件的描述（用于确认弹窗） */
function filterScopeDesc() {
  const parts = []
  if (q.value) parts.push(`搜索「${q.value}」`)
  if (status.value) parts.push(`状态=${status.value}`)
  if (categoryId.value) {
    const c = categories.value.find(x => x.id === categoryId.value)
    parts.push(`分类=${c ? c.name : categoryId.value}`)
  }
  return parts.length ? parts.join('，') : '全部'
}

/** 统一操作入口：根据 selectAllMatched 决定走 ID 列表还是筛选条件 */
async function doAct(kind) {
  // kind: 'confirm' | 'reject'
  if (selectAllMatched.value && !sel.value.length) {
    const scope = filterScopeDesc()
    try {
      await ElMessageBox.confirm(
        `将${kind === 'confirm' ? '确认' : '驳回'}当前筛选（${scope}）下的全部 ${total.value} 条术语？`,
        `批量${kind === 'confirm' ? '确认' : '驳回'}（全选匹配）`, { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
    } catch { return }
    acting.value = true
    try {
      const fn = kind === 'confirm' ? batchConfirmByFilter : batchRejectByFilter
      const { data } = await fn({
        q: q.value || undefined,
        status: status.value || undefined,
        category_id: categoryId.value || undefined,
      })
      ElMessage.success(`${kind === 'confirm' ? '已确认' : '已驳回'} ${data[kind === 'confirm' ? 'confirmed' : 'rejected']} 条`)
      selectAllMatched.value = false
      reload()
    } catch (e) {
      ElMessage.error(e.response?.data?.detail || '操作失败')
    } finally { acting.value = false }
  } else {
    // 本页选中项操作（原有逻辑）
    const ids = sel.value.map(r => r.id)
    if (!ids.length) return
    await act(ids, kind)
  }
}

async function doDelete() {
  if (selectAllMatched.value && !sel.value.length) {
    const scope = filterScopeDesc()
    try {
      await ElMessageBox.confirm(
        `将永久删除当前筛选（${scope}）下的全部 ${total.value} 条匹配术语？此操作不可恢复。`,
        '批量删除（全选匹配）', { type: 'warning', confirmButtonText: '删除全部', cancelButtonText: '取消' })
    } catch { return }
    deleting.value = true
    try {
      const { data } = await batchDeleteByFilter({
        q: q.value || undefined,
        status: status.value || undefined,
        category_id: categoryId.value || undefined,
      })
      ElMessage.success(`已删除 ${data.deleted} 条`)
      selectAllMatched.value = false
      reload()
    } catch (e) {
      ElMessage.error(e.response?.data?.detail || '删除失败')
    } finally { deleting.value = false }
  } else {
    // 本页选中项删除（原有逻辑）
    if (!sel.value.length) return
    try {
      await ElMessageBox.confirm(
        `确定永久删除选中的 ${sel.value.length} 条术语？此操作不可恢复。`,
        '批量删除', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    } catch { return }
    deleting.value = true
    try {
      const { data } = await api.post('/studio/vocab/batch-delete', { ids: sel.value.map(r => r.id) })
      ElMessage.success(`已删除 ${data.deleted} 条`)
      sel.value = []
      reload()
    } catch (e) { ElMessage.error(e.response?.data?.detail || '删除失败') }
    finally { deleting.value = false }
  }
}

function parseTerms() {
  const lines = (importText.value || '').split(/\r?\n/).map(s => s.trim()).filter(Boolean)
  const terms = []
  for (const line of lines) {
    const parts = line.split(',')
    const term = parts[0].trim()
    if (!term) continue
    let cat = importCat.value || null
    if (parts.length > 1 && parts[1].trim()) {
      const c = parts[1].trim()
      const byId = categories.value.find(x => String(x.id) === c)
      const byName = categories.value.find(x => x.name === c)
      cat = byId ? byId.id : (byName ? byName.id : cat)
    }
    terms.push({ term, category_id: cat || undefined })
  }
  return terms
}

async function doImport() {
  const terms = parseTerms()
  if (!terms.length) { ElMessage.warning('没有可导入的词'); return }
  importing.value = true
  try {
    const { data } = await api.post('/studio/vocab/batch-import',
      { terms, default_category_id: importCat.value || undefined })
    importReport.value = data
    ElMessage.success(`导入 ${data.imported} 条，跳过重复 ${data.skipped} 条`)
    reload()
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || '导入失败')
  } finally { importing.value = false }
}

function onCsvFile(e) {
  const f = e.target.files && e.target.files[0]
  if (!f) return
  f.text()
    .then(t => { importText.value = t; ElMessage.info('CSV 已载入文本框，可编辑后导入') })
    .catch(() => ElMessage.error('读取文件失败'))
  e.target.value = ''  // 允许重复选同一文件
}

onMounted(loadStats)
// keep-alive：切去别的菜单再回来刷一次统计 + 当前页结果（可能有新挖掘出的词）
onActivated(() => { loadStats(); reload() })
</script>
