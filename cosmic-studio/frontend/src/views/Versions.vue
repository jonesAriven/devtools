<template>
  <el-card>
    <div class="bar">
      <h3>版本管理</h3>
      <div>
        <el-select v-model="pid" placeholder="选择编写库项目" style="width:320px">
          <el-option v-for="p in projects" :key="p.id" :label="`${p.requirement_id} ${p.requirement_name?.slice(0,24)}`" :value="p.id" />
        </el-select>
        <el-button :disabled="!pid" @click="load">刷新</el-button>
        <el-button v-if="['admin','editor'].includes(roleName)" type="primary" :disabled="!pid"
                   :loading="snapping" @click="snapDlg = true">创建版本快照</el-button>
      </div>
    </div>
    <el-table :data="list" v-loading="loading">
      <el-table-column prop="seq" label="版本" width="80">
        <template #default="s">v{{ s.row.seq }}</template>
      </el-table-column>
      <el-table-column prop="label" label="标签" width="110" />
      <el-table-column prop="sha256" label="SHA256" min-width="200" show-overflow-tooltip />
      <el-table-column prop="file_size" label="大小" width="90">
        <template #default="s">{{ (s.row.file_size / 1024).toFixed(1) }} KB</template>
      </el-table-column>
      <el-table-column prop="changelog" label="变更说明" min-width="180" show-overflow-tooltip />
      <el-table-column prop="created_at" label="时间" width="170" />
      <el-table-column label="操作" width="100">
        <template #default="s">
          <el-button size="small" @click="download(s.row.id)">下载</el-button>
        </template>
      </el-table-column>
    </el-table>

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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const projects = ref([])
const pid = ref(null)
const list = ref([])
const loading = ref(false)
const snapDlg = ref(false)
const snap = reactive({ label: '', changelog: '' })
const snapping = ref(false)
const roleName = ref(JSON.parse(localStorage.getItem('user') || '{}').role)

onMounted(async () => { projects.value = (await api.get('/active/projects')).data })
async function load() {
  if (!pid.value) return
  loading.value = true
  try { list.value = (await api.get(`/active/projects/${pid.value}/versions`)).data } finally { loading.value = false }
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
function download(id) { window.open(`/api/active/versions/${id}/download`, '_blank') }
</script>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 8px; }
.bar h3 { margin: 0; }
</style>
