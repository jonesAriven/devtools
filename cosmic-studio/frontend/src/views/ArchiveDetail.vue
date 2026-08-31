<template>
  <el-card v-loading="loading">
    <div class="bar">
      <div>
        <el-breadcrumb style="margin-bottom:6px">
          <el-breadcrumb-item :to="{ path: '/archive' }">归档库</el-breadcrumb-item>
          <el-breadcrumb-item>项目详情</el-breadcrumb-item>
        </el-breadcrumb>
        <h3>{{ proj?.requirement_id }} {{ proj?.requirement_name }}</h3>
      </div>
      <div class="bar-actions">
        <el-button type="success" plain @click="exportXlsx">导出 xlsx</el-button>
        <el-button type="info" plain @click="exportJson">导出 JSON</el-button>
      </div>
    </div>

    <el-alert v-if="loaded && !subCount" type="warning" :closable="false" style="margin-bottom:10px">
      该项目归档时<b>未包含子过程</b>（数据移动 / 数据组 / 数据属性）。
      根因：Hermes 归档建表只建了 projects / modules / fps 三张表，漏建
      <code>cosmic_sub_processes</code>，子过程从未写入归档库。建表缺陷已修复，历史数据需重新归档补录。
    </el-alert>

    <!-- 模块级分页：单项目可达 251 模块 / 3780 FP，必须分页，否则整棵三层树铺进 DOM 会卡死 -->
    <div class="bar" style="align-items:center">
      <div class="bar-actions">
        <el-input v-model="modKw" placeholder="按模块名筛选" style="width:220px" clearable
                  aria-label="按模块名筛选" @keyup.enter="reload" @clear="reload" />
        <span class="muted" v-if="stats">
          全项目共 {{ stats.module_count }} 模块 / {{ stats.fp_count }} FP / {{ stats.sub_count }} 子过程
        </span>
      </div>
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
                     :total="total" :page-sizes="[5, 10, 20, 50]" :disabled="loading"
                     layout="total, sizes, prev, pager, next" background />
    </div>

    <el-table :data="rows" row-key="rowKey" border :tree-props="{ children: 'children' }"
              :default-expand-all="false">
      <template #empty>
        <el-empty :description="error || '该项目还没有模块'" />
      </template>
      <el-table-column prop="module" label="三级模块" min-width="140" show-overflow-tooltip />
      <el-table-column prop="fp" label="功能过程" min-width="150" show-overflow-tooltip />
      <el-table-column prop="event" label="触发事件" min-width="150" show-overflow-tooltip />
      <el-table-column prop="move" label="数据移动类型" width="90" />
      <el-table-column prop="desc" label="子过程描述" min-width="180" show-overflow-tooltip />
      <el-table-column prop="group" label="数据组" min-width="150" show-overflow-tooltip />
      <el-table-column prop="attrs" label="数据属性" min-width="180" show-overflow-tooltip />
    </el-table>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'

const route = useRoute()
const pid = route.params.id

const tree = ref(null)
const loading = ref(false)
const error = ref('')
const loaded = ref(false)
const page = ref(1)
const pageSize = ref(10)
const modKw = ref('')

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

// 摊平成 el-table 树形行：module → fps → subs
// 子过程为空时不给 children，避免出现点开是空的展开箭头
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
</script>
