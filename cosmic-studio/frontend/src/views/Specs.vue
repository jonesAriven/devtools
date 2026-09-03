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
        <el-form-item label="规范键">
          <el-select v-model="createForm.spec_key" filterable
                     placeholder="从目录选择规范类型" style="width:100%"
                     @change="onKeyPicked">
            <el-option v-for="k in keySuggestions" :key="k.value" :value="k.value"
                       :label="k.label" :disabled="k.exists">
              <span>{{ k.label }}</span>
              <span v-if="k.exists" class="muted">（已存在）</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="createForm.description" /></el-form-item>
        <el-form-item label="值(JSON)">
          <el-input v-model="createForm.valueText" type="textarea" :rows="6" spellcheck="false"
                    style="font-family: monospace" />
        </el-form-item>
      </el-form>
      <el-alert v-if="pickedMeta" type="info" :closable="false" style="margin-top:8px">
        <p style="margin:0"><b>用途：</b>{{ pickedMeta.usage }}</p>
      </el-alert>
      <p class="muted" style="margin:6px 0 0">选中键后自动预填说明与值模板，改成你们团队的实际内容即可；后续可随导出/导入迁移，对话助手可随时查询。</p>
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
import { computed, onActivated, onMounted, ref } from 'vue'
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
const customKeys = ref([])
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
    const all = data.specs || {}
    seedKeys.value = Object.keys(all).filter(k => all[k]._source === 'seed')
    customKeys.value = Object.keys(all).filter(k => all[k]._source !== 'seed')
  } catch { /* 非阻塞：仅影响按钮文案 */ }
}

// 新增对话框的规范键目录：每键带用途说明与值模板——回答「该造什么、造了怎么用」。
// 消费场景：quality_checklist / review_guidelines 会显示在需求详情的评审抽屉顶部，
// 供评审人对照；全部自定义规范可被对话助手 get_spec 查询、随导出/导入迁移。
const KEY_CATALOG = [
  {
    value: 'quality_checklist', label: 'quality_checklist（质量检查清单）',
    description: '人工评审逐项对照的检查清单', usage: '显示在需求详情「评审」抽屉顶部，评审时逐项对照',
    valueTemplate: JSON.stringify([
      'FP 名动词开头且无禁词', '每个 FP 的 EWX 子过程齐全', '数据属性 ≥3 且来自字段池',
      '触发事件格式：{发起者}{FP名}时触发',
    ], null, 2),
  },
  {
    value: 'review_guidelines', label: 'review_guidelines（评审指引）',
    description: '评审流程与判定标准说明', usage: '显示在需求详情「评审」抽屉顶部，新评审人入门对照',
    valueTemplate: JSON.stringify({
      流程: '先看门禁报告 → 逐 FP 对照检查清单 → 意见写在对应行',
      判定: '禁词/格式类必须改；相似度 65%-75% 酌情；口径类找业务确认',
    }, null, 2),
  },
  {
    value: 'naming_conventions', label: 'naming_conventions（命名规范）',
    description: 'FP 名/数据属性命名约定', usage: '编写与评审时对照；对话助手可查询',
    valueTemplate: JSON.stringify({
      fp_name: '动词开头（新增/修改/删除/查询/预览），业务对象用词库术语',
      attributes: '名词短语、顿号分隔、来自字段池',
    }, null, 2),
  },
  {
    value: 'delivery_requirements', label: 'delivery_requirements（交付要求）',
    description: '交付物格式/版本命名/验收标准', usage: '打版本快照与交付时对照；对话助手可查询',
    valueTemplate: JSON.stringify({ 版本标签: 'v{序号}-{里程碑名}', 验收: '门禁 error=0 方可交付' }, null, 2),
  },
  {
    value: 'measurement_guides', label: 'measurement_guides（度量指引）',
    description: 'COSMIC 度量口径答疑与案例', usage: '编写争议时对照；对话助手可查询',
    valueTemplate: JSON.stringify({ 口径: '数据移动按 E/W/R/X 判定；引用码表算 R', 案例: '……' }, null, 2),
  },
  {
    value: 'team_conventions', label: 'team_conventions（团队约定）',
    description: '以上之外的其他团队约定', usage: '团队内部共识沉淀；对话助手可查询',
    valueTemplate: JSON.stringify({ 约定: '……' }, null, 2),
  },
]
const pickedMeta = computed(() => KEY_CATALOG.find(k => k.value === createForm.value.spec_key))
function onKeyPicked(key) {
  const meta = KEY_CATALOG.find(k => k.value === key)
  if (!meta) return
  // 模板只做预填：若用户已改过内容则不覆盖（仅在说明/值为空或等于其他模板时覆盖，简化为：总是覆盖值模板、说明留用户输入）
  if (!createForm.value.description) createForm.value.description = meta.description
  createForm.value.valueText = meta.valueTemplate
}
const keySuggestions = computed(() => {
  const items = KEY_CATALOG.map(k => ({ ...k, exists: customKeys.value.includes(k.value) }))
  for (const k of customKeys.value) {
    if (!items.some(i => i.value === k)) items.push({ value: k, label: k, exists: true })
  }
  return items
})
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
  try {
    await ElMessageBox.confirm(
      isSeed ? `确认把「${row.spec_key}」还原为内置默认值？（当前自定义修改将丢弃）`
             : `确认删除自定义规范「${row.spec_key}」？此操作不可恢复。`,
      isSeed ? '还原确认' : '删除确认', { type: 'warning' })
  } catch { return }
  await api.delete(`/studio/specs/${row.spec_key}`)
  ElMessage.success(isSeed ? '已还原默认' : '已删除')
  load()
}

function openCreate() {
  createForm.value = { spec_key: '', description: '', valueText: '{}' }
  loadSeedKeys()
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
