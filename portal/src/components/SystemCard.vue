<template>
  <div class="system-card" :style="{ '--accent': config.color }">
    <div class="card-header">
      <div class="card-icon">
        <el-icon :size="28">
          <component :is="config.icon" />
        </el-icon>
      </div>
      <div class="card-title-area">
        <div class="card-title-row">
          <h3 class="card-title">{{ config.name }}</h3>
          <el-button
            class="favorite-btn"
            :type="isFavorited ? 'warning' : 'default'"
            :icon="isFavorited ? StarFilled : Star"
            circle
            size="small"
            @click.stop="handleToggleFavorite"
          />
        </div>
        <StatusBadge v-if="config.healthCheckUrl" :status="status || 'unknown'" :latency="latency" />
      </div>
    </div>

    <p class="card-desc">{{ config.description }}</p>

    <div v-if="config.techStack" class="card-tech">
      <el-tag size="small" type="info" effect="plain">{{ config.techStack }}</el-tag>
    </div>

    <div class="card-actions">
      <el-button
        v-if="config.url"
        type="primary"
        size="small"
        @click="openUrl(config.url!)"
      >
        <el-icon><Position /></el-icon>
        访问
      </el-button>

      <el-button
        v-if="config.downloadPath"
        type="success"
        size="small"
        @click="download"
      >
        <el-icon><Download /></el-icon>
        下载
      </el-button>

      <el-dropdown v-if="config.docs && config.docs.length > 0" trigger="click">
        <el-button size="small">
          <el-icon><Document /></el-icon>
          文档
          <el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="doc in config.docs"
              :key="doc.url"
              @click="openUrl(doc.url)"
            >
              {{ doc.label }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Download, Document, ArrowDown, Star, StarFilled } from '@element-plus/icons-vue'
import type { SystemConfig, SystemStatus } from '@/config/systems'
import StatusBadge from './StatusBadge.vue'
import { useFavoritesStore } from '@/stores/favorites'

const props = defineProps<{
  config: SystemConfig
  status?: SystemStatus
  latency?: number
  isFavorite?: boolean
}>()

const favoritesStore = useFavoritesStore()

const isFavorited = computed(() => props.isFavorite || favoritesStore.isFavorite(props.config.id))

function handleToggleFavorite() {
  favoritesStore.toggleFavorite(props.config.id)
}

function openUrl(url: string) {
  window.open(url, '_blank', 'noopener')
}

function download() {
  if (props.config.downloadPath) {
    window.open(props.config.downloadPath, '_blank')
  }
}
</script>

<style scoped lang="scss">
.system-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #ebeef5;
  border-top: 3px solid var(--accent);
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;
  gap: 12px;

  &:hover {
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
    transform: translateY(-2px);
  }
}

.card-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.card-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.card-title-area {
  flex: 1;
  min-width: 0;
}

.card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.favorite-btn {
  flex-shrink: 0;
}

.card-desc {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  min-height: 42px;
}

.card-tech {
  :deep(.el-tag) {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.card-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: auto;
}
</style>
