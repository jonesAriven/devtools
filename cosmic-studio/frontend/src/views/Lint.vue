<template>
  <el-card>
    <div class="bar">
      <h3>质量门禁</h3>
      <div>
        <el-select v-model="pid" placeholder="选择编写库项目" style="width:320px">
          <el-option v-for="p in projects" :key="p.id" :label="`${p.requirement_id} ${p.requirement_name?.slice(0,24)}`" :value="p.id" />
        </el-select>
        <el-button type="primary" :disabled="!pid" :loading="loading" @click="run">执行全量检查</el-button>
      </div>
    </div>

    <template v-if="report.summary">
      <el-alert :type="report.summary.pass ? 'success' : 'error'" :closable="false"
                :title="report.summary.pass ? '✅ 全部通过' : `❌ 错误 ${report.summary.error} 项 / 警告 ${report.summary.warn} 项`" />
      <el-tabs style="margin-top:10px">
        <el-tab-pane :label="`错误 ${report.errors.length}`">
          <el-table :data="report.errors" size="small" max-height="460">
            <el-table-column prop="check" label="检查项" width="120" />
            <el-table-column prop="ref" label="位置" min-width="180" show-overflow-tooltip />
            <el-table-column prop="message" label="问题" min-width="340" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`警告 ${report.warnings.length}`">
          <el-table :data="report.warnings" size="small" max-height="460">
            <el-table-column prop="check" label="检查项" width="120" />
            <el-table-column prop="ref" label="位置" min-width="180" show-overflow-tooltip />
            <el-table-column prop="message" label="问题" min-width="340" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </template>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const projects = ref([])
const pid = ref(null)
const report = ref({})
const loading = ref(false)

onMounted(async () => { projects.value = (await api.get('/active/projects')).data })
async function run() {
  loading.value = true
  try { report.value = (await api.get(`/active/projects/${pid.value}/lint`)).data }
  catch (e) { ElMessage.error(e.response?.data?.detail || '失败') }
  finally { loading.value = false }
}
</script>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 8px; }
.bar h3 { margin: 0; }
</style>
