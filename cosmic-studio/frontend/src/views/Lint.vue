<template>
  <el-card>
    <div class="bar">
      <h3>质量门禁</h3>
      <div class="bar-actions">
        <el-select v-model="pid" placeholder="选择编写库项目" style="width:300px" filterable
                   aria-label="选择编写库项目">
          <el-option v-for="p in projects" :key="p.id"
                     :label="`${p.requirement_id} ${p.requirement_name?.slice(0, 24)}`"
                     :value="p.id" />
        </el-select>
        <el-button type="primary" :disabled="!pid" :loading="running" @click="run">执行全量检查</el-button>
      </div>
    </div>

    <template v-if="report.summary">
      <el-alert :type="report.summary.pass ? 'success' : 'error'" :closable="false"
                :title="report.summary.pass
                  ? '✅ 全部通过'
                  : `❌ 错误 ${report.summary.error} 项 / 警告 ${report.summary.warn} 项`" />

      <div class="bar" style="margin-top:var(--sp-3)">
        <div class="bar-actions">
          <el-radio-group v-model="severity" @change="reload">
            <el-radio-button value="">全部 {{ totalAll }}</el-radio-button>
            <el-radio-button value="error">错误 {{ report.counts?.error ?? 0 }}</el-radio-button>
            <el-radio-button value="warn">警告 {{ report.counts?.warn ?? 0 }}</el-radio-button>
          </el-radio-group>
        </div>
        <el-input v-model="kw" placeholder="过滤检查项 / 位置 / 问题" style="width:260px" clearable
                  aria-label="过滤检查结果" @keyup.enter="reload" @clear="reload" />
      </div>

      <el-table :data="list" v-loading="loading" size="small" row-key="id">
        <el-table-column label="级别" width="80" align="center">
          <template #default="s">
            <el-tag size="small" :type="s.row.level === 'error' ? 'danger' : 'warning'" effect="plain">
              {{ s.row.level === 'error' ? '错误' : '警告' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="check" label="检查项" width="120" />
        <el-table-column prop="ref" label="位置" min-width="180" show-overflow-tooltip />
        <el-table-column prop="message" label="问题" min-width="340" show-overflow-tooltip />
        <template #empty>
          <el-empty :description="error || '该项目没有问题项'" />
        </template>
      </el-table>

      <!-- lint 是计算型报告，服务端算完在这里切片渲染；
           此前 243 条 issue 一次性塞进 DOM，归档库大项目会直接卡死 -->
      <div class="pager">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize"
                       :total="total" :page-sizes="PAGER_SIZES" :layout="PAGER_LAYOUT"
                       :disabled="loading" background />
      </div>
    </template>
  </el-card>
</template>

<script setup>
import { computed, onActivated, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { PAGER_LAYOUT, PAGER_SIZES, usePaged } from '../composables/usePaged'
import { usePersistentState } from '../composables/usePersistentState'

const projects = ref([])
const pid = usePersistentState('pid', null)
const report = ref({})
const running = ref(false)
const severity = usePersistentState('severity', '')
const kw = usePersistentState('kw', '')

const {
  list, total, page, pageSize, loading, error, reset,
} = usePaged(
  p => api.get(`/active/projects/${pid.value}/lint`, {
    params: { ...p, severity: severity.value || undefined, keyword: kw.value || undefined },
  })
)
const totalAll = computed(() => (report.value.counts?.error ?? 0) + (report.value.counts?.warn ?? 0))

// 切严重度/关键词时：不只刷新表格（reset 走 usePaged），还要重算 report 横幅与计数，
// 否则顶部「通过/错误 N/警告 M」会停在上次全量检查的状态，与过滤后的表格不一致，误判门禁结论
function reload() { if (pid.value) fetchReport(false) }

// resetPage：点「执行全量检查」回到第 1 页；切走再切回来要留在原页码
async function fetchReport(resetPage = true) {
  running.value = true
  try {
    if (resetPage) page.value = 1
    // 一次请求同时拿到 summary / counts 和当前页数据
    report.value = (await api.get(`/active/projects/${pid.value}/lint`, {
      params: {
        page: page.value,
        page_size: pageSize.value,
        severity: severity.value || undefined,
        keyword: kw.value || undefined,
      },
    })).data
    list.value = report.value.list ?? []
    total.value = report.value.total ?? 0
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || '检查失败')
    report.value = {}
  } finally { running.value = false }
}
function run() { if (pid.value) fetchReport(true) }

onMounted(async () => {
  try {
    const { data } = await api.get('/active/projects', { params: { page: 1, page_size: 100 } })
    projects.value = data.list ?? []
    // pid / severity / kw 都是持久化的：切回本菜单自动补回检查报告，
    // 否则状态恢复了但 report 为空，结果区整块不渲染
    if (pid.value) await fetchReport(false)
  } catch { /* 401 由拦截器处理 */ }
})
// keep-alive：从别的菜单切回时重新拉项目列表与检查报告（保持原页码/筛选）
onActivated(async () => {
  try {
    const { data } = await api.get('/active/projects', { params: { page: 1, page_size: 100 } })
    projects.value = data.list ?? []
    if (pid.value) await fetchReport(false)
  } catch { /* 401 由拦截器处理 */ }
})
</script>
