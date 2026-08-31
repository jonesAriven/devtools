<template>
  <el-card>
    <div class="bar">
      <h3>归档库（只读，人工导入维护）</h3>
      <div class="bar-actions">
        <el-input v-model="kw" placeholder="需求编号 / 需求名称 / 客户" style="width:240px"
                  clearable aria-label="搜索归档项目" @keyup.enter="reload" @clear="reload" />
        <el-upload v-if="isAdmin()" :show-file-list="false" :auto-upload="false" accept=".xlsx"
                   :on-change="onFile" style="display:inline-block">
          <el-button type="primary">导入 xlsx</el-button>
        </el-upload>
      </div>
    </div>

    <el-table :data="list" v-loading="loading" size="small">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="requirement_id" label="需求编号" width="140" sortable />
      <el-table-column prop="requirement_name" label="需求名称" min-width="240" show-overflow-tooltip />
      <el-table-column prop="archived_at" label="归档时间" width="170" />
      <el-table-column label="操作" width="160">
        <template #default="s">
          <el-button size="small" @click="exportJson(s.row.id)">JSON</el-button>
          <el-button size="small" type="success" plain @click="exportXlsx(s.row.id)">xlsx</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="error || '暂无归档项目'" />
      </template>
    </el-table>

    <div class="pager">
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
                     :total="total" :page-sizes="PAGER_SIZES" :layout="PAGER_LAYOUT"
                     :disabled="loading" background />
    </div>

    <el-dialog v-model="dlg" title="导入归档库" width="520px">
      <el-alert type="info" :closable="false" style="margin-bottom:10px">
        <p style="margin:0">第5行起填数据；B/C/D=一二三级模块，E=功能用户，G=功能过程名（动词开头），K=数据属性（、分隔≥3字段）。增量按「模块+FP名」主键 upsert。</p>
        <el-link type="primary" style="margin-top:4px" href="/api/archive/import/template">下载导入模板（含逐列填写说明）</el-link>
      </el-alert>
      <el-radio-group v-model="mode">
        <el-radio value="incremental">增量导入（按业务主键 upsert，需选目标项目）</el-radio>
        <el-radio value="overwrite">全量覆盖导入（清空归档库重灌，admin）</el-radio>
      </el-radio-group>
      <el-select v-if="mode === 'incremental'" v-model="targetPid" placeholder="选择归档项目"
                 style="width:100%; margin-top:10px" filterable aria-label="选择归档项目">
        <el-option v-for="p in allProjects" :key="p.id"
                   :label="`${p.requirement_id} ${p.requirement_name?.slice(0, 20)}`"
                   :value="p.id" />
      </el-select>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="doImport">确认导入</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api, { isAdmin } from '../api'
import { PAGER_LAYOUT, PAGER_SIZES, usePaged } from '../composables/usePaged'

const kw = ref('')
const dlg = ref(false)
const mode = ref('incremental')
const targetPid = ref(null)
const file = ref(null)
const uploading = ref(false)
// 导入目标下拉要全量项目，不能只取当前页 —— 单独拉一份（归档项目量级有限）
const allProjects = ref([])

const { list, total, page, pageSize, loading, error, reset } = usePaged(
  p => api.get('/archive/projects', { params: { ...p, keyword: kw.value || undefined } }),
  { pageSize: 20, immediate: true }
)

function reload() { reset() }

function onFile(f) {
  file.value = f.raw
  mode.value = 'incremental'
  dlg.value = true
  loadAll()
}

async function loadAll() {
  try {
    const { data } = await api.get('/archive/projects', { params: { page: 1, page_size: 100 } })
    allProjects.value = data.list ?? []
  } catch { /* ignore */ }
}

async function doImport() {
  if (!file.value) return
  if (mode.value === 'incremental' && !targetPid.value) { ElMessage.warning('请选择目标项目'); return }
  const fd = new FormData()
  fd.append('file', file.value)
  const q = new URLSearchParams({ mode: mode.value })
  if (mode.value === 'incremental') q.set('project_id', targetPid.value)
  if (mode.value === 'overwrite') {
    if (!confirm(`确认全量覆盖归档库？现有 ${total.value} 个项目将被清空重灌`)) return
    q.set('confirm', 'archive')
  }
  uploading.value = true
  try {
    const { data } = await api.post(`/archive/import/xlsx?${q}`, fd)
    ElMessage.success(`导入完成：模块${data.modules} FP${data.fps} 子过程${data.subs}`)
    dlg.value = false
    reset()
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || '导入失败')
  } finally { uploading.value = false }
}

function exportXlsx(id) { window.open(`/api/archive/projects/${id}/export/xlsx`, '_blank') }
async function exportJson(id) {
  const { data } = await api.get(`/archive/projects/${id}/export/json`)
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `archive_p${id}.json`
  a.click()
}
onMounted(loadAll)
</script>
