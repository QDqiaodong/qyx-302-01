<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElTable, ElTableColumn, ElButton, ElForm, ElFormItem, ElInput, ElMessage, ElCard, ElSlider, ElDivider, ElTag, ElAlert, ElIcon } from 'element-plus'
import { Brush, Upload, Lock, Warning } from '@element-plus/icons-vue'
import { workApi, scoreApi, resolveErrorMessage } from '@/api'
import type { Work, Score } from '@/types'

// 维度口径与后端完全一致：下划线 key。缺维度 = 没有该 key 的有效分，而不是 0 分。
type DimKey = 'creativity' | 'completion' | 'commercial_potential' | 'craftsmanship'
const DIMENSIONS: { key: DimKey; label: string; description: string }[] = [
  { key: 'creativity', label: '创意', description: '作品的创新性和独特性' },
  { key: 'completion', label: '完成度', description: '作品的完整性和细节处理' },
  { key: 'commercial_potential', label: '商业潜力', description: '作品的市场价值和应用前景' },
  { key: 'craftsmanship', label: '工艺', description: '作品的制作工艺和技术水平' }
]

const works = ref<Work[]>([])
const selectedWork = ref<Work | null>(null)
// 选中作品当前已落库的维度分（每维至多一条有效分）
const existingScores = ref<Score[]>([])
// 本次补齐缺失维度的草稿：dimension -> 分数
const draft = ref<Record<DimKey, number>>({
  creativity: 50,
  completion: 50,
  commercial_potential: 50,
  craftsmanship: 50
})
const submitting = ref(false)

const judgeName = ref('')
const judgeId = ref(1)

// dimension -> 已落库的有效分
const scoreByDimension = computed(() => {
  const map = new Map<DimKey, Score>()
  for (const s of existingScores.value) {
    map.set(s.dimension as DimKey, s)
  }
  return map
})

const missingDimensions = computed<DimKey[]>(
  () => DIMENSIONS.filter(d => !scoreByDimension.value.has(d.key)).map(d => d.key)
)

const missingLabels = computed(() =>
  DIMENSIONS.filter(d => missingDimensions.value.includes(d.key)).map(d => d.label)
)

const isComplete = computed(() => missingDimensions.value.length === 0)

const getApprovedWorks = async (preferredWorkId?: number) => {
  try {
    // 打分面板只处理未公示作品：待打分(APPROVED)、待齐分(PENDING_SCORES)。
    // 四维齐全的未公示 GRADED 作品不再出现在待办里（公示前配置仍可即时重算），
    // 已公示作品后端会二次拦截（409）。
    const [approvedRes, pendingRes] = await Promise.all([
      workApi.getWorks({ status: 'APPROVED', published: false, size: 100 }),
      workApi.getWorks({ status: 'PENDING_SCORES', published: false, size: 100 })
    ])
    works.value = [...approvedRes.data.content, ...pendingRes.data.content]

    const keepId = preferredWorkId ?? selectedWork.value?.id
    const refreshed = keepId != null ? works.value.find(w => w.id === keepId) : undefined
    if (refreshed) {
      await selectWork(refreshed)
    } else if (works.value.length > 0) {
      await selectWork(works.value[0])
    } else {
      selectedWork.value = null
      existingScores.value = []
    }
  } catch (error) {
    console.error('获取待评分作品失败:', error)
    ElMessage.error('获取待评分作品失败')
  }
}

const selectWork = async (work: Work) => {
  selectedWork.value = work
  try {
    const res = await scoreApi.getScoresByWorkId(work.id)
    existingScores.value = res.data
    // 草稿只对缺失维度有意义，每次切换重置
    draft.value = { creativity: 50, completion: 50, commercial_potential: 50, craftsmanship: 50 }
  } catch (error) {
    console.error('获取已有维度分失败:', error)
    ElMessage.error('获取已有维度分失败')
  }
}

