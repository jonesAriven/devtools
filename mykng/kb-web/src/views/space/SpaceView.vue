<template>
  <div class="space-view" v-loading="spaceLoading">
    <div class="space-sidebar">
      <ResourceTree
        ref="resourceTreeRef"
        :space-id="spaceId"
        :space-name="currentSpace?.name"
        :current-folder-id="currentFolderId"
        @select="handleTreeSelect"
        @refresh="handleRefreshTree"
      />
    </div>
    <div class="space-main">
      <ResourceList
        ref="resourceListRef"
        :space-id="spaceId"
        :space-name="currentSpace?.name"
        :folder-id="currentFolderId"
        @refresh-tree="handleRefreshTree"
        @folder-change="handleFolderChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSpaceStore } from '@/stores/space'
import { getSpaceDetail } from '@/api/space'
import { navigateToResource } from '@/utils/format'
import ResourceTree from '@/components/ResourceTree.vue'
import ResourceList from '@/components/ResourceList.vue'
import type { Space } from '@/types'

const route = useRoute()
const router = useRouter()
const spaceStore = useSpaceStore()

const spaceId = computed(() => Number(route.params.spaceId))
const spaceLoading = ref(false)
const currentFolderId = ref<number | null>(null)
const resourceTreeRef = ref()
const resourceListRef = ref()
const currentSpace = ref<Space | null>(null)

onMounted(async () => {
  await loadSpace()
})

async function loadSpace() {
  if (!spaceId.value) return
  spaceLoading.value = true
  try {
    const res = await getSpaceDetail(spaceId.value)
    currentSpace.value = res.data.data
    if (res.data.data) {
      spaceStore.setCurrentSpace(res.data.data)
    }
  } finally {
    spaceLoading.value = false
  }
}

function handleTreeSelect(node: any) {
  if (node.type === 'folder') {
    currentFolderId.value = node.id
  } else {
    navigateToResource(router, node.type, node.id)
  }
}

function handleRefreshTree() {
  resourceTreeRef.value?.loadTree()
}

function handleFolderChange(folderId: number | null) {
  currentFolderId.value = folderId
}
</script>

<style scoped lang="scss">
.space-view {
  display: flex;
  height: 100%;
  background-color: #faf8f5;
}

.space-sidebar {
  width: 260px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #ebeef5;
  padding: 16px 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.space-main {
  flex: 1;
  min-width: 0;
  padding: 16px 20px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

@media (max-width: 768px) {
  .space-view {
    flex-direction: column;
  }

  .space-sidebar {
    width: 100%;
    height: auto;
    max-height: 50%;
    border-right: none;
    border-bottom: 1px solid #ebeef5;
  }
}
</style>
