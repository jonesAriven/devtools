<template>
  <el-card>
    <div class="bar">
      <h3>归档库（只读，人工导入维护）</h3>
      <el-upload v-if="isAdmin" :show-file-list="false" :auto-upload="false" accept=".xlsx"
                 :on-change="onFile" style="display:inline-block">
        <el-button type="primary">导入 xlsx</el-button>
      </el-upload>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="requirement_id" label="需求编号" width="140" />
      <el-table-column prop="requirement_name" label="需求名称" min-width="240" show-overflow-tooltip />
      <el-table-column prop="archived_at" label="归档时间" width="170" />
      <el-table-column label="操作" width="160">
        <template #default="s">
          <el-button size="small" @click="exportJson(s.row.id)">JSON</el-button>
          <el-button size="small" type="success" plain @click="exportXlsx(s.row.id)">xlsx</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dlg" title="导入归档库" width="520px">
      <el-alert type="info" :closable="false" style="margin-bottom:10px">
        <p style="margin:0">第5行起填数据；B/C/D=一二三级模块，E=功能用户，G=功能过程名（动词开头），K=数据属性（、分隔≥3字段）。增量按「模块+FP名」主键 upsert。</p>
        <el-link type="primary" style="margin-top:4px" href="/api/archive/import/template">下载导入模板（含逐列填写说明）</el-link>
      </el-alert>
      <el-radio-group v-model="mode">
        <el-radio value="incremental">增量导入（按业务主键 upsert，需选目标项目）</el-radio>
        <el-radio value="overwrite">全量覆盖导入（清空归档库重灌，admin）</el-radio>
      </el-radio-group>
      <el-select v-if="mode === 'incremental'" v-model="targetPid" placeholder="选择归档项目" style="width:100%; margin-top:10px">
        <el-option v-for="p in list" :key="p.id" :label="`${p.requirement_id} ${p.requirement_name?.slice(0,20)}`" :value="p.id" />
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

const list = ref([])
const loading = ref(false)
const dlg = ref(false)
const mode = ref('incremental')
const targetPid = ref(null)
const file = ref(null)
const uploading = ref(false)

async function load() {
  loading.value = true
  try { list.value = (await api.get('/archive/projects')).data } finally { loading.value = false }
}
function onFile(f) {
  file.value = f.raw
  mode.value = 'incremental'
  dlg.value = true
}
async function doImport() {
  if (!file.value) return
  if (mode.value === 'incremental' && !targetPid.value) { ElMessage.warning('请选择目标项目'); return }
  const fd = new FormData()
  fd.append('file', file.value)
  const q = new URLSearchParams({ mode: mode.value })
  if (mode.value === 'incremental') q.set('project_id', targetPid.value)
  if (mode.value === 'overwrite') { if (!confirm(`确认全量覆盖归档库？现有 ${list.value.length} 个项目将被清空重灌`)) return; q.set('confirm', 'archive') }
  uploading.value = true
  try {
    const { data } = await api.post(`/archive/import/xlsx?${q}`, fd)
    ElMessage.success(`导入完成：模块${data.modules} FP${data.fps} 子过程${data.subs}`)
    dlg.value = false
    load()
  } catch (e) { ElMessage.error(e.response?.data?.detail || '导入失败') } finally { uploading.value = false }
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
onMounted(load)
</script>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.bar h3 { margin: 0; }
</style>