const handleSubmit = async () => {
  if (!selectedWork.value) {
    ElMessage.warning('请先选择作品')
    return
  }
  if (!judgeName.value.trim()) {
    ElMessage.warning('请输入评委姓名')
    return
  }

  // 只提交仍缺失的维度：已有人写过的维度滑块是锁死的，不会进入提交体。
  const dimensions: ScoreSubmitDimensions = {}
  for (const key of missingDimensions.value) {
    dimensions[key] = draft.value[key]
  }
  if (Object.keys(dimensions).length === 0) {
    ElMessage.warning('该作品四维均已打分，无需再提交')
    return
  }

  submitting.value = true
  const submittedCount = Object.keys(dimensions).length
  try {
    await scoreApi.submitScore({
      workId: selectedWork.value.id,
      judgeId: judgeId.value,
      judgeName: judgeName.value.trim(),
      dimensions
    })

    const remaining = missingDimensions.value.length - submittedCount
    ElMessage.success(
      remaining <= 0
        ? '已补齐全部维度，作品进入综合分计算（按当前权重/合格线即时出分）'
        : `提交成功，作品仍缺 ${remaining} 个维度，保持待齐分（不会产生综合分）`
    )
    // 重新拉列表+该作品已有维度分：自己的提交落库，或并发时别人抢先的维度会显示为已填
    await getApprovedWorks()
  } catch (error) {
    console.error('提交打分失败:', error)
    ElMessage.error(resolveErrorMessage(error, '提交打分失败'))
    // 并发被抢（409）等场景：刷新已有维度分，让评委立刻看到这一维已有人写过
    await getApprovedWorks(selectedWork.value?.id)
  } finally {
    submitting.value = false
  }
}

type ScoreSubmitDimensions = Parameters<typeof scoreApi.submitScore>[0]['dimensions']

onMounted(() => {
  getApprovedWorks()
})
</script>

