<template>
  <div class="dashboard">
    <div class="overview-bar">
      <div class="overview-stats">
        <div class="stat-item">
          <span class="stat-value">{{ totalSystems }}</span>
          <span class="stat-label">总系统数</span>
        </div>
        <div class="stat-item online">
          <span class="stat-value">{{ onlineCount }}</span>
          <span class="stat-label">在线</span>
        </div>
        <div class="stat-item offline">
          <span class="stat-value">{{ offlineCount }}</span>
          <span class="stat-label">离线</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ unknownCount }}</span>
          <span class="stat-label">未检测</span>
        </div>
      </div>
      <el-button type="primary" :loading="checking" @click="runHealthCheck">
        <el-icon><Refresh /></el-icon>
        {{ checking ? '检测中...' : '刷新状态' }}
      </el-button>
    </div>

    <div id="favorites" class="section-wrapper">
      <div class="section-title" @click="favoritesStore.toggleCollapse('favorites')">
        <el-icon class="section-icon"><StarFilled /></el-icon>
        <span>我的收藏</span>
        <span class="section-count">({{ favoriteSystems.length }})</span>
        <el-icon class="collapse-icon" :class="{ collapsed: favoritesStore.isCollapsed('favorites') }">
          <ArrowDown />
        </el-icon>
      </div>
      <div v-show="!favoritesStore.isCollapsed('favorites')">
        <div v-if="favoriteSystems.length > 0" class="cards-grid">
          <SystemCard
            v-for="sys in favoriteSystems"
            :key="sys.id"
            :config="sys"
            :status="healthMap.get(sys.id)?.status || 'unknown'"
            :latency="healthMap.get(sys.id)?.latency"
            :is-favorite="true"
          />
        </div>
        <el-empty v-else description="暂无收藏，点击卡片上的星标收藏常用系统" :image-size="80" />
      </div>
    </div>

    <div v-for="cat in categories" :key="cat" class="section-wrapper" :id="`category-${cat}`">
      <div class="section-title" @click="favoritesStore.toggleCollapse(cat)">
        <el-icon class="section-icon">
          <component :is="categoryIcons[cat]" />
        </el-icon>
        <span>{{ categoryLabels[cat] }}</span>
        <span class="section-count">({{ getSystemsByCategory(cat).length }})</span>
        <el-icon class="collapse-icon" :class="{ collapsed: favoritesStore.isCollapsed(cat) }">
          <ArrowDown />
        </el-icon>
      </div>
      <div v-show="!favoritesStore.isCollapsed(cat)">
        <div v-if="getSystemsByCategory(cat).length > 0" class="cards-grid">
          <SystemCard
            v-for="sys in getSystemsByCategory(cat)"
            :key="sys.id"
            :config="sys"
            :status="healthMap.get(sys.id)?.status || 'unknown'"
            :latency="healthMap.get(sys.id)?.latency"
          />
        </div>
        <el-empty v-else description="暂无系统" :image-size="80" />
      </div>
    </div>

    <el-empty v-if="filteredSystems.length === 0" description="没有找到匹配的系统" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Refresh, StarFilled, ArrowDown } from '@element-plus/icons-vue'
import { categoryLabels, categoryIcons, type SystemCategory, type SystemConfig, type SystemStatus } from '@/config/systems'
import { checkHealth, type HealthResult } from '@/api/health'
import SystemCard from '@/components/SystemCard.vue'
import { useFavoritesStore } from '@/stores/favorites'
import { useSystemStore } from '@/stores/system'
import { ElMessage } from 'element-plus'

const favoritesStore = useFavoritesStore()
const systemStore = useSystemStore()
const categories: SystemCategory[] = ['web', 'infra', 'tool', 'doc']
const healthMap = ref<Map<string, HealthResult>>(new Map())
const checking = ref(false)
const searchKeyword = ref('')

const systems = computed(() => systemStore.systems)

const filteredSystems = computed(() => {
  if (!searchKeyword.value.trim()) return systems.value
  const keyword = searchKeyword.value.toLowerCase()
  return systems.value.filter(
    s =>
      s.name.toLowerCase().includes(keyword) ||
      s.description.toLowerCase().includes(keyword)
  )
})

const favoriteSystems = computed(() => {
  return filteredSystems.value.filter(s => favoritesStore.isFavorite(s.id))
})

const totalSystems = computed(() => filteredSystems.value.length)
const onlineCount = computed(() => countByStatus('online'))
const offlineCount = computed(() => countByStatus('offline'))
const unknownCount = computed(() => filteredSystems.value.length - onlineCount.value - offlineCount.value)

function countByStatus(status: SystemStatus): number {
  let count = 0
  for (const sys of filteredSystems.value) {
    if (sys.healthCheckUrl) {
      if (healthMap.value.get(sys.id)?.status === status) count++
    }
  }
  return count
}

function getSystemsByCategory(cat: SystemCategory): SystemConfig[] {
  return filteredSystems.value.filter(
    s => s.category === cat && !favoritesStore.isFavorite(s.id)
  )
}

async function fetchSystems() {
  try {
    await systemStore.fetchSystems()
  } catch (e: any) {
    ElMessage.error(e.message || '获取系统列表失败')
  }
}

async function runHealthCheck() {
  checking.value = true
  try {
    const result = await checkHealth(filteredSystems.value)
    healthMap.value = result
  } finally {
    checking.value = false
  }
}

function handleSearch(e: Event) {
  const event = e as CustomEvent<{ keyword: string }>
  searchKeyword.value = event.detail.keyword
}

function handleCategoryClick(e: Event) {
  const event = e as CustomEvent<{ category: SystemCategory }>
  const cat = event.detail.category
  if (favoritesStore.isCollapsed(cat)) {
    favoritesStore.toggleCollapse(cat)
  }
  setTimeout(() => {
    const el = document.getElementById(`category-${cat}`)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }, 50)
}

onMounted(async () => {
  await fetchSystems()
  runHealthCheck()
  document.addEventListener('portal-search', handleSearch as EventListener)
  document.addEventListener('portal-category-click', handleCategoryClick as EventListener)
})

onUnmounted(() => {
  document.removeEventListener('portal-search', handleSearch as EventListener)
  document.removeEventListener('portal-category-click', handleCategoryClick as EventListener)
})
</script>

<style scoped lang="scss">
.overview-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  margin-bottom: 24px;
}

.overview-stats {
  display: flex;
  gap: 32px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;

  .stat-value {
    font-size: 28px;
    font-weight: 700;
    color: #303133;
  }

  .stat-label {
    font-size: 12px;
    color: #909399;
  }

  &.online .stat-value {
    color: #67c23a;
  }

  &.offline .stat-value {
    color: #f56c6c;
  }
}

.section-wrapper {
  margin-bottom: 8px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 24px 0 16px;
  cursor: pointer;
  user-select: none;

  .section-icon {
    font-size: 22px;
    color: #667eea;
  }

  .section-count {
    font-size: 14px;
    font-weight: 400;
    color: #909399;
  }

  .collapse-icon {
    margin-left: auto;
    font-size: 16px;
    color: #909399;
    transition: transform 0.3s ease;

    &.collapsed {
      transform: rotate(-90deg);
    }
  }

  &:hover {
    .collapse-icon {
      color: #667eea;
    }
  }
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

@media (max-width: 768px) {
  .overview-bar {
    flex-direction: column;
    gap: 16px;
    padding: 16px;
  }

  .overview-stats {
    gap: 20px;
  }

  .stat-item .stat-value {
    font-size: 22px;
  }

  .cards-grid {
    grid-template-columns: 1fr;
  }
}
</style>
