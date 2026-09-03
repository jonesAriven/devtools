<template>
  <el-card v-loading="loading">
    <div class="bar">
      <div>
        <el-breadcrumb style="margin-bottom:6px">
          <el-breadcrumb-item :to="{ path: '/archive' }">归档库</el-breadcrumb-item>
          <el-breadcrumb-item>需求详情</el-breadcrumb-item>
        </el-breadcrumb>
        <h3>{{ proj?.requirement_id }} {{ proj?.requirement_name }}</h3>
      </div>
      <div class="bar-actions">
        <el-button type="success" plain @click="exportXlsx">导出 xlsx</el-button>
        <el-button type="info" plain @click="exportJson">导出 JSON</el-button>
      </div>
    </div>

    <el-alert v-if="loaded && !subCount" type="warning" :closable="false" style="margin-bottom:10px">
      该需求归档时<b>未包含子过程</b>（数据移动 / 数据组 / 数据属性）。
      根因：Hermes 归档建表只建了 projects / modules / fps 三张表，漏建
      <code>cosmic_sub_processes</code>，子过程从未写入归档库。建表缺陷已修复，历史数据需重新归档补录。
    </el-alert>

    <!-- 模块级分页 + 视图切换 -->
    <div class="bar" style="align-items:center">
      <div class="bar-actions">
        <el-input v-model="modKw" placeholder="按模块名筛选" style="width:200px" clearable
                  aria-label="按模块名筛选" @keyup.enter="reload" @clear="reload" />
        <el-button-group>
          <el-button :type="viewMode === 'tree' ? 'primary' : ''" size="small" @click="viewMode = 'tree'">树形</el-button>
          <el-button :type="viewMode === 'flat' ? 'primary' : ''" size="small" @click="viewMode = 'flat'">扁平</el-button>
        </el-button-group>
        <span class="muted" v-if="stats">
          全需求共 {{ stats.module_count }} 模块 / {{ stats.fp_count }} FP / {{ stats.sub_count }} 子过程
        </span>
      </div>
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
                     :total="total" :page-sizes="[5, 10, 20, 50]" :disabled="loading"
                     layout="total, sizes, prev, pager, next" background />
    </div>

    <!-- 全列筛选（扁平视图下最有用，树形视图也可用） -->
    <div v-show="viewMode === 'flat'" class="bar" style="align-items:center">
      <div class="bar-actions" style="flex-wrap:wrap; gap:6px">
        <el-input v-model="filters.fp" placeholder="功能过程" style="width:140px" size="small" clearable />
        <el-input v-model="filters.event" placeholder="触发事件" style="width:140px" size="small" clearable />
        <el-select v-model="filters.move" placeholder="数据移动类型" style="width:130px" size="small" clearable>
          <el-option label="E 输入" value="E" /><el-option label="W 写入" value="W" />
          <el-option label="R 读取" value="R" /><el-option label="X 排除" value="X" />
        </el-select>
        <el-input v-model="filters.desc" placeholder="子过程描述" style="width:150px" size="small" clearable />
        <el-input v-model="filters.group" placeholder="数据组" style="width:140px" size="small" clearable />
        <el-input v-model="filters.attrs" placeholder="数据属性" style="width:150px" size="small" clearable />
        <el-button size="small" @click="clearFilters">清空筛选</el-button>
        <span class="muted" v-if="filteredFlatRows.length !== flatRows.length">
          筛选后 {{ filteredFlatRows.length }} / {{ flatRows.length }} 条
        </span>
      </div>
    </div>

    <!-- 树形视图（原有） -->
    <div v-if="viewMode === 'tree'" class="tfill">
    <el-table :data="rows" row-key="rowKey" border height="100%" :tree-props="{ children: 'children' }"
              :default-expand-all="false">
      <template #empty>
        <el-empty :description="error || '该需求还没有模块'" />
      </template>
      <el-table-column prop="module" label="三级模块" min-width="140" show-overflow-tooltip />
      <el-table-column prop="fp" label="功能过程" min-width="150" show-overflow-tooltip />
      <el-table-column prop="event" label="触发事件" min-width="150" show-overflow-tooltip />
      <el-table-column prop="move" label="数据移动类型" width="90" />
      <el-table-column prop="desc" label="子过程描述" min-width="180" show-overflow-tooltip />
      <el-table-column prop="group" label="数据组" min-width="150" show-overflow-tooltip />
      <el-table-column prop="attrs" label="数据属性" min-width="180" show-overflow-tooltip />
    </el-table>
    </div>

    <!-- 扁平视图：全部子过程铺平，父列合并单元格（类似 Excel） -->
    <div v-else class="tfill">
    <el-table :data="filteredFlatRows" row-key="rowKey" border height="100%"
              :span-method="flatSpanMethod" style="width:100%">
      <template #empty>
        <el-empty :description="error || '该需求还没有模块'" />
      </template>
      <el-table-column prop="module" label="三级模块" min-width="120" show-overflow-tooltip />
      <el-table-column prop="fp" label="功能过程" min-width="150" show-overflow-tooltip />
      <el-table-column prop="event" label="触发事件" min-width="160" show-overflow-tooltip />
      <el-table-column prop="move" label="数据移动类型" width="80" />
      <el-table-column prop="desc" label="子过程描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="group" label="数据组" min-width="160" show-overflow-tooltip />
      <el-table-column prop="attrs" label="数据属性" min-width="200" show-overflow-tooltip />
    </el-table>
    </div>
  </el-card>
