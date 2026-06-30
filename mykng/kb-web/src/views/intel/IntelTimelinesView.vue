<template>
  <div class="intel-timelines">
    <el-alert
      type="info"
      :closable="false"
      title="事件来源于知识引擎解析的文档"
      show-icon
      class="top-alert"
    />
    <el-card shadow="never">
      <template #header>
        <div class="header-bar">
          <span class="card-title">时间线</span>
          <div class="header-actions">
            <el-select
              v-model="eventTypeFilter"
              placeholder="事件类型"
              clearable
              style="width: 150px"
            >
              <el-option label="DEPLOY" value="DEPLOY" />
              <el-option label="INCIDENT" value="INCIDENT" />
              <el-option label="CHANGE" value="CHANGE" />
              <el-option label="MAINTENANCE" value="MAINTENANCE" />
              <el-option label="OTHER" value="OTHER" />
            </el-select>
            <el-select
              v-model="severityFilter"
              placeholder="严重程度"
              clearable
              style="width: 140px"
            >
              <el-option label="INFO" value="INFO" />
              <el-option label="WARN" value="WARN" />
              <el-option label="ERROR" value="ERROR" />
              <el-option label="CRITICAL" value="CRITICAL" />
            </el-select>
            <el-button :icon="Refresh" @click="loadData">刷新</el-button>
          </div>
        </div>
      </template>
      <div v-loading="loading">
        <el-timeline v-if="filteredList.length">
          <el-timeline-item
            v-for="item in filteredList"
            :key="item.id"
            :timestamp="item.eventTime"
            :type="timelineType(item.severity)"
            placement="top"
          >
            <el-card shadow="never" class="event-card">
              <div class="event-title">
                <span class="title-text">{{ item.title }}</span>
                <el-tag size="small" class="event-type-tag">{{ item.eventType }}</el-tag>
              </div>
              <p class="event-desc">{{ item.description }}</p>
              <el-alert
                v-if="item.solution"
                type="success"
                :closable="false"
                show-icon
                class="solution-alert"
              >
                <template #title>解决方案</template>
                <div>{{ item.solution }}</div>
              </el-alert>
            </el-card>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无事件" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getTimelineList } from '@/api/intelligence'
import type { IntelTimeline } from '@/types'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

const loading = ref(false)
const list = ref<IntelTimeline[]>([])
const eventTypeFilter = ref('')
const severityFilter = ref('')

const filteredList = computed(() => {
  return list.value.filter(t => {
    if (eventTypeFilter.value && t.eventType !== eventTypeFilter.value) return false
    if (severityFilter.value && t.severity !== severityFilter.value) return false
    return true
  })
})

function timelineType(severity: string) {
  const s = (severity || '').toUpperCase()
  if (s === 'INFO') return 'primary'
  if (s === 'WARN') return 'warning'
  if (s === 'ERROR') return 'danger'
  if (s === 'CRITICAL') return 'danger'
  return 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getTimelineList()
    list.value = res.data.data
  } catch {
    ElMessage.error('加载时间线失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.intel-timelines .top-alert {
  margin-bottom: 12px;
}
.intel-timelines .header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.intel-timelines .card-title {
  font-weight: 600;
  color: #303133;
}
.intel-timelines .header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.intel-timelines .event-card {
  margin-bottom: 4px;
}
.intel-timelines .event-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.intel-timelines .title-text {
  font-weight: 700;
  font-size: 15px;
  color: #303133;
}
.intel-timelines .event-type-tag {
  margin-left: 4px;
}
.intel-timelines .event-desc {
  margin: 0 0 8px 0;
  color: #606266;
  line-height: 1.6;
}
.intel-timelines .solution-alert {
  margin-top: 8px;
}

@media (max-width: 768px) {
  .intel-timelines .header-bar {
    flex-wrap: wrap;
    gap: 8px;
  }
  .intel-timelines .header-actions {
    flex-wrap: wrap;
  }
  .intel-timelines .event-title {
    flex-wrap: wrap;
  }
}
</style>
