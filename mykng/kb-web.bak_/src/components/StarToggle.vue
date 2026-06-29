<template>
  <el-icon
    class="star-toggle"
    :size="size"
    :color="starred ? '#e6a23c' : '#c0c4cc'"
    @click.stop="handleToggle"
  >
    <StarFilled v-if="starred" />
    <Star v-else />
  </el-icon>
</template>

<script setup lang="ts">
const props = withDefaults(defineProps<{
  starred: boolean
  size?: number
}>(), {
  size: 18,
})

const emit = defineEmits<{
  (e: 'toggle'): void
}>()

function handleToggle() {
  emit('toggle')
}
</script>

<style scoped lang="scss">
.star-toggle {
  cursor: pointer;
  transition: color 0.2s;

  &:hover {
    color: #e6a23c;
  }
}
</style>