</template>

<script setup>
import { computed, onActivated, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useViewMode } from '../composables/useViewMode'
import { usePersistentState } from '../composables/usePersistentState'

const route = useRoute()
let pid = route.params.id

const tree = ref(null)
const loading = ref(false)
const error = ref('')
const loaded = ref(false)
const page = usePersistentState('page', 1)
const pageSize = usePersistentState('pageSize', 10)
const modKw = usePersistentState('modKw', '')
const viewMode = useViewMode() // 'tree' | 'flat'，跨路由/刷新保持

// 全列筛选
const filters = usePersistentState('filters', { fp: '', event: '', move: '', desc: '', group: '', attrs: '' })
function clearFilters() { Object.assign(filters.value, { fp: '', event: '', move: '', desc: '', group: '', attrs: '' }) }

const proj = computed(() => tree.value?.project)
const stats = computed(() => tree.value?.stats)
const total = computed(() => tree.value?.total ?? 0)
const subCount = computed(() => stats.value?.sub_count ?? 0)

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.get(`/archive/projects/${pid}/tree`, {
      params: { module_page: page.value, module_page_size: pageSize.value, keyword: modKw.value || undefined }
    })
    tree.value = data
  } catch (e) {
    error.value = e.response?.data?.detail || '加载失败'
    tree.value = null
  } finally {
    loading.value = false
    loaded.value = true
  }
}

// ── 树形视图行（原有） ──
const rows = computed(() => {
  const mods = tree.value?.modules
  if (!mods) return []
  return mods.map(m => {
    const mkey = `m${m.id}`
    return {
      rowKey: mkey, kind: 'module', id: m.id, module: m.level3,
      fp: '', fu: '', event: '', move: '', desc: '', group: '', attrs: '',
      children: (m.fps || []).map(f => {
        const fkey = `${mkey}-f${f.id}`
        const subs = f.subs || []
        return {
          rowKey: fkey, kind: 'fp', id: f.id, module: m.level3,
          fp: f.fp_name, fu: f.functional_user, event: f.trigger_event,
          move: '', desc: '', group: '', attrs: '',
          children: subs.length
            ? subs.map(s => ({
                rowKey: `${fkey}-s${s.id}`, kind: 'sub', id: s.id,
                module: m.level3, fp: f.fp_name, event: f.trigger_event, move: s.data_move_type,
                desc: s.description, group: s.data_group_name, attrs: s.data_attributes
              }))
            : undefined
        }
      })
    }
  })
})

// ── 扁平视图：全部子过程铺平 + 合并单元格信息 ──
const flatRows = computed(() => {
  const mods = tree.value?.modules
  if (!mods) return []
  const out = []
  for (const m of mods) {
    for (const f of (m.fps || [])) {
      const subs = f.subs || []
      if (!subs.length) continue // FP 无子过程则不展示（扁平视图只看叶子）
      for (const s of subs) {
        out.push({
          rowKey: `flat-s${s.id}`, kind: 'sub', id: s.id,
          _moduleId: m.id, _fpId: f.id,
          module: m.level3, fp: f.fp_name, event: f.trigger_event,
          move: s.data_move_type, desc: s.description,
          group: s.data_group_name, attrs: s.data_attributes
        })
      }
    }
  }
  return out
})

// 筛选后的扁平行
const filteredFlatRows = computed(() => {
  const f = filters.value
  const hasFilter = f.fp || f.event || f.move || f.desc || f.group || f.attrs
  if (!hasFilter) return flatRows.value
  const kw = (v) => (v || '').toLowerCase()
  return flatRows.value.filter(r =>
    (!f.fp || kw(r.fp).includes(kw(f.fp))) &&
    (!f.event || kw(r.event).includes(kw(f.event))) &&
    (!f.move || r.move === f.move) &&
    (!f.desc || kw(r.desc).includes(kw(f.desc))) &&
    (!f.group || kw(r.group).includes(kw(f.group))) &&
    (!f.attrs || kw(r.attrs).includes(kw(f.attrs)))
  )
})

// el-table span-method：相邻同行同值的父列合并（三级模块/功能过程/触发事件）
function flatSpanMethod({ row, column, rowIndex, columnIndex }) {
  // 只对前 3 列（module=0, fp=1, event=2）做合并
  if (columnIndex > 2) return { rowspan: 1, colspan: 1 }
  const data = filteredFlatRows.value
  const colKey = ['module', 'fp', 'event'][columnIndex]
  const val = row[colKey]
  // 向前找第一个相同值的行
  let first = rowIndex
  while (first > 0 && data[first - 1][colKey] === val) first--
  // 向后数连续相同值个数
  let span = 1
  while (first + span < data.length && data[first + span][colKey] === val) span++
  if (rowIndex === first) return { rowspan: span, colspan: 1 }
  return { rowspan: 0, colspan: 1 } // 被合并的行隐藏
}

function exportXlsx() { window.open(`/api/archive/projects/${pid}/export/xlsx`, '_blank') }
async function exportJson() {
  try {
    const { data } = await api.get(`/archive/projects/${pid}/export/json`)
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `archive_p${pid}.json`
    a.click()
  } catch {
    ElMessage.error('导出失败')
  }
}

watch([page, pageSize], reload)
onMounted(reload)
// keep-alive：切换不同归档项目时主动重拉
watch(() => route.params.id, (v, old) => { if (v && v !== old) { pid = v; reload() } })
// keep-alive 激活时刷数据
onActivated(reload)
</script>
