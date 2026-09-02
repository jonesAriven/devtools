<template>
  <el-card>
    <div class="bar">
      <h3>版本管理</h3>
      <div class="bar-actions">
        <el-select v-model="pid" placeholder="选择编写库需求" style="width:300px" filterable
                   aria-label="选择编写库需求">
          <el-option v-for="p in projects" :key="p.id" :label="`${p.requirement_id} ${p.requirement_name?.slice(0,24)}`" :value="p.id" />
        </el-select>
        <el-button :disabled="!pid" @click="load">刷新</el-button>
        <el-button v-if="selVersions.length === 2" @click="openDiff">对比所选版本</el-button>
        <el-button v-if="['admin','editor'].includes(roleName)" type="primary" :disabled="!pid"
                   :loading="snapping" @click="snapDlg = true">创建版本快照</el-button>
      </div>
    </div>
    <el-table :data="pageList" v-loading="loading"
              @selection-change="v => (selVersions = v.slice(-2))">
      <el-table-column type="selection" width="42" />
      <template #empty><el-empty :description="pid ? '该需求还没有版本快照' : '请先选择需求'" /></template>
      <el-table-column prop="seq" label="版本" width="80">
        <template #default="s">v{{ s.row.seq }}</template>
      </el-table-column>
      <el-table-column prop="label" label="标签" width="110" />
      <el-table-column prop="sha256" label="SHA256" min-width="200" show-overflow-tooltip />
      <el-table-column prop="file_size" label="大小" width="90">
        <template #default="s">{{ (s.row.file_size / 1024).toFixed(1) }} KB</template>
      </el-table-column>
      <el-table-column prop="changelog" label="变更说明" min-width="180" show-overflow-tooltip />
      <el-table-column prop="created_at" label="时间" width="170" :formatter="formatDateTime" />
      <el-table-column label="操作" width="100">
        <template #default="s">
          <el-button size="small" @click="download(s.row.id)">下载</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
                     :total="total" :page-sizes="[10, 20, 50]" background
                     layout="total, sizes, prev, pager, next" />
    </div>

    <!-- 版本对比：旧(勾选序靠前) → 新 -->
    <el-dialog v-model="diffDlg" title="版本对比" width="860px" top="5vh">
      <div v-if="diffData">
        <p class="muted" style="margin-top:0">
          {{ diffData.a?.label || ('#' + diffData.a?.id) }}（{{ diffData.a?.created_at }}）
          →
          {{ diffData.b?.label || ('#' + diffData.b?.id) }}（{{ diffData.b?.created_at }}）</p>
        <div class="stat-row">
          <div class="stat"><span class="k">新增 FP</span><span class="v" style="color:var(--c-success)">{{ diffData.summary.fps_added }}</span></div>
          <div class="stat"><span class="k">删除 FP</span><span class="v" style="color:var(--c-danger)">{{ diffData.summary.fps_removed }}</span></div>
          <div class="stat"><span class="k">修改 FP</span><span class="v" style="color:var(--c-warning)">{{ diffData.summary.fps_modified }}</span></div>
          <div class="stat"><span class="k">新增子过程</span><span class="v" style="color:var(--c-success)">{{ diffData.summary.subs_added }}</span></div>
          <div class="stat"><span class="k">删除子过程</span><span class="v" style="color:var(--c-danger)">{{ diffData.summary.subs_removed }}</span></div>
          <div class="stat"><span class="k">修改子过程</span><span class="v" style="color:var(--c-warning)">{{ diffData.summary.subs_modified }}</span></div>
        </div>
        <el-tabs>
          <el-tab-pane label="FP 变更">
            <div v-if="!diffData.fps_added.length && !diffData.fps_removed.length && !diffData.fps_modified.length">
              <el-empty description="FP 层无变更" :image-size="60" />
            </div>
            <div v-for="f in diffData.fps_added" :key="'fa' + f.fp_name" class="diff-line">
              <el-tag size="small" type="success">新增</el-tag> {{ f.fp_name }}（{{ f.module }}）
            </div>
            <div v-for="f in diffData.fps_removed" :key="'fr' + f.fp_name" class="diff-line">
              <el-tag size="small" type="danger">删除</el-tag> {{ f.fp_name }}（{{ f.module }}）
            </div>
            <div v-for="f in diffData.fps_modified" :key="'fm' + f.fp_name" class="diff-line">
              <el-tag size="small" type="warning">修改</el-tag> <b>{{ f.fp_name }}</b>
              <div v-for="d in f.diffs" :key="d.field" class="diff-detail">
                {{ d.field }}："<span class="old-val">{{ d.old || '（空）' }}</span>" → "<span class="new-val">{{ d.new || '（空）' }}</span>"
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane :label="`子过程变更（${diffData.summary.subs_added + diffData.summary.subs_removed + diffData.summary.subs_modified}）`">
            <div v-if="!diffData.subs_added.length && !diffData.subs_removed.length && !diffData.subs_modified.length">
              <el-empty description="子过程层无变更" :image-size="60" />
            </div>
            <div v-for="(x, i) in diffData.subs_added" :key="'sa' + i" class="diff-line">
              <el-tag size="small" type="success">新增</el-tag> [{{ x.move_type }}] {{ x.fp }} / {{ x.description }}
            </div>
            <div v-for="(x, i) in diffData.subs_removed" :key="'sr' + i" class="diff-line">
              <el-tag size="small" type="danger">删除</el-tag> [{{ x.move_type }}] {{ x.fp }} / {{ x.description }}
            </div>
            <div v-for="(x, i) in diffData.subs_modified" :key="'sm' + i" class="diff-line">
              <el-tag size="small" type="warning">修改</el-tag> [{{ x.move_type }}] {{ x.fp }} / {{ x.description }}
              <div v-for="d in x.diffs" :key="d.field" class="diff-detail">
                {{ d.field }}："<span class="old-val">{{ d.old || '（空）' }}</span>" → "<span class="new-val">{{ d.new || '（空）' }}</span>"
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="模块变更">
            <div v-if="!diffData.modules_added.length && !diffData.modules_removed.length">
              <el-empty description="模块层无变更" :image-size="60" />
            </div>
            <div v-for="m in diffData.modules_added" :key="'ma' + m" class="diff-line">
              <el-tag size="small" type="success">新增</el-tag> {{ m }}
            </div>
            <div v-for="m in diffData.modules_removed" :key="'mr' + m" class="diff-line">
              <el-tag size="small" type="danger">删除</el-tag> {{ m }}
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
      <template #footer>
        <el-button @click="diffDlg = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="snapDlg" title="创建版本快照" width="440px">
      <el-form label-width="80px">
        <el-form-item label="标签"><el-input v-model="snap.label" placeholder="留空自动 vN" /></el-form-item>
        <el-form-item label="变更说明"><el-input v-model="snap.changelog" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="snapDlg = false">取消</el-button>
        <el-button type="primary" @click="snapshot">创建</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onActivated, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api, { downloadBlob } from '../api'
