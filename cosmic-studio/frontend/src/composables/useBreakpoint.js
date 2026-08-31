import { onMounted, onUnmounted, ref } from 'vue'

/**
 * 统一响应式断点。
 *
 * 此前移动端断点在 3 处各写一份（Layout.vue 两处 window.innerWidth < 768、
 * Chat.vue 两处），阈值散落且没有清理监听。这里收口成一个，响应式驱动。
 */
const BREAKPOINT = 768

export function useBreakpoint() {
  const isMobile = ref(window.innerWidth < BREAKPOINT)
  const onResize = () => { isMobile.value = window.innerWidth < BREAKPOINT }
  onMounted(() => window.addEventListener('resize', onResize))
  onUnmounted(() => window.removeEventListener('resize', onResize))
  return { isMobile }
}

export { BREAKPOINT }