<template>
  <div class="p-6">
    <div class="flex items-center gap-3 mb-6">
      <Brush :size="24" class="text-indigo-600" />
      <h2 class="text-xl font-bold text-gray-800">评委打分面板</h2>
    </div>

    <ElAlert
      type="info"
      :closable="false"
      show-icon
      class="mb-4"
      title="打分规则：一件作品四个维度（创意、完成度、商业潜力、工艺）全部有有效分后才会产生综合分。可以只交自己负责的维度；缺任何一维作品都停在“待齐分”，综合分与统计图都不会把缺的维度当 0 分凑。每个维度只保留一条有效分。"
    />

    <div class="grid grid-cols-12 gap-6">
      <div class="col-span-4">
        <ElCard class="shadow-sm h-full">
          <template #header>
            <div class="flex items-center justify-between">
              <span class="font-semibold">待齐分作品列表</span>
              <span class="text-sm text-gray-500">共 {{ works.length }} 件</span>
            </div>
          </template>

          <ElTable :data="works" stripe @row-click="selectWork" highlight-current-row class="cursor-pointer">
            <ElTableColumn prop="id" label="ID" width="60" />
            <ElTableColumn prop="workName" label="作品名称">
              <template #default="{ row }">
                <div class="flex flex-col">
                  <span>{{ row.workName }}</span>
                  <ElTag
                    v-if="row.status === 'PENDING_SCORES'"
                    size="small"
                    type="warning"
                    class="mt-1 w-fit"
                  >
                    待齐分 · 缺维度
                  </ElTag>
                  <ElTag v-else size="small" type="info" class="mt-1 w-fit">
                    待打分
                  </ElTag>
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="category" label="品类" width="80" />
            <ElTableColumn prop="creatorName" label="创作者" width="100" />
          </ElTable>
        </ElCard>
      </div>

      <div class="col-span-8">
        <ElCard class="shadow-sm">
          <template #header>
            <div class="flex items-center justify-between">
              <span class="font-semibold">作品信息</span>
            </div>
          </template>

          <div v-if="selectedWork" class="space-y-6">
            <div class="flex items-start gap-6">
              <div class="w-32 h-32 bg-gray-100 rounded-lg flex items-center justify-center">
                <img v-if="selectedWork.imageUrl" :src="selectedWork.imageUrl" :alt="selectedWork.workName" class="w-full h-full object-cover rounded-lg" />
                <span v-else class="text-gray-400 text-sm">暂无图片</span>
              </div>
              <div class="flex-1">
                <div class="flex items-center gap-2 mb-2">
                  <h3 class="text-xl font-bold text-gray-800">{{ selectedWork.workName }}</h3>
                  <ElTag v-if="isComplete" type="success" size="small">四维已齐</ElTag>
                  <ElTag v-else type="danger" size="small">
                    待齐分 · 还缺：{{ missingLabels.join('、') }}
                  </ElTag>
                </div>
                <p class="text-gray-500 text-sm mb-1">品类：{{ selectedWork.category }}</p>
                <p class="text-gray-500 text-sm mb-1">题材：{{ selectedWork.theme || '-' }}</p>
                <p class="text-gray-500 text-sm">创作者：{{ selectedWork.creatorName }}</p>
              </div>
            </div>

            <!-- 缺维醒目提示：缺的维度不出综合分，不允许观众看到被零分垫出来的成绩 -->
            <ElAlert
              v-if="!isComplete"
              type="error"
              :closable="false"
              show-icon
              :icon="Warning"
              :title="`该作品还缺 ${missingDimensions.length} 个维度：${missingLabels.join('、')}。补齐前作品停在待齐分，不产生综合分，也不进入统计平均。`"
            />
            <ElAlert
              v-else
              type="success"
              :closable="false"
              show-icon
              title="四维已全部有有效分，综合分已按当前权重/合格线即时计算。"
            />

            <ElDivider />

            <ElForm :model="{ judgeName, judgeId }" label-width="100px">
              <ElFormItem label="评委姓名" required>
                <ElInput v-model="judgeName" placeholder="请输入评委姓名" class="w-48" />
              </ElFormItem>
              <ElFormItem label="评委ID">
                <ElInput v-model="judgeId" type="number" placeholder="请输入评委ID" class="w-48" />
              </ElFormItem>
            </ElForm>

            <ElDivider />

            <div class="space-y-6">
              <div
                v-for="dim in DIMENSIONS"
                :key="dim.key"
                :class="['rounded-lg p-4 border-2', scoreByDimension.get(dim.key)
                  ? 'bg-green-50 border-green-200'
                  : 'bg-red-50 border-red-300']"
              >
                <div class="flex justify-between items-center mb-2">
                  <div class="flex items-center gap-2">
                    <span class="font-medium text-gray-800">{{ dim.label }}</span>
                    <ElTag v-if="scoreByDimension.get(dim.key)" type="success" size="small">
                      <ElIcon class="mr-0.5"><Lock /></ElIcon>
                      已由 {{ scoreByDimension.get(dim.key)?.judgeName }} 打分：
                      {{ Number(scoreByDimension.get(dim.key)?.value).toFixed(1) }} 分
                    </ElTag>
                    <ElTag v-else type="danger" size="small">缺失 · 待评委打分</ElTag>
                  </div>
                  <span class="text-xs text-gray-400">{{ dim.description }}</span>
                </div>
                <div class="flex items-center gap-4">
                  <ElSlider
                    :model-value="scoreByDimension.get(dim.key)
                      ? Number(scoreByDimension.get(dim.key)?.value)
                      : draft[dim.key]"
                    @update:model-value="(val: number | number[]) => {
                      if (!scoreByDimension.get(dim.key)) {
                        draft[dim.key] = Array.isArray(val) ? val[0] : val
                      }
                    }"
                    :min="0"
                    :max="100"
                    :step="0.5"
                    :disabled="!!scoreByDimension.get(dim.key)"
                    class="flex-1"
                  />
                  <ElInput
                    :model-value="scoreByDimension.get(dim.key)
                      ? Number(scoreByDimension.get(dim.key)?.value)
                      : draft[dim.key]"
                    @update:model-value="(val: string) => {
                      if (!scoreByDimension.get(dim.key)) {
                        draft[dim.key] = parseFloat(val) || 0
                      }
                    }"
                    type="number"
                    :min="0"
                    :max="100"
                    :disabled="!!scoreByDimension.get(dim.key)"
                    class="w-20"
                  />
                </div>
              </div>
            </div>

            <div class="flex justify-end pt-4">
              <ElButton
                type="primary"
                @click="handleSubmit"
                :icon="Upload"
                size="large"
                :loading="submitting"
                :disabled="isComplete"
              >
                {{ isComplete ? '四维已齐 · 无需提交' : `提交缺失维度（${missingDimensions.length} 项）` }}
              </ElButton>
            </div>
          </div>

          <div v-else class="text-center py-12 text-gray-400">
            没有待齐分作品——所有未公示作品均已凑齐四维评分
          </div>
        </ElCard>
      </div>
    </div>
  </div>
</template>
