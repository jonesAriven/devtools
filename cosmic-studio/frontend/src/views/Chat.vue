<template>
  <div class="chat-page">
    <div class="msgs" ref="msgsEl">
      <div v-for="(m, i) in msgs" :key="i" :class="['msg', m.role]">
        <div class="bubble">
          <div v-if="m.tools && m.tools.length" class="tools">
            <el-tag v-for="(t, j) in m.tools" :key="j" size="small" type="warning" effect="plain"
                    style="margin-right:4px">{{ t.tool }}</el-tag>
          </div>
          <div class="text">{{ m.content }}</div>
        </div>
      </div>
      <div v-if="loading" class="msg assistant"><div class="bubble">思考中…</div></div>
    </div>
    <div class="input-bar">
      <el-input v-model="input" type="textarea" :rows="isMobile ? 1 : 2" resize="none"
                placeholder="对话式操作：如「看看编写库有哪些项目」「跑一下项目1的门禁」「查一下'资费'相关的词库术语」"
                @keydown.enter.exact.prevent="send" />
      <el-button type="primary" :loading="loading" @click="send">发送</el-button>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const msgs = ref([{ role: 'assistant', content: '我是 cosmic-studio 助手，可以查项目、跑门禁、导出交付件、查词库、改规范。直接说需求即可。' }])
const input = ref('')
const loading = ref(false)
const msgsEl = ref()
const isMobile = ref(window.innerWidth < 768)

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return
  input.value = ''
  msgs.value.push({ role: 'user', content: text })
  loading.value = true
  await scroll()
  try {
    const { data } = await api.post('/chat', { message: text })
    msgs.value.push({ role: 'assistant', content: data.reply, tools: data.tools_used })
  } catch (e) {
    msgs.value.push({ role: 'assistant', content: e.response?.data?.detail || '请求失败' })
  } finally {
    loading.value = false
    await scroll()
  }
}
async function scroll() {
  await nextTick()
  if (msgsEl.value) msgsEl.value.scrollTop = msgsEl.value.scrollHeight
}
onMounted(() => window.addEventListener('resize', () => { isMobile.value = window.innerWidth < 768 }))
</script>

<style scoped>
.chat-page { display: flex; flex-direction: column; height: calc(100vh - 100px); }
.msgs { flex: 1; overflow-y: auto; padding: 8px 4px; }
.msg { display: flex; margin: 8px 0; }
.msg.user { justify-content: flex-end; }
.bubble { max-width: 78%; padding: 10px 14px; border-radius: 10px; background: #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,.08); white-space: pre-wrap; word-break: break-word; }
.msg.user .bubble { background: #409eff; color: #fff; }
.msg.assistant .bubble { background: #fff; color: #303133; }
.tools { margin-bottom: 6px; }
.input-bar { display: flex; gap: 8px; align-items: flex-end; }
@media (max-width: 767px) { .chat-page { height: calc(100vh - 92px); } }
</style>
