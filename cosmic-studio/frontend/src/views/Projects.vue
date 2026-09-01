<template>
  <el-card>
    <div class="bar">
      <h3>编写库项目</h3>
      <div class="bar-actions">
        <el-input v-model="q" placeholder="搜索需求编号/名称/客户" style="width:240px" clearable
                  aria-label="搜索项目" @keyup.enter="reload" @clear="reload" />
        <el-upload v-if="canImport" :show-file-list="false" :auto-upload="false" accept=".xlsx"
                   :on-change="onFile" style="display:inline-block">
          <el-button type="success" plain>导入 xlsx</el-button>
        </el-upload>
        <el-button v-if="canEdit" type="primary" @click="openDlg">新建项目</el-button>
      </div>
    </div>

    <el-table :data="groupedRows" row-key="rowKey" border v-loading="loading"
              :tree-props="{ children: 'children' }" default-expand-all>
      <template #empty>
        <el-empty :description="error || '暂无项目'" />
      </template>
      <el-table-column prop="reqLabel" label="需求 / 副本" min-width="300">
        <template #default="s">
          <template v-if="s.row.kind === 'group'">
            <b>{{ s.row.reqId }}</b>　{{ s.row.reqName }}
            <el-tag size="small" style="margin-left:6px">{{ s.row.copies.length }} 个副本</el-tag>
          </template>
          <template v-else>
            <span style="padding-left:4px">
              <el-tag v-if="s.row.is_primary" size="small" type="warning" effect="dark" style="margin-right:6px">主</el-tag>
              副本{{ s.row.copy_no }}　{{ s.row.requirement_name }}
            </span>
          </template>
        </template>
      </el-table-column>
      <el-table-column prop="client_name" label="客户" width="120" show-overflow-tooltip />
      <el-table-column prop="module_count" label="模块" width="70" />
      <el-table-column prop="fp_count" label="FP" width="70" />
      <el-table-column prop="sub_count" label="子过程" width="80" />
      <el-table-column prop="created_at" label="创建时间" width="160" />
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="s">
          <template v-if="s.row.kind === 'copy'">
            <el-button size="small" type="primary" text @click="$router.push(`/projects/${s.row.id}`)">进入</el-button>
            <el-button v-if="!s.row.is_primary" size="small" text type="warning" @click="setPrimary(s.row)">设主</el-button>
            <el-button size="small" text @click="copyProject(s.row)">复制</el-button>
            <el-button size="small" text @click="diffDlgOpen(s.row)">对比</el-button>
            <el-button v-if="isAdmin" size="small" text type="danger" @click="delProject(s.row)">删</el-button>
          </template>
          <template v-else-if="s.row.kind === 'group'">
            <el-button v-if="isAdmin" size="small" text type="danger" @click="delGroup(s.row)">删除全部副本</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <!-- 副本对比 -->
    <el-dialog v-model="diffDlg" :title="`副本对比：${diffRow?.requirement_name}`" width="640px">
      <p class="hint">对比目标：{{ diffTarget?.requirement_name }}（{{ diffTarget?.is_primary ? '主副本' : '副本' + diffTarget?.copy_no }}）</p>
      <div v-if="diffData">
        <p>✅ 共有 FP：<b>{{ diffData.common }}</b> 个 ｜ 本副本独有 <b style="color:#e6a23c">{{ diffData.only_in_this.length }}</b> 个 ｜ 对方独有 <b style="color:#909399">{{ diffData.only_in_main.length }}</b> 个</p>
        <template v-if="diffData.only_in_this.length">
          <p class="hint">本副本独有：</p>
          <div class="diff-list"><div v-for="f in diffData.only_in_this" :key="f">· {{ f }}</div></div>
        </template>
        <template v-if="diffData.only_in_main.length">
          <p class="hint">对方独有：</p>
          <div class="diff-list"><div v-for="f in diffData.only_in_main" :key="f">· {{ f }}</div></div>
        </template>
      </div>
    </el-dialog>

    <!-- 导入向导 -->
    <el-dialog v-model="impDlg" title="导入编写库" width="520px">
      <el-alert type="info" :closable="false" style="margin-bottom:10px">
        <p style="margin:0">第5行起填数据；B/C/D=一二三级模块，E=功能用户（发起者/接收者两行），G=功能过程名（动词开头），K=数据属性（、分隔≥3字段）。增量按「模块+FP名」主键 upsert。</p>
        <el-link type="primary" style="margin-top:4px" @click="downloadTemplate">下载导入模板（含逐列填写说明）</el-link>
      </el-alert>
      <el-radio-group v-model="impMode" class="imp-modes">
        <el-radio value="incremental">增量导入（按业务主键 upsert，需选目标项目）</el-radio>
        <el-radio value="overwrite_proj" :disabled="!isAdmin">覆盖导入该项目（清空所选项目重灌，admin）</el-radio>
        <el-radio value="overwrite_all" :disabled="!isAdmin">整库覆盖导入（清空编写库全部重灌，admin）</el-radio>
      </el-radio-group>
      <el-select v-if="impMode !== 'overwrite_all'" v-model="impPid" placeholder="选择目标项目"
                 style="width:100%; margin-top:10px" filterable aria-label="选择导入目标项目">
        <el-option v-for="p in allProjects" :key="p.id"
                   :label="`副本${p.copy_no}：${p.requirement_id} ${p.requirement_name?.slice(0, 20)}`" :value="p.id" />
      </el-select>
      <template #footer>
        <el-button @click="impDlg = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="doImport">确认导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dlg" title="新建项目" width="480px">
      <el-form label-width="90px">
        <el-form-item label="需求编号"><el-input v-model="form.requirement_id" /></el-form-item>
        <el-form-item label="需求名称"><el-input v-model="form.requirement_name" /></el-form-item>
        <el-form-item label="项目编码"><el-input v-model="form.project_code" placeholder="如 ngcard" /></el-form-item>
        <el-form-item label="客户名称"><el-input v-model="form.client_name" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">创建</el-button>
      </template>
    </el-dialog>

    <div class="pager">
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
                     :total="total" :page-sizes="PAGER_SIZES" :layout="PAGER_LAYOUT"
                     :disabled="loading" background />
    </div>
  </el-card>
