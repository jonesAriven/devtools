<template>
  <div class="breadcrumb-wrapper" v-if="items.length > 0">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">
        <el-icon class="home-icon"><HomeFilled /></el-icon>
      </el-breadcrumb-item>
      <el-breadcrumb-item
        v-for="item in items"
        :key="item.path"
        :to="item.path !== currentPath ? { path: item.path } : undefined"
      >
        {{ item.title }}
      </el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { HomeFilled } from '@element-plus/icons-vue'

const route = useRoute()

interface BreadcrumbItem {
  title: string
  path: string
}

/** 路由名称 → 中文标题映射 */
const ROUTE_TITLES: Record<string, string> = {
  Dashboard: '工作台',
  Space: '知识空间',
  SpaceManage: '知识空间',
  FileList: '文件管理',
  FileDetail: '文件详情',
  DocCreate: '新建文档',
  DocEdit: '文档编辑',
  WebDetail: '网页收藏',
  Search: '搜索',
  Trash: '回收站',
  TagManage: '标签管理',
  ShareList: '分享中心',
  Settings: '设置',
  OperationLog: '操作日志',
  Graph: '知识图谱',
}

/** 详情页的父级列表页（面包屑中间层） */
const PARENT_BREADCRUMB: Record<string, BreadcrumbItem> = {
  FileDetail: { title: '文件管理', path: '/file' },
  DocEdit: { title: '知识空间', path: '/spaces' },
  WebDetail: { title: '搜索', path: '/search' },
}

const items = computed<BreadcrumbItem[]>(() => {
  const result: BreadcrumbItem[] = []
  const routeName = route.name as string
  if (!routeName) return result

  // 详情页：插入父级列表页
  const parent = PARENT_BREADCRUMB[routeName]
  if (parent) {
    result.push(parent)
  }

  // 当前页标题
  const title = ROUTE_TITLES[routeName]
  if (title) {
    result.push({ title, path: route.path })
  }

  return result
})

const currentPath = computed(() => route.path)
</script>

<style scoped lang="scss">
.breadcrumb-wrapper {
  display: flex;
  align-items: center;
  font-size: 13px;

  :deep(.el-breadcrumb) {
    align-items: center;
  }

  :deep(.el-breadcrumb__item) {
    display: flex;
    align-items: center;
  }

  :deep(.el-breadcrumb__inner) {
    color: #909399;
    font-weight: 400;

    &.is-link:hover {
      color: var(--el-color-primary);
    }
  }

  :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
    color: #303133;
    font-weight: 500;
  }
}

.home-icon {
  font-size: 14px;
  vertical-align: middle;
  margin-right: 2px;
}
</style>
