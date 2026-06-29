<template>
  <div class="tag-input">
    <div class="tag-list">
      <el-tag
        v-for="tag in selectedTags"
        :key="tag.id"
        closable
        :color="tag.color"
        size="small"
        @close="handleRemoveTag(tag)"
      >
        {{ tag.name }}
      </el-tag>
    </div>
    <el-autocomplete
      v-model="inputValue"
      :fetch-suggestions="querySearch"
      placeholder="输入标签"
      size="small"
      clearable
      @select="handleSelect"
      @keyup.enter="handleAddCustomTag"
    >
      <template #default="{ item }">
        <span>{{ item.name }}</span>
      </template>
    </el-autocomplete>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getTagList, createTag, addResourceTag, removeResourceTag, getResourceTags } from '@/api/tag'
import type { Tag } from '@/types'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  resourceId: number
  resourceType: string
}>()

const selectedTags = ref<Tag[]>([])
const allTags = ref<Tag[]>([])
const inputValue = ref('')

onMounted(() => {
  loadTags()
})

async function loadTags() {
  const [allRes, resRes] = await Promise.all([
    getTagList(),
    getResourceTags(props.resourceId, props.resourceType),
  ])
  allTags.value = allRes.data.data
  selectedTags.value = resRes.data.data
}

function querySearch(queryString: string, cb: (results: any[]) => void) {
  const results = allTags.value
    .filter((tag) => !selectedTags.value.some((st) => st.id === tag.id))
    .filter((tag) => tag.name.toLowerCase().includes(queryString.toLowerCase()))
    .map((tag) => ({ ...tag, value: tag.name }))
  cb(results)
}

async function handleSelect(item: any) {
  const tag = item as Tag
  await addResourceTag({
    tagId: tag.id,
    resourceId: props.resourceId,
    resourceType: props.resourceType,
  })
  selectedTags.value.push(tag)
  inputValue.value = ''
}

async function handleAddCustomTag() {
  const name = inputValue.value.trim()
  if (!name) return

  const existing = allTags.value.find((t) => t.name === name)
  if (existing) {
    await handleSelect(existing)
    return
  }

  const res = await createTag({ name })
  const newTag = res.data.data
  allTags.value.push(newTag)
  await addResourceTag({
    tagId: newTag.id,
    resourceId: props.resourceId,
    resourceType: props.resourceType,
  })
  selectedTags.value.push(newTag)
  inputValue.value = ''
}

async function handleRemoveTag(tag: Tag) {
  await removeResourceTag({
    tagId: tag.id,
    resourceId: props.resourceId,
    resourceType: props.resourceType,
  })
  selectedTags.value = selectedTags.value.filter((t) => t.id !== tag.id)
}
</script>

<style scoped lang="scss">
.tag-input {
  .tag-list {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: 8px;
  }
}
</style>
