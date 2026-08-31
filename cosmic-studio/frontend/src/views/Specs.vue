<template>
  <el-card>
    <div class="bar">
      <h3>规范中心（即改即生效，无需重启）</h3>
      <el-radio-group v-model="category" @change="load">
        <el-radio-button value="writing">编写规范</el-radio-button>
        <el-radio-button value="screenshot">截图规范</el-radio-button>
      </el-radio-group>
    </div>
    <el-table :data="pageList" v-loading="loading">
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
          <el-button v-if="s.row._source === 'db' && isAdmin" size="small" type="warning" plain
                     @click="reset(s.row)">还原</el-button>
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
        <el-button v-if="isAdmin" type="primary" @click="save">保存（立即生效）</el-button>
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

const all = ref([])
const category = usePersistentState('category', 'writing')
const loading = ref(false)
const dlg = ref(false)
const current = ref({})
const editText = ref('')

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
function open(row) {
  current.value = row
  editText.value = JSON.stringify(row.value, null, 2)
  dlg.value = true
}
async function save() {
  try {
    const value = JSON.parse(editText.value)
    await api.put(`/studio/specs/${current.value.spec_key}`, { value, category: current.value.category })
    ElMessage.success('已保存并立即生效')
    dlg.value = false
    load()
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || 'JSON 格式错误')
  }
}
async function reset(row) {
  await api.delete(`/studio/specs/${row.spec_key}`)
  ElMessage.success('已还原默认')
  load()
}
onMounted(() => load(false))
// keep-alive：切回时重拉（保留原页码）
onActivated(() => load(false))
</script>

<!-- .bar / .bar h3 已迁入 theme.css -->
