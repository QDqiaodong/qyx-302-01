<script setup lang="ts">
import { ref } from 'vue'
import { ElCard, ElTable, ElTableColumn, ElTag, ElButton, ElDialog, ElMessage, ElMessageBox } from 'element-plus'
import { Promotion, RefreshLeft, View } from '@element-plus/icons-vue'
import { publicationApi, resolveErrorMessage } from '@/api'
import type { PublicationBatch, Work } from '@/types'

const batches = ref<PublicationBatch[]>([])
const loading = ref(false)

const detailVisible = ref(false)
const currentBatch = ref<PublicationBatch | null>(null)
const batchWorks = ref<Work[]>([])

const publishAllLoading = ref(false)
const publishAllVisible = ref(false)
const publishAllName = ref('')

const formatTime = (value: string | null) => {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}

const loadBatches = async () => {
  loading.value = true
  try {
    const res = await publicationApi.listBatches()
    batches.value = res.data
  } catch (error) {
    console.error('获取公示批次失败:', error)
    ElMessage.error('获取公示批次失败')
  } finally {
    loading.value = false
  }
}

const openPublishAll = () => {
  publishAllName.value = ''
  publishAllVisible.value = true
}

const handlePublishAll = async () => {
  publishAllLoading.value = true
  try {
    // workIds 留空：后端把全部已评分且未公示的作品放进同一批、同一快照锁定
    const res = await publicationApi.publish({
      batchName: publishAllName.value.trim() || undefined
    })
    ElMessage.success(`公示成功：批次 #${res.data.id}，共锁定 ${res.data.workCount} 件作品`)
    publishAllVisible.value = false
    loadBatches()
  } catch (error) {
    console.error('公示失败:', error)
    ElMessage.error(resolveErrorMessage(error, '公示失败，整批已保持未公示状态'))
  } finally {
    publishAllLoading.value = false
  }
}

const viewBatch = async (batch: PublicationBatch) => {
  currentBatch.value = batch
  try {
    const res = await publicationApi.listBatchWorks(batch.id)
    batchWorks.value = res.data
    detailVisible.value = true
  } catch (error) {
    ElMessage.error('获取批次作品失败')
  }
}

const handleRevoke = (batch: PublicationBatch) => {
  ElMessageBox.confirm(
    `确定整批撤回 #${batch.id} 吗？批内 ${batch.workCount} 件作品将全部回到未公示状态，并按当前权重/合格线重新出分。`,
    '整批撤回确认',
    { type: 'warning', confirmButtonText: '整批撤回', cancelButtonText: '取消' }
  ).then(async () => {
    try {
      await publicationApi.revoke(batch.id)
      ElMessage.success(`批次 #${batch.id} 已整批撤回`)
      loadBatches()
    } catch (error) {
      console.error('撤回失败:', error)
      ElMessage.error(resolveErrorMessage(error, '撤回失败'))
    }
  }).catch(() => {})
}
</script>

<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div class="flex items-center gap-3">
        <Promotion :size="24" class="text-indigo-600" />
        <h2 class="text-xl font-bold text-gray-800">对外公示批次</h2>
      </div>
      <ElButton type="warning" @click="openPublishAll" :icon="Promotion">
        公示全部未公示作品
      </ElButton>
    </div>

    <ElCard class="shadow-sm">
      <template #header>
        <span class="font-semibold">每个批次 = 一套不可变的权重 / 合格线快照</span>
      </template>
      <ElTable :data="batches" stripe v-loading="loading">
        <ElTableColumn prop="id" label="批次" width="80">
          <template #default="{ row }">#{{ row.id }}</template>
        </ElTableColumn>
        <ElTableColumn prop="batchName" label="批次名称" min-width="180">
          <template #default="{ row }">{{ row.batchName || '（未命名批次）' }}</template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="100">
          <template #default="{ row }">
            <ElTag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" effect="dark">
              {{ row.status === 'PUBLISHED' ? '已公示' : '已撤回' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="快照权重（创意/完成/商业/工艺）" min-width="240">
          <template #default="{ row }">
            {{ row.creativityWeight }} / {{ row.completionWeight }} /
            {{ row.commercialPotentialWeight }} / {{ row.craftsmanshipWeight }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="合格线" width="90">
          <template #default="{ row }">{{ row.qualifiedScore }}</template>
        </ElTableColumn>
        <ElTableColumn prop="workCount" label="作品数" width="80" />
        <ElTableColumn label="公示时间" width="180">
          <template #default="{ row }">{{ formatTime(row.publishedAt) }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="170">
          <template #default="{ row }">
            <ElButton size="small" @click="viewBatch(row as PublicationBatch)" :icon="View">作品</ElButton>
            <ElButton
              v-if="row.status === 'PUBLISHED'"
              size="small"
              type="danger"
              :icon="RefreshLeft"
              @click="handleRevoke(row as PublicationBatch)"
            >整批撤回</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>

    <ElDialog v-model="publishAllVisible" title="公示全部未公示作品" width="520px">
      <p class="text-sm text-gray-600 mb-4">
        将把所有「已评分且未公示」的作品放入同一批次，按当前权重与合格线一次性锁定。
        已公示作品不受影响、不会重算。
      </p>
      <ElInput v-model="publishAllName" placeholder="批次名称（可选）" />
      <template #footer>
        <ElButton @click="publishAllVisible = false">取消</ElButton>
        <ElButton type="warning" :loading="publishAllLoading" @click="handlePublishAll">确认公示</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="detailVisible" :title="`批次 #${currentBatch?.id ?? ''} 的作品（${batchWorks.length} 件）`" width="720px">
      <ElTable :data="batchWorks" stripe max-height="400">
        <ElTableColumn prop="id" label="ID" width="60" />
        <ElTableColumn prop="workName" label="作品名称" min-width="150" />
        <ElTableColumn prop="creatorName" label="创作者" width="100" />
        <ElTableColumn label="公示综合分" width="110">
          <template #default="{ row }">
            <span class="font-bold text-indigo-600">
              {{ row.publishedTotalScore != null ? Number(row.publishedTotalScore).toFixed(2) : '-' }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="publishedGrade" label="公示等级" width="90">
          <template #default="{ row }">
            <ElTag>{{ row.publishedGrade }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="公示合格性" width="100">
          <template #default="{ row }">
            <ElTag :type="row.publishedIsQualified ? 'success' : 'danger'">
              {{ row.publishedIsQualified ? '合格' : '不合格' }}
            </ElTag>
          </template>
        </ElTableColumn>
      </ElTable>
      <template #footer>
        <ElButton @click="detailVisible = false">关闭</ElButton>
      </template>
    </ElDialog>
  </div>
</template>
