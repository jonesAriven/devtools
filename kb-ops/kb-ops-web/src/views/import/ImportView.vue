<template>
  <div class="import-view">
    <el-card shadow="never">
      <template #header>
        <span>数据导入</span>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="主机导入" name="host">
          <div class="import-panel">
            <div class="import-desc">
              <p>通过 Excel/CSV 文件批量导入主机数据。</p>
              <el-button type="primary" link @click="downloadTemplate('host')">
                <el-icon><Download /></el-icon>
                下载导入模板
              </el-button>
            </div>
            <el-upload
              class="upload-demo"
              drag
              :auto-upload="false"
              :on-change="handleFileChange"
              :file-list="fileList"
              :limit="1"
              accept=".xlsx,.xls,.csv"
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">
                将文件拖到此处，或<em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                  支持 xlsx、xls、csv 格式文件，单个文件不超过 10MB
                </div>
              </template>
            </el-upload>
            <div class="import-actions">
              <el-button type="primary" :loading="importing" :disabled="!selectedFile" @click="handleImport('host')">
                开始导入
              </el-button>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="服务导入" name="service">
          <div class="import-panel">
            <div class="import-desc">
              <p>通过 Excel/CSV 文件批量导入服务数据。</p>
              <el-button type="primary" link @click="downloadTemplate('service')">
                <el-icon><Download /></el-icon>
                下载导入模板
              </el-button>
            </div>
            <el-upload
              class="upload-demo"
              drag
              :auto-upload="false"
              :on-change="handleFileChange"
              :file-list="fileList"
              :limit="1"
              accept=".xlsx,.xls,.csv"
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">
                将文件拖到此处，或<em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                  支持 xlsx、xls、csv 格式文件，单个文件不超过 10MB
                </div>
              </template>
            </el-upload>
            <div class="import-actions">
              <el-button type="primary" :loading="importing" :disabled="!selectedFile" @click="handleImport('service')">
                开始导入
              </el-button>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="端口导入" name="port">
          <div class="import-panel">
            <div class="import-desc">
              <p>通过 Excel/CSV 文件批量导入端口数据。</p>
              <el-button type="primary" link @click="downloadTemplate('port')">
                <el-icon><Download /></el-icon>
                下载导入模板
              </el-button>
            </div>
            <el-upload
              class="upload-demo"
              drag
              :auto-upload="false"
              :on-change="handleFileChange"
              :file-list="fileList"
              :limit="1"
              accept=".xlsx,.xls,.csv"
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">
                将文件拖到此处，或<em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                  支持 xlsx、xls、csv 格式文件，单个文件不超过 10MB
                </div>
              </template>
            </el-upload>
            <div class="import-actions">
              <el-button type="primary" :loading="importing" :disabled="!selectedFile" @click="handleImport('port')">
                开始导入
              </el-button>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <el-divider />

      <div v-if="importResult" class="import-result">
        <h4>导入结果</h4>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-statistic title="成功" :value="importResult.successCount" value-color="#67c23a" />
          </el-col>
          <el-col :span="8">
            <el-statistic title="失败" :value="importResult.failCount" value-color="#f56c6c" />
          </el-col>
        </el-row>
        <el-table v-if="importResult.details && importResult.details.length > 0" :data="importResult.details" size="small" style="margin-top: 16px">
          <el-table-column prop="row" label="行号" width="80" />
          <el-table-column prop="message" label="错误信息" show-overflow-tooltip />
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { importData, getImportTemplate } from '@/api/import'
import type { ImportResult, UploadUserFile } from 'element-plus'

const activeTab = ref('host')
const fileList = ref<UploadUserFile[]>([])
const selectedFile = ref<File | null>(null)
const importing = ref(false)
const importResult = ref<ImportResult | null>(null)

function handleFileChange(file: UploadUserFile) {
  selectedFile.value = file.raw as File
  fileList.value = [file]
  importResult.value = null
}

async function downloadTemplate(type: string) {
  try {
    const res = await getImportTemplate(type)
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `${type}_template.xlsx`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载模板失败')
  }
}

async function handleImport(type: string) {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  importing.value = true
  try {
    const res = await importData(type, selectedFile.value)
    importResult.value = res.data.data
    ElMessage.success('导入完成')
  } catch {
  } finally {
    importing.value = false
  }
}
</script>

<style scoped lang="scss">
.import-view {
  .import-panel {
    padding: 20px 0;
  }

  .import-desc {
    margin-bottom: 20px;
    color: #606266;

    p {
      margin-bottom: 8px;
    }
  }

  .import-actions {
    margin-top: 20px;
    text-align: center;
  }

  .import-result {
    h4 {
      margin: 0 0 16px 0;
    }
  }
}
</style>