</template>

<script setup>
import { computed, onActivated, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import api, { isAdmin, role, downloadBlob } from '../api'
import { PAGER_LAYOUT, PAGER_SIZES, usePaged } from '../composables/usePaged'
import { usePersistentState } from '../composables/usePersistentState'

const router = useRouter()
const q = usePersistentState('q', '')
const dlg = ref(false)
const saving = ref(false)
const form = reactive({ requirement_id: '', requirement_name: '', project_code: 'ngcard', client_name: '中移动在线基地' })
const emptyForm = () => ({ requirement_id: '', requirement_name: '', project_code: 'ngcard', client_name: '中移动在线基地' })

// ── 导入向导 ──
const canImport = computed(() => ['admin', 'editor'].includes((JSON.parse(localStorage.getItem('user') || '{}').role) || ''))
const canEdit = computed(() => ['admin', 'editor'].includes(role()))
const impDlg = ref(false)
const impMode = ref('incremental')
const impPid = ref(null)
const impFile = ref(null)
const importing = ref(false)

// 导入目标下拉需要全量项目，不能只给当前页
const allProjects = ref([])
async function loadAll() {
  try {
    const { data } = await api.get('/active/projects', { params: { page: 1, page_size: 100 } })
    allProjects.value = data.list ?? []
  } catch { /* ignore */ }
}

function downloadTemplate() {
  downloadBlob('/api/active/import/template', 'cosmic-import-template.xlsx')
    .catch(e => ElMessage.error(e.message || '模板下载失败'))
}
function onFile(f) {
  impFile.value = f.raw
  impMode.value = 'incremental'
  impPid.value = null
  impDlg.value = true
  loadAll()
}
async function doImport() {
  if (!impFile.value) return
  if (impMode.value !== 'overwrite_all' && !impPid.value) { ElMessage.warning('请选择目标项目'); return }
  const q = new URLSearchParams()
  if (impMode.value === 'incremental') {
    q.set('mode', 'incremental'); q.set('project_id', impPid.value)
  } else {
    q.set('mode', 'overwrite'); q.set('project_id', impPid.value || '')
    if (impMode.value === 'overwrite_all') {
      try {
        await ElMessageBox.confirm('确认整库覆盖？编写库现有全部项目将被清空重灌（覆盖前自动备份）', '整库覆盖确认', { type: 'warning' })
      } catch { return }
      q.set('confirm', 'active')
    }
  }
  const fd = new FormData()
  fd.append('file', impFile.value)
  importing.value = true
  try {
    const { data } = await api.post(`/active/import/xlsx?${q}`, fd)
    ElMessage.success(`导入完成：模块${data.modules} FP${data.fps} 子过程${data.subs}${data.backup ? '，已自动备份' : ''}`)
    impDlg.value = false
    impFile.value = null
    load()
  } catch (e) {
    const d = e.response?.data
    ElMessage.error(typeof d?.detail === 'string' ? d.detail : `导入失败（${e.response?.status}）`)
  } finally { importing.value = false }
}

// ── 副本管理 ──
const diffDlg = ref(false)
const diffRow = ref(null)
const diffTarget = ref(null)
const diffData = ref(null)

// 关键字筛选已下推到服务端（此前是拉全量再在内存里 filter）
const groupedRows = computed(() => {
  const groups = {}
  for (const p of list.value) {
    const k = p.requirement_id || `__id${p.id}`
    ;(groups[k] ||= []).push(p)
  }
  return Object.values(groups).map(copies => {
    copies.sort((a, b) => (a.copy_no || 0) - (b.copy_no || 0))
    const head = copies[0]
    return {
      rowKey: `g-${head.requirement_id}`, kind: 'group',
      reqId: head.requirement_id, reqName: head.requirement_name, copies,
      client_name: [...new Set(copies.map(c => c.client_name).filter(Boolean))].join('/') || '—',
      module_count: copies.reduce((s, c) => s + (c.module_count || 0), 0),
      fp_count: copies.reduce((s, c) => s + (c.fp_count || 0), 0),
      sub_count: copies.reduce((s, c) => s + (c.sub_count || 0), 0),
      created_at: '',
      children: copies.map(p => ({
        rowKey: `p-${p.id}`, kind: 'copy', ...p,
        reqLabel: `副本${p.copy_no}`
      }))
    }
  })
})

async function setPrimary(row) {
  await api.put(`/active/projects/${row.id}/primary`)
  ElMessage.success(`副本${row.copy_no} 已设为主副本`)
  load()
}
async function copyProject(row) {
  const { data } = await api.post(`/active/projects/${row.id}/copy`)
  ElMessage.success(`已复制为新副本：${data.name}（id=${data.id}）`)
  load()
}
async function diffDlgOpen(row) {
  diffRow.value = row
  const main = list.value.find(p => p.requirement_id === row.requirement_id && p.is_primary) || row
  diffTarget.value = main
  if (main.id === row.id) { ElMessage.info('该副本就是主副本，请先复制一个副本再对比'); return }
  const { data } = await api.get(`/active/projects/${row.id}/diff`, { params: { against: main.id } })
  diffData.value = data
  diffDlg.value = true
}
async function delProject(row) {
  const vers = await api.get(`/active/projects/${row.id}/versions`).catch(() => ({ data: [] }))
  const tip = (vers.data || []).length
    ? `删除副本${row.copy_no}「${row.requirement_name}」？（该副本有 ${vers.data.length} 个版本存档）`
    : `⚠️ 副本${row.copy_no}「${row.requirement_name}」从未创建过版本快照，删除后数据不可恢复！确认删除？`
  await ElMessageBox.confirm(tip, '删除确认', { type: 'warning' })
  await api.delete(`/active/projects/${row.id}?confirm=active`)
  ElMessage.success('已删除')
  load()
}
async function delGroup(row) {
  await ElMessageBox.confirm(`删除需求「${row.reqId}」下全部 ${row.copies.length} 个副本？（有版本存档的副本数据可在存档文件中找回）`, '整组删除确认', { type: 'warning' })
  for (const c of row.copies) {
    await api.delete(`/active/projects/${c.id}?confirm=active`)
  }
  ElMessage.success('已删除全部副本')
  load()
}

function openDlg() {
  Object.assign(form, emptyForm())  // 防止残留上次输入
  dlg.value = true
}

// 按「需求」分页：total = 需求数。分片在需求级别，同一个需求的副本不会被劈到两页
const { list, total, page, pageSize, loading, error, reset, load } = usePaged(
  p => api.get('/active/projects', {
    params: { ...p, group_by_req: true, keyword: q.value || undefined },
  }),
  { pageSize: 20, immediate: true }
)
function reload() { reset() }
async function create() {
  if (!form.requirement_id.trim() || !form.requirement_name.trim()) {
    ElMessage.warning('需求编号和需求名称为必填项')
    return
  }
  try {
    await api.post('/active/projects', form)
    ElMessage.success('已创建')
    dlg.value = false
    load()
  } catch (e) {
    const d = e.response?.data?.detail
    ElMessage.error(typeof d === 'string' ? d : '创建失败，请检查必填项')
  }
}
onMounted(loadAll)
// keep-alive：切去别的菜单再回来刷一次数据（全量项目列表 + 当前页结果）
onActivated(() => { loadAll(); reload() })
</script>

<style scoped>
/* .bar / .bar h3 已迁入 theme.css，此处不再重复定义 */
.imp-modes { display: flex; flex-direction: column; gap: var(--sp-2); align-items: flex-start; }
.hint { color: var(--c-text-3); font-size: var(--fs-sm); margin: var(--sp-2) 0; }
.diff-list { max-height: 200px; overflow-y: auto; background: var(--c-surface-3);
  padding: var(--sp-2); border-radius: var(--r-sm);
  font-size: var(--fs-base); line-height: 1.8; }
</style>
