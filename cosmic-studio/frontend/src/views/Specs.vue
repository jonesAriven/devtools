<template>
  <el-card>
    <div class="bar">
      <h3>规范中心（即改即生效，无需重启）</h3>
      <div class="bar-actions">
        <el-radio-group v-model="category" @change="load">
          <el-radio-button value="writing">编写规范</el-radio-button>
          <el-radio-button value="screenshot">截图规范</el-radio-button>
          <el-radio-button value="custom">自定义</el-radio-button>
        </el-radio-group>
        <template v-if="isAdmin()">
          <el-button @click="openCreate">新增规范</el-button>
          <el-button @click="exportSpecs">导出 JSON</el-button>
          <el-button @click="impDlg = true">导入 JSON</el-button>
          <el-button v-if="selKeys.length" type="danger" plain
                     @click="bulkDel">批量删除（{{ selKeys.length }}）</el-button>
        </template>
      </div>
    </div>
    <el-table :data="pageList" v-loading="loading"
              @selection-change="v => (selKeys = v.map(r => r.spec_key))">
      <el-table-column type="selection" width="42" />
      <template #empty><el-empty description="该分类下暂无规范" /></template>
      <el-table-column prop="spec_key" label="规范键" min-width="200" />
      <el-table-column prop="description" label="说明" min-width="260" show-overflow-tooltip />
      <el-table-column label="来源" width="80">
        <template #default="s">
          <el-tag size="small" :type="s.row._source === 'db' ? 'warning' : 'info'">{{ s.row._source }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="s">
          <el-button size="small" @click="open(s.row)">查看/编辑</el-button>
          <el-button v-if="s.row._source === 'db' && isAdmin()" size="small" type="warning" plain
                     @click="reset(s.row)">{{ s.row.spec_key in seedKeys ? '还原' : '删除' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
                     :total="total" :page-sizes="[10, 20, 50]" background
                     layout="total, sizes, prev, pager, next" />
    </div>

    <el-dialog v-model="dlg" :title="current.spec_key" width="640px" top="6vh">
      <p class="muted" style="margin-top:0">{{ current.description }}</p>
      <el-input v-model="editText" type="textarea" :rows="14" spellcheck="false"
                style="font-family: monospace" />
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button v-if="isAdmin()" type="primary" @click="save">保存（立即生效）</el-button>
      </template>
    </el-dialog>

    <!-- 新增自定义规范键：种子键由引擎定义不可新增，这里只放团队自定义条目 -->
    <el-dialog v-model="createDlg" title="新增自定义规范" width="560px">
      <el-form label-width="90px">
        <el-form-item label="规范键"><el-input v-model="createForm.spec_key" placeholder="如 team_conventions" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="createForm.description" /></el-form-item>
        <el-form-item label="值(JSON)">
          <el-input v-model="createForm.valueText" type="textarea" :rows="6" spellcheck="false"
                    style="font-family: monospace" />
        </el-form-item>
      </el-form>
      <p class="muted" style="margin:0">自定义规范引擎不消费，仅作为团队规范数据沉淀（可随导出/导入迁移）。</p>
      <template #footer>
        <el-button @click="createDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createSpec">创建</el-button>
      </template>
    </el-dialog>

    <!-- 导入规范 JSON（merge：逐键覆盖，未提及的键不动） -->
    <el-dialog v-model="impDlg" title="导入规范 JSON" width="640px" top="6vh">
      <p class="muted" style="margin-top:0">
        粘贴「导出 JSON」的 specs 部分或完整导出文件内容。种子键会先过合法性校验，非法键跳过并报告。</p>
      <el-input v-model="impText" type="textarea" :rows="14" spellcheck="false"
                style="font-family: monospace" placeholder='{"ewx_rules": {"value": {...}}, ...}' />
      <template #footer>
        <el-button @click="impDlg = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="doImport">导入（立即生效）</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onActivated, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api, { isAdmin } from '../api'
import { useLocalPaged } from '../composables/usePaged'
import { usePersistentState } from '../composables/usePersistentState'
import { ElMessageBox } from 'element-plus'

const all = ref([])
const category = usePersistentState('category', 'writing')
const loading = ref(false)
const dlg = ref(false)
const current = ref({})
const editText = ref('')
const selKeys = ref([])
const seedKeys = ref([])
const createDlg = ref(false)
const saving = ref(false)
const createForm = ref({ spec_key: '', description: '', valueText: '{}' })
const impDlg = ref(false)
const importing = ref(false)
const impText = ref('')

const { list: pageList, total, page, pageSize } = useLocalPaged(all, 20)

// resetPage：切换分类时回第 1 页；但切走再切回来要留在原来的页码
// （@change="load" 会把分类值当第一个实参传进来，非布尔值同样按 resetPage=true 处理）
async function load(resetPage = true) {
  loading.value = true
  try {
    const { data } = await api.get('/studio/specs', { params: { category: category.value } })
    all.value = Object.values(data.specs).map((v, i) => ({ spec_key: Object.keys(data.specs)[i], ...v }))
    if (resetPage) page.value = 1
  } finally { loading.value = false }
}
// 种子键集合：/studio/specs/export 的返回里 _source=seed 的键（决定按钮文案是还原还是删除）
async function loadSeedKeys() {
  try {
    const { data } = await api.get('/studio/specs/export')
    seedKeys.value = Object.keys(data.specs || {}).filter(k => data.specs[k]._source === 'seed')
  } catch { /* 非阻塞：仅影响按钮文案 */ }
}
function open(row) {
  current.value = row
  editText.value = JSON.stringify(row.value, null, 2)
  dlg.value = true
}
async function save() {
  try {
    const value = JSON.parse(editText.value)
    await api.put(`/studio/specs/${current.value.spec_key}`, { value, category: category.value })
    ElMessage.success('已保存并立即生效')
    dlg.value = false
    load()
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || 'JSON 格式错误')
  }
}
async function reset(row) {
  const isSeed = row.spec_key in seedKeys.value
  await api.delete(`/studio/specs/${row.spec_key}`)
  ElMessage.success(isSeed ? '已还原默认' : '已删除')
  load()
}

function openCreate() {
  createForm.value = { spec_key: '', description: '', valueText: '{}' }
  createDlg.value = true
}
async function createSpec() {
  let value
  try { value = JSON.parse(createForm.value.valueText) } catch { ElMessage.error('值不是合法 JSON'); return }
  saving.value = true
  try {
    await api.post('/studio/specs', { ...createForm.value, value })
    ElMessage.success('规范已创建')
    createDlg.value = false
    load()
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || '创建失败')
  } finally { saving.value = false }
}

// 批量删除：种子键=回落默认值，自定义键=彻底删除，确认框里说清两种语义
async function bulkDel() {
  const keys = selKeys.value
  const seedPart = keys.filter(k => seedKeys.value.includes(k))
  const customPart = keys.filter(k => !seedKeys.value.includes(k))
  const desc = [
    seedPart.length && `${seedPart.length} 个内置规范将还原为默认值`,
    customPart.length && `${customPart.length} 个自定义规范将彻底删除`,
  ].filter(Boolean).join('；')
  try {
    await ElMessageBox.confirm(`选中 ${keys.length} 项：${desc}。确认执行？`, '批量删除确认', { type: 'warning' })
  } catch { return }
  try {
    const { data } = await api.post('/studio/specs/bulk-delete', { keys })
    ElMessage.success(`已处理 ${data.count} 项（还原 ${data.reverted.length}，删除 ${data.removed.length}）`)
    selKeys.value = []
    load()
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || '批量删除失败')
  }
}

async function exportSpecs() {
  const { data } = await api.get('/studio/specs/export')
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `cosmic-specs-${new Date().toISOString().slice(0, 10)}.json`
  a.click()
  ElMessage.success('已导出')
}

async function doImport() {
  let parsed
  try { parsed = JSON.parse(impText.value) } catch { ElMessage.error('不是合法 JSON'); return }
  const specs = parsed.specs && typeof parsed.specs === 'object' ? parsed.specs : parsed
  importing.value = true
  try {
    const { data } = await api.post('/studio/specs/import', { specs })
    if (data.error_count) {
      ElMessage.warning(`已应用 ${data.applied_count} 项，${data.error_count} 项被拒：${data.errors.map(e => e.spec_key).join('、')}`)
    } else {
      ElMessage.success(`已导入 ${data.applied_count} 项规范并立即生效`)
    }
    impDlg.value = false
    load()
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || '导入失败')
  } finally { importing.value = false }
}

onMounted(() => { load(false); loadSeedKeys() })
// keep-alive：切回时重拉（保留原页码）
onActivated(() => { load(false); loadSeedKeys() })
</script>

<!-- .bar / .bar h3 已迁入 theme.css -->
