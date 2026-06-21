<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<style>
#app {
  width: 100%;
  height: 100%;
}

/* ========== 全局移动端适配 ========== */
@media (max-width: 768px) {
  /* 对话框全宽 */
  .el-dialog {
    width: 92% !important;
    margin: 8vh auto !important;
  }

  /* 分页器简化 */
  .el-pagination {
    white-space: normal;
  }

  .el-pagination .el-pagination__jump,
  .el-pagination .el-pagination__total {
    display: none;
  }

  /* 表格水平滚动 */
  .el-table {
    width: 100% !important;
  }

  /* 消息提示全宽 */
  .el-message {
    min-width: auto !important;
    max-width: 90% !important;
  }

  /* 表单标签上置 */
  .el-form--inline .el-form-item {
    margin-right: 0;
    margin-bottom: 12px;
    width: 100%;
  }
}
</style>
