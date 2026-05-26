<template>
  <div>
    <div style="margin-bottom: 16px">
      <el-button @click="$router.back()">← 返回</el-button>
      <h2 style="display: inline-block; margin-left: 12px">配置预览</h2>
    </div>

    <el-card v-loading="loading">
      <div style="margin-bottom: 16px">
        <el-tag>{{ type === 'server' ? '服务端' : '客户端' }}</el-tag>
        <span style="margin-left: 8px">{{ name }}</span>
        <el-tag type="info" style="margin-left: 8px">{{ format }}</el-tag>
      </div>

      <el-alert
        title="该配置为预览模式，实际部署需确认连接信息后通过部署功能下发"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <el-input
        type="textarea"
        :rows="20"
        :model-value="config"
        readonly
        style="font-family: 'Courier New', monospace; font-size: 13px"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { deployApi } from '../utils/api'

const route = useRoute()
const type = ref(route.params.type)
const id = ref(route.params.id)
const config = ref('')
const name = ref('')
const format = ref('')
const loading = ref(true)

onMounted(async () => {
  try {
    if (type.value === 'server') {
      const res = await deployApi.previewFrps(id.value)
      config.value = res.data.config
      format.value = res.data.format || 'ini'
      name.value = `服务端 #${id.value}`
    } else {
      const res = await deployApi.previewFrpc(id.value)
      config.value = res.data.config
      format.value = res.data.format || 'toml'
      name.value = `客户端 #${id.value}`
    }
  } catch (e) {
    config.value = '获取配置失败'
  } finally {
    loading.value = false
  }
})
</script>