import { useLocalPaged } from '../composables/usePaged'
import { usePersistentState } from '../composables/usePersistentState'
import { formatDateTime } from '../utils/format'

const projects = ref([])
const pid = usePersistentState('pid', null)
const allVersions = ref([])
const loading = ref(false)
const snapDlg = ref(false)
const snap = reactive({ label: '', changelog: '' })
const snapping = ref(false)
const selVersions = ref([])
const diffDlg = ref(false)
const diffData = ref(null)
const roleName = ref(JSON.parse(localStorage.getItem('user') || '{}').role)

// 版本量级有限，客户端切片
const { list: pageList, total, page, pageSize } = useLocalPaged(allVersions, 20)

onMounted(async () => {
  // /active/projects 已改成分页体 {list,total,...}，不能再用裸数组
  const { data } = await api.get('/active/projects', { params: { page: 1, page_size: 100 } })
  projects.value = data.list ?? []
  // pid 是持久化的：切回本菜单要自动补回版本列表，并留在上次的页码
  if (pid.value) load(false)
})
// 选中项目后自动加载（无需再手动点刷新）；用户主动切换才回第 1 页
watch(pid, () => load(true))
// keep-alive：从别的菜单切回时刷一次项目列表与版本（保持原页码）
onActivated(async () => {
  const { data } = await api.get('/active/projects', { params: { page: 1, page_size: 100 } })
  projects.value = data.list ?? []
  if (pid.value) load(false)
})
// resetPage：主动切项目时回第 1 页；切走再切回来要留在原页码
// （模板里 @click="load" 会传入 MouseEvent，非 false 即按 resetPage=true 处理）
async function load(resetPage = true) {
  if (!pid.value) return
  loading.value = true
  try {
    allVersions.value = (await api.get(`/active/projects/${pid.value}/versions`)).data
    if (resetPage) page.value = 1
  } finally { loading.value = false }
}
async function snapshot() {
  snapping.value = true
  try {
    const { data } = await api.post(`/active/projects/${pid.value}/versions`, snap)
    ElMessage.success(`已创建 ${data.label}（sha256 ${data.sha256.slice(0, 12)}…）`)
    snapDlg.value = false
    snap.label = ''; snap.changelog = ''
    load()
  } catch (e) { ElMessage.error(e.response?.data?.detail || '失败') }
  finally { snapping.value = false }
}
// 对比勾选的两个版本：勾选序靠前的是旧版本（selection-change 里 slice(-2) 保序）
async function openDiff() {
  const [x, y] = selVersions.value
  // seq 小的是旧版本
  const [a, b] = x.seq <= y.seq ? [x, y] : [y, x]
  loading.value = true
  try {
    const { data } = await api.get(`/active/versions/${a.id}/diff/${b.id}`)
    diffData.value = data
    diffDlg.value = true
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || '对比失败')
  } finally { loading.value = false }
}
// 必须带 Bearer 下载：window.open 裸 GET 会被 require_role 拦截返 401 未登录
async function download(id) {
  try {
    await downloadBlob(`/api/active/versions/${id}/download`, `version_${id}.xlsx`)
  } catch (e) {
    ElMessage.error(e.message || '下载失败')
  }
}
</script>

<!-- .bar / .bar h3 已迁入 theme.css -->
<style scoped>
.diff-line { padding: 5px 0; border-bottom: 1px dashed var(--c-border); }
.diff-detail { margin: 3px 0 3px 52px; color: var(--c-text-2); font-size: var(--fs-sm); }
.old-val { color: var(--c-danger); text-decoration: line-through; }
.new-val { color: var(--c-success); }
</style>
