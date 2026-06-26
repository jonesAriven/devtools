<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Trash2, Cloud, List } from 'lucide-vue-next'
import { tagApi } from '@/api/knowledge'
import type { Tag } from '@/types/api'

const loading = ref(false)
const tags = ref<Tag[]>([])
const view = ref<'cloud' | 'list'>('cloud')

const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ name: '', color: '#409eff' })

const presetColors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#d4a574', '#9b59b6', '#1abc9c']

const maxCount = computed(() => Math.max(1, ...tags.value.map((t) => t.count || 0)))

function tagFontSize(tag: Tag): number {
  const c = tag.count || 0
  if (c === 0) return 14
  const ratio = c / maxCount.value
  return Math.round(14 + ratio * 16)
}

async function loadTags() {
  loading.value = true
  try {
    tags.value = (await tagApi.list()) as Tag[]
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.name = ''
  form.color = '#409eff'
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入标签名称')
    return
  }
  submitting.value = true
  try {
    await tagApi.create({ name: form.name.trim(), color: form.color })
    ElMessage.success('创建成功')
    dialogVisible.value = false
    await loadTags()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(tag: Tag) {
  await ElMessageBox.confirm(`确认删除标签「${tag.name}」？`, '删除确认', { type: 'warning' })
  await tagApi.delete(tag.id)
  ElMessage.success('删除成功')
  await loadTags()
}

onMounted(loadTags)
</script>

<template>
  <div class="tag-manage" v-loading="loading">
    <div class="header">
      <h2 class="title">标签管理</h2>
      <div class="header-actions">
        <el-radio-group v-model="view" size="small">
          <el-radio-button value="cloud"><Cloud :size="14" /> 标签云</el-radio-button>
          <el-radio-button value="list"><List :size="14" /> 列表</el-radio-button>
        </el-radio-group>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建标签</el-button>
      </div>
    </div>

    <!-- 标签云视图 -->
    <div v-if="view === 'cloud'" class="tag-cloud">
      <span
        v-for="tag in tags"
        :key="tag.id"
        class="cloud-tag"
        :style="{ color: tag.color, fontSize: tagFontSize(tag) + 'px', borderColor: tag.color }"
      >
        {{ tag.name }}
        <span class="cloud-count">{{ tag.count || 0 }}</span>
        <el-icon class="cloud-del" @click="handleDelete(tag)"><Trash2 :size="12" /></el-icon>
      </span>
      <el-empty v-if="tags.length === 0" description="暂无标签，点击右上角创建" />
    </div>

    <!-- 列表视图 -->
    <el-table v-else :data="tags" stripe>
      <el-table-column label="标签" min-width="160">
        <template #default="{ row }">
          <el-tag :color="row.color" effect="dark">{{ row.name }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="颜色" width="120">
        <template #default="{ row }">
          <div class="color-cell">
            <span class="color-block" :style="{ background: row.color }"></span>
            {{ row.color }}
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="count" label="关联资源数" width="120" align="center">
        <template #default="{ row }">{{ row.count || 0 }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center">
        <template #default="{ row }">
          <el-button type="danger" :icon="Trash2" size="small" @click="handleDelete(row)" />
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="新建标签" width="420px">
      <el-form :model="form" label-width="72px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="请输入标签名称" maxlength="20" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-color-picker v-model="form.color" />
          <div class="preset-colors">
            <span
              v-for="c in presetColors"
              :key="c"
              class="preset-color"
              :style="{ background: c }"
              @click="form.color = c"
            ></span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.tag-manage {
  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
  }
  .title {
    font-size: 22px;
    font-weight: 600;
    color: #2c3e50;
    margin: 0;
  }
  .header-actions {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

.tag-cloud {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 32px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 20px;
  min-height: 280px;
}

.cloud-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 500;
  cursor: default;
  border-bottom: 2px solid transparent;
  transition: opacity 0.2s;

  &:hover {
    opacity: 0.85;
    .cloud-del {
      display: inline-flex;
    }
  }
}

.cloud-count {
  font-size: 12px;
  color: #95a5a6;
  background: #f5f3f0;
  border-radius: 10px;
  padding: 0 6px;
}

.cloud-del {
  display: none;
  cursor: pointer;
  color: #f56c6c;
  margin-left: 2px;
}

.color-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-block {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  border: 1px solid #ebeef5;
}

.preset-colors {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.preset-color {
  width: 22px;
  height: 22px;
  border-radius: 4px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: border-color 0.2s;

  &:hover {
    border-color: #2c3e50;
  }
}
</style>
