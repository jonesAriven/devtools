<template>
  <el-card>
    <div class="bar">
      <h3>业务词库</h3>
      <el-input v-model="q" placeholder="搜索术语" style="width:280px" clearable @keyup.enter="load" />
    </div>
    <el-table :data="list" v-loading="loading" size="small">
      <el-table-column prop="term" label="术语" min-width="220" />
      <el-table-column prop="frequency" label="频次" width="90" sortable />
      <el-table-column prop="source" label="来源" width="100" />
      <el-table-column prop="status" label="状态" width="110">
        <template #default="s">
          <el-tag size="small" :type="s.row.status === 'confirmed' ? 'success' : 'info'">{{ s.row.status }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import api from '../api'

const list = ref([])
const q = ref('')
const loading = ref(false)
async function load() {
  loading.value = true
  try { list.value = (await api.get('/studio/vocab', { params: { q: q.value, limit: 100 } })).data }
  finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.bar h3 { margin: 0; }
</style>
