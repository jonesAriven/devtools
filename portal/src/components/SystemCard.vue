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
      <!-- 三入口下拉：至少有一个入口时显示 -->
      <el-dropdown v-if="hasAnyEntry" trigger="click" @command="openUrl">
        <el-button type="primary" size="small">
          <el-icon><Position /></el-icon>
          访问
          <el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-if="config.urlPublic" :command="config.urlPublic">
              🌐 公网入口
            </el-dropdown-item>
            <el-dropdown-item v-if="config.urlLan" :command="config.urlLan">
              🏠 家庭局域网
            </el-dropdown-item>
            <el-dropdown-item v-if="config.urlTailscale" :command="config.urlTailscale">
              🔒 Tailscale
            </el-dropdown-item>
            <el-dropdown-item
              v-if="config.url && !config.urlPublic && !config.urlLan && !config.urlTailscale"
              :command="config.url"
            >
              🔗 主入口
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

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

      <el-dropdown
        v-if="showCredentialsBtn"
        trigger="click"
        @command="handleCredentialsCommand"
      >
        <el-button size="small" type="warning">
          <el-icon><Key /></el-icon>
          账密
          <el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="copy-username">
              <el-icon><User /></el-icon>
              复制账号
            </el-dropdown-item>
            <el-dropdown-item command="copy-password">
              <el-icon><Lock /></el-icon>
              复制密码
            </el-dropdown-item>
            <el-dropdown-item command="quick-login" divided>
              <el-icon><Lightning /></el-icon>
              快速登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  Position,
  Download,
  Document,
  ArrowDown,
  Star,
  StarFilled,
  Key,
  User,
  Lock,
  Lightning
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { SystemConfig, SystemStatus } from '@/config/systems'
import StatusBadge from './StatusBadge.vue'
import { useFavoritesStore } from '@/stores/favorites'
import { getSystemCredentials } from '@/api/system'

const props = defineProps<{
  config: SystemConfig
  status?: SystemStatus
  latency?: number
  isFavorite?: boolean
}>()

const favoritesStore = useFavoritesStore()
const loadingCredentials = ref(false)

const isFavorited = computed(() => props.isFavorite || favoritesStore.isFavorite(props.config.id))
const showCredentialsBtn = computed(() => !!primaryUrl.value && props.config.loginUsername)

// 优先级：公网 > Tailscale > 家庭 > 兼容旧 url
const primaryUrl = computed(() =>
  props.config.urlPublic
  || props.config.urlTailscale
  || props.config.urlLan
  || props.config.url
)

const hasAnyEntry = computed(() =>
  !!(props.config.urlPublic || props.config.urlLan || props.config.urlTailscale || props.config.url)
)

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

async function fetchCredentials() {
  loadingCredentials.value = true
  try {
    return await getSystemCredentials(props.config.id)
  } catch (e: any) {
    ElMessage.error(e.message || '获取账密失败')
    return null
  } finally {
    loadingCredentials.value = false
  }
}

function copyToClipboard(text: string): boolean {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  try {
    document.execCommand('copy')
    document.body.removeChild(textarea)
    return true
  } catch (e) {
    document.body.removeChild(textarea)
    return false
  }
}

async function handleCredentialsCommand(command: string) {
  const creds = await fetchCredentials()
  if (!creds) return

  switch (command) {
    case 'copy-username':
      if (!creds.username) {
        ElMessage.warning('该系统未配置登录账号')
        break
      }
      if (copyToClipboard(creds.username)) {
        ElMessage.success('账号已复制到剪贴板')
      } else {
        ElMessage.error('复制失败，请手动复制')
      }
      break
    case 'copy-password':
      if (!creds.password) {
        ElMessage.warning('该系统未配置登录密码')
        break
      }
      if (copyToClipboard(creds.password)) {
        ElMessage.success('密码已复制到剪贴板')
      } else {
        ElMessage.error('复制失败，请手动复制')
      }
      break
    case 'quick-login':
      if (creds.username && creds.password) {
        const text = `${creds.username}\t${creds.password}`
        copyToClipboard(text)
        if (primaryUrl.value) {
          openUrl(primaryUrl.value)
        }
        ElMessage.success('账号密码已复制，登录页按 Ctrl+V 粘贴')
      } else if (creds.username) {
        copyToClipboard(creds.username)
        if (primaryUrl.value) {
          openUrl(primaryUrl.value)
        }
        ElMessage.success('账号已复制，登录页按 Ctrl+V 粘贴')
      }
      break
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
