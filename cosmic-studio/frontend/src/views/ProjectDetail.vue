<template>
  <el-card v-loading="loading">
    <div class="bar">
      <div>
        <el-breadcrumb style="margin-bottom:6px">
          <el-breadcrumb-item :to="{ path: '/projects' }">编写库</el-breadcrumb-item>
          <el-breadcrumb-item>项目详情</el-breadcrumb-item>
        </el-breadcrumb>
        <h3>{{ proj?.requirement_id }} {{ proj?.requirement_name }}</h3>
      </div>
      <div class="bar-actions">
        <el-upload v-if="canEdit" :show-file-list="false" :auto-upload="false" accept=".xlsx"
                   :on-change="onImportFile" style="display:inline-block">
          <el-button type="info" plain>导入</el-button>
        </el-upload>
        <el-button v-if="canEdit" @click="openModDlg">新建模块</el-button>
        <el-button v-if="canEdit" type="primary" plain @click="openFpDlg()">新建功能过程</el-button>
        <el-button :loading="busy === 'derive'" @click="derive(false)">推导检查</el-button>
        <el-button v-if="canEdit" type="warning" plain :loading="busy === 'fix'"
                   @click="derive(true)">一键修复推导列</el-button>
        <el-button type="primary" plain :loading="busy === 'lint'" @click="lint()">质量门禁</el-button>
        <el-button type="success" plain :loading="busy === 'export'" @click="exportXlsx">导出 xlsx</el-button>
        <el-badge :value="pendingReviewCount" :hidden="!pendingReviewCount">
          <el-button type="danger" plain @click="reviewDrawer = true">评审（{{ reviews.length }}）</el-button>
        </el-badge>
      </div>
    </div>

    <el-alert v-if="deriveIssues.length" type="warning" :closable="false" style="margin-bottom:10px">
      推导列差异 {{ deriveIssues.length }} 处：
      <div v-for="(it, i) in deriveIssues.slice(0, 5)" :key="i">
        [{{ it.col }}] {{ it.fp_name }}：{{ (it.actual || '空') }} → {{ it.expected }}
      </div>
    </el-alert>

    <!-- 模块级分页：归档库单个项目可达 251 个模块 / 3780 个 FP，
         此前一次全量读入再把整棵三层树铺进 DOM，页面直接不可用 -->
    <div class="bar" style="align-items:center">
      <div class="bar-actions">
        <el-input v-model="modKw" placeholder="按模块名筛选" style="width:220px" clearable
                  aria-label="按模块名筛选" @keyup.enter="reloadTree" @clear="reloadTree" />
        <span class="muted" v-if="tree?.stats">
          全项目共 {{ tree.stats.module_count }} 模块 / {{ tree.stats.fp_count }} FP / {{ tree.stats.sub_count }} 子过程
        </span>
      </div>
      <el-pagination v-model:current-page="modPage" v-model:page-size="modPageSize"
                     :total="modTotal" :page-sizes="[5, 10, 20, 50]" :disabled="loading"
                     layout="total, sizes, prev, pager, next" background />
    </div>

    <el-table :data="tableRows" row-key="rowKey" border :tree-props="{ children: 'children' }"
              :default-expand-all="false">
      <template #empty>
        <el-empty :description="error || '该项目还没有模块'" />
      </template>
      <el-table-column prop="module" label="三级模块" min-width="140" show-overflow-tooltip />
      <el-table-column prop="fp" label="功能过程" min-width="150" show-overflow-tooltip />
      <el-table-column prop="move" label="类型" width="55" />
      <el-table-column prop="desc" label="子过程描述" min-width="180" show-overflow-tooltip />
      <el-table-column prop="group" label="数据组" min-width="150" show-overflow-tooltip />
      <el-table-column label="评审意见" width="120">
        <template #default="s">
          <template v-if="s.row.reviews && s.row.reviews.length">
            <el-tooltip placement="top" effect="light">
              <template #content>
                <div v-for="r in s.row.reviews" :key="r.id" style="max-width:320px; margin-bottom:4px">
                  <b>{{ { pending: '[待处理]', manual_done: '[已手动修订]', auto_done: '[已AI修订]', needs_manual: '[需人工]', wont_fix: '[不修改]' }[r.disposition] }}</b>
                  {{ r.content }}
                </div>
              </template>
              <el-tag size="small" style="cursor:pointer"
                      :type="s.row.reviewPending ? 'danger' : 'success'"
                      @click="reviewDrawer = true">
                📝 {{ s.row.reviews.length }} 条{{ s.row.reviewPending ? '待处理' : '' }}
              </el-tag>
            </el-tooltip>
          </template>
          <span v-else style="color:#cdd0d6">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="attrs" label="数据属性" min-width="180" show-overflow-tooltip />
      <el-table-column v-if="canEdit" label="操作" width="190" fixed="right">
        <template #default="s">
          <template v-if="s.row.kind === 'fp'">
            <el-button size="small" text type="primary" @click="openSubDlg(s.row)">加子过程</el-button>
            <el-button size="small" text type="success" @click="diversify(s.row)">差异化</el-button>
            <el-button size="small" text type="info" @click="openReview(s.row)">评审</el-button>
            <el-button size="small" text type="danger" @click="delFp(s.row)">删</el-button>
          </template>
          <template v-else-if="s.row.kind === 'sub'">
            <el-button size="small" text type="primary" @click="openSubDlg(s.row)">编辑</el-button>
            <el-button size="small" text type="info" @click="openReview(s.row)">评审</el-button>
            <el-button size="small" text type="danger" @click="delSub(s.row)">删</el-button>
          </template>
          <template v-else-if="s.row.kind === 'module'">
            <el-button size="small" text type="danger" @click="delModule(s.row)">删模块</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建模块 -->
    <el-dialog v-model="modDlg" title="新建模块" width="440px">
      <el-form label-width="80px">
        <el-form-item label="一级模块"><el-input v-model="modForm.level1" /></el-form-item>
        <el-form-item label="二级模块"><el-input v-model="modForm.level2" /></el-form-item>
        <el-form-item label="三级模块">
          <el-input v-model="modForm.level3" placeholder="禁含禁词（记录/日志/列表等）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createModule">创建</el-button>
      </template>
    </el-dialog>

    <!-- 新建功能过程 -->
    <el-dialog v-model="fpDlg" title="新建功能过程" width="480px">
      <el-form label-width="80px">
        <el-form-item label="所属模块">
          <el-select v-model="fpForm.module_id" style="width:100%">
            <el-option v-for="m in modules" :key="m.id" :label="m.level3" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="功能过程名">
          <el-input v-model="fpForm.name" placeholder="动词开头：新增/修改/删除/查询/预览 + 业务对象" />
        </el-form-item>
      </el-form>
      <p class="hint">保存后系统自动推导：F 列功能用户、E 列触发事件、按动词展开 EW/ERX 标准子过程（含描述与数据组名），属性可再用「差异化」从字段池填充。</p>
      <template #footer>
        <el-button @click="fpDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createFp">创建</el-button>
      </template>
    </el-dialog>

    <!-- 子过程 新建/编辑 -->
    <el-dialog v-model="subDlg" :title="subForm.id ? '编辑子过程' : '加子过程'" width="520px">
      <el-form label-width="80px">
        <el-form-item label="FP">
          <el-input :model-value="subForm.fpName" disabled />
        </el-form-item>
        <el-form-item label="移动类型">
          <el-radio-group v-model="subForm.move_type">
            <el-radio-button v-for="t in subForm.allowedMoves" :key="t" :value="t">{{ t }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="子过程描述">
          <el-input v-model="subForm.desc" :placeholder="subForm.id ? '' : '留空自动按模板推导'" />
        </el-form-item>
        <el-form-item label="数据组名">
          <el-input v-model="subForm.group_name" :placeholder="subForm.id ? '' : '留空自动按模板推导'" />
        </el-form-item>
        <el-form-item label="数据属性">
          <el-input v-model="subForm.attributes" type="textarea" :rows="3"
                    placeholder="用、分隔，至少3个字段，如：策略编号、归属省份、生效时间" />
          <div class="hint" :style="attrCount >= 3 ? 'color:#67c23a' : 'color:#e6a23c'">
            当前 {{ attrCount }} 个字段（{{ attrCount >= 3 ? '✓ 满足最低要求' : '至少 3 个' }}，建议 ≥4 并打乱顺序）
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSub">保存</el-button>
      </template>
    </el-dialog>

    <!-- 导入到本项目 -->
    <el-dialog v-model="impDlg" title="导入到本项目" width="520px">
      <el-alert type="info" :closable="false" style="margin-bottom:10px">
        <p style="margin:0">数据将合并到当前项目。增量按「模块+FP名」主键 upsert（命中更新，未命中新建）；覆盖会清空本项目全部数据后重灌（admin，自动备份）。</p>
        <el-link type="primary" style="margin-top:4px" href="/api/active/import/template">下载导入模板（含逐列填写说明）</el-link>
      </el-alert>
      <el-radio-group v-model="impMode" class="imp-modes">
        <el-radio value="incremental">增量合并到本项目（推荐）</el-radio>
        <el-radio value="overwrite" :disabled="!isAdmin">覆盖本项目（清空重灌，admin）</el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="impDlg = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="doImport">确认导入</el-button>
      </template>
    </el-dialog>

    <!-- 评审抽屉 -->
    <el-drawer v-model="reviewDrawer" title="评审意见" size="55%">
      <div style="margin-bottom:10px; display:flex; gap:8px; align-items:center">
        <el-button type="danger" :disabled="!pendingReviewCount" :loading="autoFixing"
                   @click="autoFix">自动优化（{{ pendingReviewCount }} 条待处理）</el-button>
        <span class="hint">一轮处理全部待处理意见，生成一个新版本；结构调整型意见由 AI 出方案、人工确认</span>
      </div>
      <div v-for="rv in reviews" :key="rv.id" class="review-item">
        <div class="rv-head">
          <el-tag size="small" :type="rv.target_type === 'sub' ? 'primary' : rv.target_type === 'fp' ? 'warning' : 'info'">
            {{ rv.target_type === 'sub' ? '子过程' : rv.target_type === 'fp' ? '功能过程' : '整表' }}#{{ rv.target_id }}
          </el-tag>
          <span class="rv-label">{{ rv.target_label || ('#' + rv.target_id) }}</span>
          <el-tag size="small" :type="rv.disposition === 'pending' ? 'info' : rv.disposition === 'manual_done' ? 'success' : rv.disposition === 'auto_done' ? 'success' : rv.disposition === 'needs_manual' ? 'warning' : 'info'">
            {{ { pending: '待处理', manual_done: '已手动修订', auto_done: '已AI修订', needs_manual: '需人工', wont_fix: '不修改' }[rv.disposition] }}
          </el-tag>
          <el-tag v-if="rv.classify === 'structure'" size="small" type="danger">结构调整型</el-tag>
          <span class="spacer" />
        </div>
        <div class="rv-content">{{ rv.content }}</div>
        <div v-if="rv.revision_note" class="hint">修订说明：{{ rv.revision_note }}</div>
        <div v-if="rv.version_id" class="hint">修订版本：v（快照 #{{ rv.version_id }}，版本管理页可下载）</div>
        <div style="margin-top:6px" v-if="rv.disposition === 'pending'">
          <el-button size="small" type="primary" @click="gotoFix(rv)">去修改</el-button>
          <el-button size="small" @click="wontFix(rv)">不修改（记理由）</el-button>
          <el-button v-if="isAdmin" size="small" text type="danger" @click="delReview(rv)">删除</el-button>
        </div>
      </div>
      <el-empty v-if="!reviews.length" description="暂无评审意见：在表格行点「评审」录入，行级挂载" />
    </el-drawer>

    <!-- 录入评审意见 -->
    <el-dialog v-model="reviewDlg" title="录入评审意见" width="520px">
      <p class="hint">挂载行：{{ reviewForm.target_label || '整表' }}</p>
      <el-input v-model="reviewForm.content" type="textarea" :rows="3"
                placeholder="评审意见，如：这条子过程描述含断句，请拆成两句；该数据属性含PII字段，请替换" />
      <el-radio-group v-model="reviewForm.classify" style="margin-top:10px">
        <el-radio value="text_replace">文本替换型（可直接改）</el-radio>
        <el-radio value="structure">结构调整型（需人工确认）</el-radio>
        <el-radio value="question">内容质疑型</el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="reviewDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReview">提交</el-button>
      </template>
    </el-dialog>

    <!-- FP 编辑（评审「去修改」入口） -->
    <el-dialog v-model="fpEditDlg" title="编辑功能过程" width="480px">
      <el-form label-width="80px">
        <el-form-item label="功能过程名"><el-input v-model="fpEditForm.name" /></el-form-item>
        <el-form-item label="功能用户">
          <el-input v-model="fpEditForm.user" type="textarea" :rows="2" placeholder="发起者：X\n接收者：Y" />
        </el-form-item>
        <el-form-item label="触发事件"><el-input v-model="fpEditForm.event" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fpEditDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveFpEdit">保存（自动出新版本）</el-button>
      </template>
    </el-dialog>

    <!-- 门禁报告 -->
    <el-dialog v-model="lintDlg" title="质量门禁报告" width="860px" top="6vh">
      <el-alert v-if="lintReport.summary" :type="lintReport.summary.pass ? 'success' : 'error'"
                :closable="false"
                :title="`错误 ${lintReport.summary.error} / 警告 ${lintReport.summary.warn}`" />
      <el-table :data="lintReport.list" size="small" max-height="420" style="margin-top:10px">
        <el-table-column prop="check" label="检查项" width="110" />
        <el-table-column label="级别" width="70">
          <template #default="s">
            <el-tag size="small" effect="plain"
                    :type="s.row.level === 'error' ? 'danger' : 'warning'">
              {{ s.row.level === 'error' ? '错误' : '警告' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ref" label="位置" min-width="150" show-overflow-tooltip />
        <el-table-column prop="message" label="问题" min-width="300" show-overflow-tooltip />
      </el-table>
      <p class="hint" v-if="lintReport.total > lintReport.list?.length">
        共 {{ lintReport.total }} 条，此处展示前 {{ lintReport.list?.length }} 条；完整列表见「质量门禁」页。
      </p>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api, { role } from '../api'

const route = useRoute()
const pid = route.params.id
const proj = ref(null)
const tree = ref(null)
const loading = ref(false)
const error = ref('')

// 模块级分页（归档库单项目可达 251 模块 / 3780 FP，必须分页）
const modPage = ref(1)
const modPageSize = ref(10)
const modTotal = ref(0)
const modKw = ref('')

const deriveIssues = ref([])
const lintDlg = ref(false)
const lintReport = ref({ list: [], summary: {}, counts: {} })
const busy = ref('')
const saving = ref(false)
const canEdit = computed(() => ['admin', 'editor'].includes(role()))
const impDlg = ref(false)
const impMode = ref('incremental')
const impFile = ref(null)
const importing = ref(false)

function onImportFile(f) {
  impFile.value = f.raw
  impMode.value = 'incremental'
  impDlg.value = true
}
async function doImport() {
  const q = impMode.value === 'overwrite'
    ? `mode=overwrite&project_id=${pid}&confirm=active`
    : `mode=incremental&project_id=${pid}`
  const fd = new FormData()
  fd.append('file', impFile.value)
  importing.value = true
  try {
    const { data } = await api.post(`/active/import/xlsx?${q}`, fd)
    ElMessage.success(`导入完成：模块${data.modules} FP${data.fps} 子过程${data.subs}${data.backup ? '（已自动备份）' : ''}`)
    impDlg.value = false
    impFile.value = null
    load()
  } catch (e) { errMsg(e, '导入失败') } finally { importing.value = false }
}

const modDlg = ref(false)
const modForm = reactive({ level1: '', level2: '', level3: '' })
const fpDlg = ref(false)
const fpForm = reactive({ module_id: null, name: '' })
const subDlg = ref(false)
const subForm = reactive({ id: null, fpId: null, fpName: '', review_id: null, move_type: 'E', desc: '', group_name: '', attributes: '', allowedMoves: ['E', 'W', 'R', 'X'] })

// 「新建功能过程」要能选到任意模块，不能只给当前页 —— 单独拉一份全量模块清单
const allModules = ref([])
async function loadAllModules() {
  try {
    const { data } = await api.get(`/active/projects/${pid}/tree`,
      { params: { module_page: 1, module_page_size: 100 } })
    allModules.value = (data.modules || []).map(m => ({ id: m.id, level3: m.level3 }))
  } catch { /* ignore */ }
}
const modules = computed(() => allModules.value.length
  ? allModules.value
  : (tree.value?.modules?.map(m => ({ id: m.id, level3: m.level3 })) || []))
const attrCount = computed(() => subForm.attributes.split('、').filter(f => f.trim()).length)

const tableRows = computed(() => {
  if (!tree.value) return []
  // 评审意见按行索引：sub→子过程 id，fp→FP id（project 级意见不挂行）
  const byTarget = {}
  for (const r of reviews.value) {
    if (r.target_type === 'project') continue
    const key = `${r.target_type}-${r.target_id}`
    ;(byTarget[key] ||= []).push(r)
  }
  const attach = (kind, id) => {
    const list = byTarget[`${kind}-${id}`] || []
    return { reviews: list, reviewPending: list.some(r => r.disposition === 'pending') }
  }
  const rows = []
  tree.value.modules?.forEach(m => {
    const mkey = `m${m.id}`
    rows.push({
      rowKey: mkey, kind: 'module', id: m.id, module: m.level3,
      fp: '', move: '', desc: '', group: '', attrs: '',
      children: m.fps.flatMap(f => {
        const fkey = `${mkey}-f${f.id}`
        return [{
          rowKey: fkey, kind: 'fp', id: f.id, moduleId: m.id, module: m.level3,
          fp: f.fp_name, move: '', desc: f.trigger_event, group: '', attrs: '',
          fu: f.functional_user,
          ...attach('fp', f.id),
          children: f.subs.map(s => ({
            rowKey: `${fkey}-s${s.id}`, kind: 'sub', id: s.id, fpId: f.id,
            module: m.level3, fp: f.fp_name, move: s.data_move_type,
            desc: s.description, group: s.data_group_name, attrs: s.data_attributes,
            ...attach('sub', s.id)
          }))
        }]
      })
    })
  })
  return rows
})

// ── 评审 ──
const reviews = ref([])
const reviewDrawer = ref(false)
const reviewDlg = ref(false)
const reviewForm = reactive({ target_type: 'project', target_id: null, target_label: '', content: '', classify: 'text_replace', review_id: null })
const autoFixing = ref(false)
const fpEditDlg = ref(false)
const fpEditForm = reactive({ id: null, name: '', user: '', event: '', review_id: null })
const pendingReviewCount = computed(() => reviews.value.filter(r => r.disposition === 'pending').length)

async function loadReviews() {
  reviews.value = (await api.get(`/active/projects/${pid}/reviews`)).data
}
function openReview(row) {
  Object.assign(reviewForm, {
    target_type: row.kind, target_id: row.id,
    target_label: row.kind === 'fp' ? row.fp : (row.desc || row.fp || ''),
    content: '', classify: 'text_replace', review_id: null
  })
  reviewDlg.value = true
}
async function submitReview() {
  if (!reviewForm.content.trim()) { ElMessage.warning('请填写评审意见'); return }
  await api.post(`/active/projects/${pid}/reviews`, reviewForm)
  ElMessage.success('评审意见已录入')
  reviewDlg.value = false
  loadReviews()
}
function gotoFix(rv) {
  // 打开对应行编辑框，保存后自动版本化并关闭意见
  if (rv.target_type === 'sub') {
    const row = findSubRow(rv.target_id)
    if (!row) { ElMessage.error('未找到该子过程（可能已删除）'); return }
    openSubDlg(row, rv.id)
    reviewDrawer.value = false
  } else if (rv.target_type === 'fp') {
    const row = findFpRow(rv.target_id)
    if (!row) { ElMessage.error('未找到该功能过程（可能已删除）'); return }
    Object.assign(fpEditForm, { id: row.id, name: row.fp, user: row.fu || '', event: row.desc || '', review_id: rv.id })
    fpEditDlg.value = true
    reviewDrawer.value = false
  } else {
    ElMessage.info('整表级意见请在对应行编辑后手动标记')
  }
}
function findSubRow(sid) {
  for (const g of tableRows.value) for (const f of (g.children || [])) for (const s of (f.children || []))
    if (s.kind === 'sub' && s.id === sid) return s
  return null
}
function findFpRow(fid) {
  for (const g of tableRows.value) for (const f of (g.children || []))
    if (f.kind === 'fp' && f.id === fid) return f
  return null
}
async function wontFix(rv) {
  const { value } = await ElMessageBox.prompt('不修改的理由（将记入意见处置）', '不修改', { inputValue: '' })
  await api.put(`/active/reviews/${rv.id}`, { disposition: 'wont_fix', revision_note: value || '评审后决定不修改' })
  loadReviews()
}
async function delReview(rv) {
  await ElMessageBox.confirm('删除该评审意见？', '确认', { type: 'warning' })
  await api.delete(`/active/reviews/${rv.id}`)
  loadReviews()
}
async function autoFix() {
  await ElMessageBox.confirm(
    `将把 ${pendingReviewCount.value} 条待处理意见一次性交给 LLM 优化对应行，全部通过门禁后生成一个新版本。继续？`,
    '批次自动优化', { type: 'warning' })
  autoFixing.value = true
  try {
    const { data } = await api.post(`/active/projects/${pid}/reviews/auto-fix`, {})
    const applied = data.applied?.length || 0
    const skipped = data.skipped?.length || 0
    ElMessage.success(`自动优化完成：修订 ${applied} 处，跳过 ${skipped} 处${data.version ? `，新版本 ${data.version.label}` : ''}（版本管理页可查看/下载）`)
    loadReviews()
  } catch (e) {
    const d = e.response?.data
    ElMessage.error(typeof d === 'string' ? d : (d?.detail?.message || d?.detail || '自动优化失败'))
  } finally { autoFixing.value = false }
}

// ── FP 编辑（手动修订） ──
async function saveFpEdit() {
  saving.value = true
  try {
    await api.put(`/active/fps/${fpEditForm.id}`, {
      name: fpEditForm.name || undefined, user: fpEditForm.user || undefined, event: fpEditForm.event || undefined
    })
    if (fpEditForm.review_id) {
      const { data } = await api.post(`/active/reviews/${fpEditForm.review_id}/manual-done`,
        { revision_note: '手动修订 FP 行' })
      ElMessage.success(`已保存并生成新版本 ${data.version.label}，评审意见已关闭`)
    } else {
      ElMessage.success('已保存')
    }
    fpEditDlg.value = false
    load(); loadReviews()
  } catch (e) { errMsg(e, '保存失败') } finally { saving.value = false }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [t] = await Promise.all([
      api.get(`/active/projects/${pid}/tree`, {
        params: {
          module_page: modPage.value,
          module_page_size: modPageSize.value,
          keyword: modKw.value || undefined,
        },
      }),
      loadReviews(),
    ])
    tree.value = t.data
    modTotal.value = t.data.total ?? 0
    // 项目信息直接从树响应里取，不再单独拉全量项目列表（分页后可能不在第 1 页）
    proj.value = t.data.project || null
    await loadAllModules()
  } catch (e) {
    error.value = e?.response?.data?.detail || '加载失败'
    tree.value = null
  } finally { loading.value = false }
}
function reloadTree() {
  if (modPage.value === 1) load()
  else modPage.value = 1
}
// 翻页 / 改页大小 / 改筛选都走同一条加载路径
watch([modPage, modPageSize], load)

// ── 模块 ──
function openModDlg() {
  const first = modules.value[0]
  modForm.level1 = first?.level1_hint || modForm.level1
  modDlg.value = true
}
async function createModule() {
  if (!modForm.level1.trim() || !modForm.level2.trim() || !modForm.level3.trim()) {
    ElMessage.warning('三级模块名称均为必填'); return
  }
  saving.value = true
  try {
    await api.post(`/active/projects/${pid}/modules`, modForm)
    ElMessage.success('模块已创建')
    modDlg.value = false
    load()
  } catch (e) { errMsg(e, '创建失败') } finally { saving.value = false }
}
async function delModule(row) {
  await ElMessageBox.confirm(`删除模块「${row.module}」及其下全部功能过程？`, '删除确认', { type: 'warning' })
  try {
    await api.delete(`/active/modules/${row.id}?cascade=true`)
    ElMessage.success('已删除'); load()
  } catch (e) { errMsg(e, '删除失败') }
}

// ── FP ──
function openFpDlg() {
  if (!modules.value.length) { ElMessage.warning('请先创建模块'); return }
  fpForm.module_id = modules.value[0].id
  fpForm.name = ''
  fpDlg.value = true
}
async function createFp() {
  if (!fpForm.name.trim()) { ElMessage.warning('功能过程名必填'); return }
  saving.value = true
  try {
    const { data } = await api.post(`/active/projects/${pid}/fps`, fpForm)
    ElMessage.success(`已创建并自动推导：${(data.user || '').split('\n')[0]} | ${data.event || ''}`)
    fpDlg.value = false
    load()
  } catch (e) { errMsg(e, '创建失败') } finally { saving.value = false }
}
async function delFp(row) {
  await ElMessageBox.confirm(`删除功能过程「${row.fp}」及其全部子过程？`, '删除确认', { type: 'warning' })
  try {
    await api.delete(`/active/fps/${row.id}?cascade=true`)
    ElMessage.success('已删除'); load()
  } catch (e) { errMsg(e, '删除失败') }
}

// ── 子过程 ──
function openSubDlg(row, reviewId = null) {
  if (row.kind === 'fp') {
    // 新建：按 FP 动词限定可选拳位
    const verb = row.fp.slice(0, 2)
    const ewx = { '新增': ['E', 'W'], '修改': ['E', 'W'], '删除': ['E', 'W'], '查询': ['E', 'R', 'X'], '预览': ['E', 'R', 'X'] }
    Object.assign(subForm, {
      id: null, fpId: row.id, fpName: row.fp, review_id: reviewId,
      move_type: (ewx[verb] || ['E', 'W'])[0],
      allowedMoves: ewx[verb] || ['E', 'W', 'R', 'X'],
      desc: '', group_name: '', attributes: ''
    })
  } else {
    Object.assign(subForm, {
      id: row.id, fpId: row.fpId, fpName: row.fp, review_id: reviewId, move_type: row.move,
      desc: row.desc, group_name: row.group, attributes: row.attrs,
      allowedMoves: ['E', 'W', 'R', 'X']
    })
  }
  subDlg.value = true
}
async function saveSub() {
  const fields = subForm.attributes.split('、').filter(f => f.trim())
  if (fields.length < 3) { ElMessage.warning('数据属性至少 3 个字段'); return }
  if (subForm.attributes.includes(',')) { ElMessage.warning('分隔符必须用「、」不能用逗号'); return }
  saving.value = true
  try {
    if (subForm.id) {
      await api.put(`/active/subs/${subForm.id}`, {
        description: subForm.desc, data_move_type: subForm.move_type,
        data_group_name: subForm.group_name, data_attributes: subForm.attributes
      })
      if (subForm.review_id) {
        const { data } = await api.post(`/active/reviews/${subForm.review_id}/manual-done`,
          { revision_note: '手动修订子过程行' })
        ElMessage.success(`已保存并生成新版本 ${data.version.label}，评审意见已关闭`)
      } else {
        ElMessage.success('已保存')
      }
    } else {
      const { data } = await api.post(`/active/fps/${subForm.fpId}/subs`, {
        move_type: subForm.move_type, desc: subForm.desc || null,
        group_name: subForm.group_name || null, attributes: subForm.attributes
      })
      ElMessage.success(`已添加：${data.description}`)
    }
    subDlg.value = false
    load(); loadReviews()
  } catch (e) { errMsg(e, '保存失败') } finally { saving.value = false }
}
async function delSub(row) {
  await ElMessageBox.confirm(`删除子过程「${(row.desc || '').slice(0, 30)}」？`, '删除确认', { type: 'warning' })
  try {
    await api.delete(`/active/subs/${row.id}`)
    ElMessage.success('已删除'); load()
  } catch (e) { errMsg(e, '删除失败') }
}
async function diversify(row) {
  try {
    await api.post(`/active/fps/${row.id}/diversify`)
    ElMessage.success('已按字段池差异化填充属性')
    load()
  } catch (e) { errMsg(e, '差异化失败') }
}

// ── 推导/门禁/导出 ──
async function derive(fix) {
  busy.value = fix ? 'fix' : 'derive'
  try {
    const { data } = await api.post(`/active/projects/${pid}/derive?fix=${fix}`)
    deriveIssues.value = data.issues
    ElMessage[data.count ? 'warning' : 'success'](data.fixed ? `修复完成，剩余 ${data.count} 处` : `发现 ${data.count} 处差异`)
    if (fix) load()
  } catch (e) { errMsg(e, '失败') } finally { busy.value = '' }
}
async function lint() {
  busy.value = 'lint'
  try {
    // 弹窗只做概览，取前 100 条即可；完整分页列表走「质量门禁」页
    const { data } = await api.get(`/active/projects/${pid}/lint`,
      { params: { page: 1, page_size: 100 } })
    lintReport.value = data
    lintDlg.value = true
  } catch (e) { errMsg(e, '检查失败') } finally { busy.value = '' }
}
async function exportXlsx() {
  busy.value = 'export'
  window.open(`/api/active/projects/${pid}/export/xlsx`, '_blank')
  setTimeout(() => { busy.value = '' }, 1500)
}
function errMsg(e, fallback) {
  const d = e.response?.data?.detail
  ElMessage.error(typeof d === 'string' ? d : fallback)
}

onMounted(load)
</script>

<style scoped>
/* .bar / .bar h3 / .hint 已迁入 theme.css，色值统一走设计令牌 */
.review-item { border: 1px solid var(--c-border); border-radius: var(--r-md);
  padding: var(--sp-3); margin-bottom: var(--sp-3); }
.rv-head { display: flex; align-items: center; gap: var(--sp-2); }
.rv-head .spacer { flex: 1; }
.rv-label { color: var(--c-text-2); font-size: var(--fs-base); flex: 1;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rv-content { background: var(--c-surface-3); border-radius: var(--r-sm);
  padding: var(--sp-2); margin: var(--sp-2) 0 var(--sp-1);
  font-size: var(--fs-base); line-height: var(--lh); }
</style>
