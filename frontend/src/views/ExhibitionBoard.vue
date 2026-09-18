<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElCard, ElTable, ElTableColumn, ElTag, ElButton, ElAlert } from 'element-plus'
import { Picture, Refresh } from '@element-plus/icons-vue'
import { exhibitionApi } from '@/api'
import type { ExhibitionBoardItem } from '@/types'

const items = ref<ExhibitionBoardItem[]>([])
const loading = ref(false)

const formatTime = (value: string | null) => {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}

const loadBoard = async () => {
  loading.value = true
  try {
    const res = await exhibitionApi.getBoard()
    items.value = res.data
  } catch (error) {
    console.error('获取展陈看板失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadBoard()
})
</script>

<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div class="flex items-center gap-3">
        <Picture :size="24" class="text-indigo-600" />
        <h2 class="text-xl font-bold text-gray-800">展陈看板</h2>
        <span class="text-sm text-gray-500">共 {{ items.length }} 件已上墙</span>
      </div>
      <ElButton @click="loadBoard" :icon="Refresh">刷新</ElButton>
    </div>

    <ElAlert
      type="info"
      :closable="false"
      show-icon
      class="mb-4"
      title="上墙规则（展厅主任口径）：只有完成现场点交——件数、完好情况、接收人三样齐全——的作品才会出现在这里。未点交、点交中的作品一律不上墙；已点交后件数被改动的作品会被立即撤下，重新点交完成前不得回来。"
    />

    <ElCard class="shadow-sm">
      <ElTable :data="items" stripe v-loading="loading">
        <ElTableColumn prop="workId" label="ID" width="70">
          <template #default="{ row }">#{{ row.workId }}</template>
        </ElTableColumn>
        <ElTableColumn prop="workName" label="作品名称" min-width="160" />
        <ElTableColumn prop="category" label="品类" width="90">
          <template #default="{ row }">
            <span class="px-2 py-1 bg-gray-100 rounded text-sm">{{ row.category }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="creatorName" label="创作者" width="110" />
        <ElTableColumn label="件数" width="90">
          <template #default="{ row }">
            <span class="font-medium">{{ row.pieceCount ?? '-' }}</span>
            <span class="text-xs text-gray-400"> 件</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="conditionStatus" label="完好情况" width="110">
          <template #default="{ row }">
            <ElTag type="success" effect="plain">{{ row.conditionStatus }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="receiver" label="接收人" width="100" />
        <ElTableColumn label="点交完成时间" width="170">
          <template #default="{ row }">{{ formatTime(row.handoverCompletedAt) }}</template>
        </ElTableColumn>
        <ElTableColumn label="参展凭证" min-width="150">
          <template #default="{ row }">
            <ElTag v-if="row.certificateStatus === 'ISSUED'" type="success" effect="dark">
              已发放 {{ row.certificateNo }}
            </ElTag>
            <ElTag v-else type="info">未发放</ElTag>
          </template>
        </ElTableColumn>
        <template #empty>
          <div class="py-10 text-gray-400">
            <p class="text-base mb-1">展墙还是空的</p>
            <p class="text-sm">实物到厅并完成点交（件数、完好情况、接收人三样齐全）的作品才会上墙。</p>
          </div>
        </template>
      </ElTable>
    </ElCard>
  </div>
</template>
