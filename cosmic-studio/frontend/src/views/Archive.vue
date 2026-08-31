<template>
  <el-card>
    <div class="bar">
      <h3>归档库（只读，人工导入维护）</h3>
      <div class="bar-actions">
        <el-input v-model="kw" placeholder="需求编号 / 需求名称 / 客户" style="width:240px"
                  clearable aria-label="搜索归档项目" @keyup.enter="reload" @clear="reload" />
        <el-button v-if="isAdmin()" type="primary" @click="openImport">导入 xlsx</el-button>
      </div>
    </div>

    <el-table :data="list" v-loading="loading" size="small">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="requirement_id" label="需求编号" width="140" sortable />
      <el-table-column prop="requirement_name" label="需求名称" min-width="240" show-overflow-tooltip />
      <el-table-column prop="archived_at" label="归档时间" width="160" />
      <!-- 统计列：一眼看出归档数据的完整度（子过程为 0 = 归档时未带子过程） -->
      <el-table-column prop="module_count" label="模块数" width="90" align="right" />
      <el-table-column prop="fp_count" label="FP数" width="90" align="right" />
      <el-table-column label="子过程数" width="100" align="right">
        <template #default="s">
          <span :style="s.row.sub_count ? '' : 'color:#e6a23c'">{{ s.row.sub_count ?? 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230">
        <template #default="s">
          <el-button size="small" type="primary" plain @click="view(s.row.id)">查看</el-button>
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

    <el-dialog v-model="dlg" title="导入归档库" width="560px">
      <el-link type="primary" href="/api/archive/import/template" target="_blank"
               style="margin-bottom:10px; display:inline-block">下载导入模板（含逐列填写说明）</el-link>
      <el-alert type="info" :closable="false" style="margin-bottom:10px">
        <p style="margin:0">第5行起填数据；B/C/D=一二三级模块，E=功能用户，G=功能过程名（动词开头），K=数据属性（、分隔≥3字段）。增量按「模块+FP名」主键 upsert。</p>
      </el-alert>
      <el-radio-group v-model="mode">
        <el-radio value="incremental">增量导入（按业务主键 upsert，需选目标项目）</el-radio>
        <el-radio value="overwrite">全量覆盖导入（清空归档库重灌，admin）</el-radio>
        <el-radio value="bulk">批量覆盖导入（多 sheet 工作簿自动识别，admin）</el-radio>
      </el-radio-group>
      <p v-if="mode === 'bulk'" style="margin:8px 0 0; color:#909399; font-size:12px">
        按工作簿内第3行表头（功能过程/子过程描述/数据移动类型）自动识别数据 sheet，逐项目匹配存量归档项目或追加新项目。超大文件（&gt;50MB）建议改用后端 <code>scripts/bulk_import_archive.py</code> 执行。
      </p>
      <el-select v-if="mode === 'incremental'" v-model="targetPid" placeholder="选择归档项目"
                 style="width:100%; margin-top:10px" filterable aria-label="选择归档项目">
        <el-option v-for="p in allProjects" :key="p.id"
                   :label="`${p.requirement_id} ${p.requirement_name?.slice(0, 20)}`"
                   :value="p.id" />
      </el-select>
      <div style="margin-top:12px">
        <el-upload v-if="isAdmin()" :show-file-list="true" :auto-upload="false" accept=".xlsx"
                   :on-change="onPick">
          <el-button>选择 Excel 文件</el-button>
        </el-upload>
        <span v-if="file" style="margin-left:8px; color:#67c23a; font-size:12px">已选择：{{ file.name }}</span>
      </div>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :disabled="!file" :loading="uploading" @click="doImport">确认导入</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api, { isAdmin } from '../api'
import { PAGER_LAYOUT, PAGER_SIZES, usePaged } from '../composables/usePaged'
import { usePersistentState } from '../composables/usePersistentState'

const router = useRouter()
const kw = usePersistentState('kw', '')
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
function view(id) { router.push(`/archive/${id}`) }

function openImport() {
  mode.value = 'incremental'
  file.value = null
  dlg.value = true
  loadAll()
}
function onPick(f) {
  file.value = f.raw   // 仅暂存文件，不触发弹窗；选完模式后由「确认导入」提交
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
  if (mode.value === 'bulk') {
    if (!confirm('确认批量覆盖导入？将按工作簿内的多个数据 sheet 自动匹配/追加归档项目，整库先自动备份')) return
    q.set('confirm', 'archive')
  }
  const ep = mode.value === 'bulk' ? '/archive/import/workbook' : '/archive/import/xlsx'
  uploading.value = true
  try {
    const { data } = await api.post(`${ep}?${q}`, fd)
    ElMessage.success(`导入完成：项目${data.projects ?? ''} 模块${data.modules} FP${data.fps} 子过程${data.subs}`)
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
