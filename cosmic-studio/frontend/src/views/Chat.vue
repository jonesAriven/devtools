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
import { nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useBreakpoint } from '../composables/useBreakpoint'
import { usePersistentState } from '../composables/usePersistentState'

// 对话历史持久化：切去别的菜单再回来，聊天记录不该清空
const MAX_MSGS = 60 // 只保留最近若干条，避免撑爆 localStorage 配额
const msgs = usePersistentState('msgs', [{ role: 'assistant', content: '我是 cosmic-studio 助手，可以查项目、跑门禁、导出交付件、查词库、改规范。直接说需求即可。' }])
watch(msgs, v => { if (v.length > MAX_MSGS) msgs.value = v.slice(-MAX_MSGS) })
const input = ref('')
const loading = ref(false)
const msgsEl = ref()
// 断点收口到 useBreakpoint（原实现在 onMounted 里另挂了一个监听且从不清理）
const { isMobile } = useBreakpoint()

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
</script>

<style scoped>
.chat-page { display: flex; flex-direction: column; height: calc(100vh - 100px); }
.msgs { flex: 1; overflow-y: auto; padding: var(--sp-2) var(--sp-1); }
.msg { display: flex; margin: var(--sp-2) 0; }
.msg.user { justify-content: flex-end; }
.bubble { max-width: 78%; padding: var(--sp-3) var(--sp-4); border-radius: var(--r-lg);
  background: var(--c-surface); box-shadow: var(--sh-1);
  white-space: pre-wrap; word-break: break-word; }
.msg.user .bubble { background: var(--c-primary); color: var(--c-text-inverse); }
.msg.assistant .bubble { background: var(--c-surface); color: var(--c-text); }
.tools { margin-bottom: var(--sp-2); }
.input-bar { display: flex; gap: var(--sp-2); align-items: flex-end; }
@media (max-width: 767px) { .chat-page { height: calc(100vh - 92px); } }
</style>
