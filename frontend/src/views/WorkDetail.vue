<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElCard, ElTable, ElTableColumn, ElTag, ElDescriptions, ElDescriptionsItem, ElAlert, ElIcon, ElInput, ElSelect, ElOption, ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Star, Lock, Warning, Box, Tickets, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { workApi, scoreApi, handoverApi, exhibitionApi, resolveErrorMessage } from '@/api'
import type { Work, Score, Handover } from '@/types'

const DIMENSIONS = [
  { key: 'creativity', label: '创意' },
  { key: 'completion', label: '完成度' },
  { key: 'commercial_potential', label: '商业潜力' },
  { key: 'craftsmanship', label: '工艺' }
] as const

const CONDITION_OPTIONS = ['完好', '外包装轻微磨损', '局部破损待确认']

const route = useRoute()
const router = useRouter()
const work = ref<Work | null>(null)
const scores = ref<Score[]>([])
const handover = ref<Handover | null>(null)

const handoverForm = ref({ conditionStatus: '', receiver: '' })
const handoverLoading = ref(false)
const certificateLoading = ref(false)

const workId = computed(() => parseInt(route.params.id as string))

const scoreByDimension = computed(() => {
  const map = new Map<string, Score>()
  for (const s of scores.value) {
    map.set(s.dimension, s)
  }
  return map
})

const missingDimensions = computed(() =>
  DIMENSIONS.filter(d => !scoreByDimension.value.has(d.key))
)

const isPendingScores = computed(() => work.value?.status === 'PENDING_SCORES')

const handoverStatus = computed(() => work.value?.handoverStatus ?? 'NOT_STARTED')
const isHandoverCompleted = computed(() => handoverStatus.value === 'COMPLETED')

const getWork = async () => {
  try {
    const response = await workApi.getWorkById(workId.value)
    work.value = response.data
  } catch (error) {
    console.error('获取作品详情失败:', error)
  }
}

const getScores = async () => {
  try {
    const response = await scoreApi.getScoresByWorkId(workId.value)
    scores.value = response.data
  } catch (error) {
    console.error('获取打分记录失败:', error)
  }
}

/** 当前点交单；未点交时后端返回 404，按"还没有单"处理 */
const getHandover = async () => {
  try {
    const response = await handoverApi.getCurrent(workId.value)
    handover.value = response.data
    handoverForm.value = {
      conditionStatus: response.data.conditionStatus ?? '',
      receiver: response.data.receiver ?? ''
    }
  } catch (error) {
    handover.value = null
    handoverForm.value = { conditionStatus: '', receiver: '' }
  }
}

const reloadHandoverState = async () => {
  await Promise.all([getWork(), getHandover()])
}

const handleStartHandover = async () => {
  handoverLoading.value = true
  try {
    await handoverApi.start(workId.value)
    ElMessage.success('已开始点交，请逐项登记')
    await reloadHandoverState()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '开始点交失败'))
  } finally {
    handoverLoading.value = false
  }
}

const handleSaveDraft = async () => {
  handoverLoading.value = true
  try {
    await handoverApi.updateDraft(workId.value, {
      conditionStatus: handoverForm.value.conditionStatus || undefined,
      receiver: handoverForm.value.receiver || undefined
    })
    ElMessage.success('点交内容已登记')
    await reloadHandoverState()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '登记失败'))
  } finally {
    handoverLoading.value = false
  }
}

const handleCompleteHandover = async () => {
  handoverLoading.value = true
  try {
    await handoverApi.complete(workId.value, {
      conditionStatus: handoverForm.value.conditionStatus || undefined,
      receiver: handoverForm.value.receiver || undefined
    })
    ElMessage.success('点交完成，作品已可上墙')
    await reloadHandoverState()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '点交单不成立'))
    await reloadHandoverState()
  } finally {
    handoverLoading.value = false
  }
}

const handleFailHandover = () => {
  ElMessageBox.confirm(
    '点交失败后整张点交单作废，已登记的内容全部清空，作品退回未点交。确定点交失败吗？',
    '点交失败确认',
    { type: 'warning', confirmButtonText: '确认失败并作废', cancelButtonText: '取消' }
  ).then(async () => {
    handoverLoading.value = true
    try {
      await handoverApi.fail(workId.value)
      ElMessage.success('点交单已作废，作品退回未点交')
      await reloadHandoverState()
    } catch (error) {
      ElMessage.error(resolveErrorMessage(error, '操作失败'))
    } finally {
      handoverLoading.value = false
    }
  }).catch(() => {})
}

const handleIssueCertificate = async () => {
  certificateLoading.value = true
  try {
    await exhibitionApi.issueCertificate(workId.value)
    ElMessage.success('参展凭证已发放')
    await getWork()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '发放失败'))
  } finally {
    certificateLoading.value = false
  }
}

const getGradeTagClass = (grade: string) => {
  switch (grade) {
    case 'S': return 'bg-red-100 text-red-800 font-bold text-xl'
    case 'A': return 'bg-orange-100 text-orange-800 font-bold text-xl'
    case 'B': return 'bg-blue-100 text-blue-800 font-bold text-xl'
    case 'C': return 'bg-gray-100 text-gray-800 font-bold text-xl'
    default: return 'bg-gray-100 text-gray-800'
  }
}

const getStatusLabel = (status: string) => {
  switch (status) {
    case 'PENDING': return '待审核'
    case 'APPROVED': return '已通过'
    case 'PENDING_SCORES': return '待齐分'
    case 'GRADED': return '已评分'
    default: return status
  }
}

const getStatusTagClass = (status: string) => {
  switch (status) {
    case 'PENDING': return 'warning'
    case 'APPROVED': return 'info'
    case 'PENDING_SCORES': return 'danger'
    case 'GRADED': return 'success'
    default: return 'info'
  }
}

const formatTime = (value: string | null) => {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}

onMounted(() => {
  getWork()
  getScores()
  getHandover()
})
</script>

<template>
  <div class="p-6">
    <div class="flex items-center gap-4 mb-6">
      <ElButton @click="router.push('/')" :icon="ArrowLeft">返回列表</ElButton>
      <h2 class="text-xl font-bold text-gray-800">作品详情</h2>
    </div>

    <div v-if="work" class="space-y-6">
      <ElAlert
        v-if="work.published"
        type="success"
        :closable="false"
        show-icon
        :icon="Lock"
        class="mb-2"
      >
        <template #title>
          该作品已于 {{ formatTime(work.publishedAt) }} 对外公示（批次 #{{ work.publicationBatchId }}）。
          综合分、等级、合格性已按公示当时的权重与合格线锁定，此后配置调整或评委改分均不会改变本页结果。
        </template>
      </ElAlert>

      <!-- 待齐分：缺维提示必须直达观众，且本页不展示任何"被零分垫出来"的综合分 -->
      <ElAlert
        v-else-if="isPendingScores"
        type="error"
        :closable="false"
        show-icon
        :icon="Warning"
        class="mb-2"
      >
        <template #title>
          该作品维度分未齐（待齐分），尚缺：{{ missingDimensions.map(d => d.label).join('、') }}。
          缺维不计 0 分：在评委补齐前不产生综合分与等级，也不进入统计平均与对外公示。
        </template>
      </ElAlert>

      <!-- 件数被改导致点交作废：展墙与凭证已撤下，必须重新点交 -->
      <ElAlert
        v-if="work.certificateStatus === 'REVOKED'"
        type="warning"
        :closable="false"
        show-icon
        :icon="Warning"
        class="mb-2"
      >
        <template #title>
          件数发生变更，原点交单已作废：作品已撤下展陈看板，参展凭证 {{ work.certificateNo }} 已撤销。
          重新完成点交前不得上墙，也不能重新领取参展凭证。
        </template>
      </ElAlert>

      <ElCard class="shadow-sm">
        <div class="flex items-start justify-between">
          <div class="flex-1">
            <div class="flex items-center gap-3 mb-4">
              <h3 class="text-2xl font-bold text-gray-800">{{ work.workName }}</h3>
              <span v-if="work.grade" :class="['px-4 py-2 rounded-lg', getGradeTagClass(work.grade)]">
                {{ work.grade }}级
              </span>
              <ElTag v-if="work.published" type="success" effect="dark" round>
                已公示 · 已锁定
              </ElTag>
              <ElTag v-else-if="work.status === 'GRADED'" type="warning" round>
                未公示 · 结果按最新配置计算
              </ElTag>
              <ElTag v-else-if="isPendingScores" type="danger" round>
                待齐分 · 暂无综合分
              </ElTag>
            </div>

            <ElDescriptions :column="2" border>
              <ElDescriptionsItem label="品类">{{ work.category }}</ElDescriptionsItem>
              <ElDescriptionsItem label="创作题材">{{ work.theme || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem label="创作者">{{ work.creatorName }}</ElDescriptionsItem>
              <ElDescriptionsItem label="联系电话">{{ work.creatorPhone || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem label="电子邮箱">{{ work.creatorEmail || '-' }}</ElDescriptionsItem>
              <ElDescriptionsItem label="状态">
                <ElTag :type="getStatusTagClass(work.status)">
                  {{ getStatusLabel(work.status) }}
                </ElTag>
              </ElDescriptionsItem>
              <ElDescriptionsItem label="件数">
                <span v-if="work.pieceCount != null">{{ work.pieceCount }} 件</span>
                <span v-else class="text-red-400 text-sm">未填（点交前必填）</span>
              </ElDescriptionsItem>
              <ElDescriptionsItem label="综合得分">
                <span v-if="work.totalScore != null" class="text-2xl font-bold text-indigo-600">
                  {{ Number(work.totalScore).toFixed(2) }}
                </span>
                <span v-else-if="isPendingScores" class="text-red-500 font-medium">
                  待齐分 · 不按零分凑
                </span>
                <span v-else class="text-gray-400">-</span>
                <span v-if="work.published" class="ml-2 text-xs text-gray-400">（公示快照值）</span>
              </ElDescriptionsItem>
              <ElDescriptionsItem label="是否合格">
                <ElTag v-if="work.status === 'GRADED'" :type="work.isQualified ? 'success' : 'danger'">
                  {{ work.isQualified ? '合格' : '不合格' }}
                </ElTag>
                <span v-else-if="isPendingScores" class="text-red-500 text-sm">
                  未齐分，暂无合格性结论
                </span>
                <span v-else class="text-gray-400">-</span>
                <span v-if="work.published" class="ml-2 text-xs text-gray-400">（按公示时合格线判定）</span>
              </ElDescriptionsItem>
              <ElDescriptionsItem v-if="work.published" label="公示批次">
                #{{ work.publicationBatchId }}
              </ElDescriptionsItem>
              <ElDescriptionsItem v-if="work.published" label="公示时间">
                {{ formatTime(work.publishedAt) }}
              </ElDescriptionsItem>
            </ElDescriptions>

            <div v-if="work.description" class="mt-4">
              <p class="text-gray-500 text-sm">作品描述：</p>
              <p class="text-gray-700 mt-1">{{ work.description }}</p>
            </div>
          </div>

          <div class="w-48 h-48 bg-gray-100 rounded-lg flex items-center justify-center ml-6">
            <img v-if="work.imageUrl" :src="work.imageUrl" :alt="work.workName" class="w-full h-full object-cover rounded-lg" />
            <span v-else class="text-gray-400 text-sm">暂无图片</span>
          </div>
        </div>
      </ElCard>

      <!-- 现场点交：未点交 → 点交中 → 已点交；三要素（件数、完好情况、接收人）缺一单不成立 -->
      <ElCard class="shadow-sm">
        <div class="flex items-center gap-2 mb-4">
          <Box :size="20" class="text-indigo-500" />
          <h3 class="text-lg font-semibold text-gray-800">现场点交</h3>
          <ElTag v-if="handoverStatus === 'COMPLETED'" type="success" effect="dark" class="ml-auto">已点交</ElTag>
          <ElTag v-else-if="handoverStatus === 'IN_PROGRESS'" type="warning" effect="dark" class="ml-auto">点交中</ElTag>
          <ElTag v-else type="info" effect="dark" class="ml-auto">未点交</ElTag>
        </div>

        <!-- 未点交：实物未交接，不能上墙、不能领证 -->
        <div v-if="handoverStatus === 'NOT_STARTED'" class="space-y-3">
          <p class="text-sm text-gray-600">
            实物尚未完成现场点交。按展厅主任口径：该作品<strong>不会出现在展陈看板</strong>，作者也<strong>领不出参展凭证</strong>。
          </p>
          <ElButton type="primary" :loading="handoverLoading" @click="handleStartHandover">
            开始点交
          </ElButton>
        </div>

        <!-- 点交中：逐项登记，三样齐了才能完成 -->
        <div v-else-if="handoverStatus === 'IN_PROGRESS'" class="space-y-4">
          <ElAlert
            type="warning"
            :closable="false"
            show-icon
            title="点交进行中：件数、完好情况、接收人三样缺一不可完成点交；中途失败将整单作废退回未点交。"
          />
          <div class="grid grid-cols-3 gap-4">
            <div>
              <p class="text-sm text-gray-500 mb-1">件数（取自作品档案）</p>
              <p v-if="work.pieceCount != null" class="text-xl font-bold text-gray-800">{{ work.pieceCount }} 件</p>
              <p v-else class="text-sm text-red-500">档案未填件数，请先在作品管理中补登</p>
            </div>
            <div>
              <p class="text-sm text-gray-500 mb-1">完好情况</p>
              <ElSelect
                v-model="handoverForm.conditionStatus"
                placeholder="选择或输入完好情况"
                filterable
                allow-create
                clearable
                class="w-full"
              >
                <ElOption v-for="opt in CONDITION_OPTIONS" :key="opt" :label="opt" :value="opt" />
              </ElSelect>
            </div>
            <div>
              <p class="text-sm text-gray-500 mb-1">接收人</p>
              <ElInput v-model="handoverForm.receiver" placeholder="现场接收人姓名" clearable />
            </div>
          </div>
          <div class="flex gap-2">
            <ElButton :loading="handoverLoading" @click="handleSaveDraft">保存登记</ElButton>
            <ElButton type="primary" :loading="handoverLoading" :icon="CircleCheck" @click="handleCompleteHandover">
              完成点交
            </ElButton>
            <ElButton type="danger" plain :loading="handoverLoading" :icon="CircleClose" @click="handleFailHandover">
              点交失败
            </ElButton>
          </div>
        </div>

        <!-- 已点交：展示钉死的点交单 -->
        <div v-else>
          <ElDescriptions :column="3" border>
            <ElDescriptionsItem label="件数（点交快照）">
              <span class="font-bold">{{ handover?.pieceCountSnapshot ?? work.pieceCount ?? '-' }}</span> 件
            </ElDescriptionsItem>
            <ElDescriptionsItem label="完好情况">{{ handover?.conditionStatus ?? '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="接收人">{{ handover?.receiver ?? '-' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="开始点交">{{ formatTime(handover?.startedAt ?? null) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="点交完成">{{ formatTime(handover?.completedAt ?? null) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="展墙状态">
              <ElTag type="success" effect="plain">已上墙（展陈看板可见）</ElTag>
            </ElDescriptionsItem>
          </ElDescriptions>
          <p class="text-xs text-gray-400 mt-3">
            提示：点交完成后若作者改动件数，点交单将作废、作品撤下展墙、参展凭证撤销，须重新点交。
          </p>
        </div>
      </ElCard>

      <!-- 参展凭证：点交完成才能领取 -->
      <ElCard class="shadow-sm">
        <div class="flex items-center gap-2 mb-4">
          <Tickets :size="20" class="text-amber-500" />
          <h3 class="text-lg font-semibold text-gray-800">参展凭证</h3>
          <ElTag v-if="work.certificateStatus === 'ISSUED'" type="success" effect="dark" class="ml-auto">已发放</ElTag>
          <ElTag v-else-if="work.certificateStatus === 'REVOKED'" type="danger" effect="dark" class="ml-auto">已撤销</ElTag>
          <ElTag v-else type="info" effect="dark" class="ml-auto">未发放</ElTag>
        </div>

        <div v-if="work.certificateStatus === 'ISSUED'" class="space-y-1">
          <p class="text-sm text-gray-600">凭证编号：<span class="font-mono font-bold text-indigo-600">{{ work.certificateNo }}</span></p>
          <p class="text-sm text-gray-600">发放时间：{{ formatTime(work.certificateIssuedAt) }}</p>
        </div>
        <div v-else-if="work.certificateStatus === 'REVOKED'" class="space-y-1">
          <p class="text-sm text-gray-600">
            原凭证 <span class="font-mono">{{ work.certificateNo }}</span> 已于 {{ formatTime(work.certificateRevokedAt) }} 撤销。
          </p>
          <p class="text-sm text-red-500">重新完成点交后才能再次领取。</p>
        </div>
        <div v-else class="space-y-3">
          <p class="text-sm text-gray-600">
            {{ isHandoverCompleted ? '点交已完成，可以领取参展凭证。' : '点交未完成，参展凭证不能发放——实物到厅并完成点交后才能领取。' }}
          </p>
        </div>

        <div v-if="work.certificateStatus !== 'ISSUED'" class="mt-4">
          <ElButton
            type="warning"
            :disabled="!isHandoverCompleted"
            :loading="certificateLoading"
            @click="handleIssueCertificate"
          >
            领取参展凭证
          </ElButton>
          <span v-if="!isHandoverCompleted" class="text-xs text-gray-400 ml-2">
            当前点交状态：{{ handoverStatus === 'IN_PROGRESS' ? '点交中' : '未点交' }}
          </span>
        </div>
      </ElCard>

      <ElCard class="shadow-sm">
        <div class="flex items-center gap-2 mb-4">
          <Star :size="20" class="text-yellow-500" />
          <h3 class="text-lg font-semibold text-gray-800">评委维度打分记录</h3>
          <span v-if="work.published" class="text-xs text-gray-400 ml-auto">
            分数以公示当时的记录为准，评委端已锁定不可修改
          </span>
          <span v-else-if="isPendingScores" class="text-xs text-red-500 ml-auto">
            已有 {{ scores.length }}/4 个维度，缺 {{ missingDimensions.length }} 个维度
          </span>
        </div>

        <!-- 维度卡片：缺的维度直接标红，由谁打过分一目了然 -->
        <div class="grid grid-cols-2 gap-4 mb-4">
          <div
            v-for="dim in DIMENSIONS"
            :key="dim.key"
            :class="['rounded-lg p-4 border-2',
              scoreByDimension.has(dim.key) ? 'bg-green-50 border-green-200' : 'bg-red-50 border-red-300']"
          >
            <div class="flex items-center justify-between">
              <span class="font-medium text-gray-700">{{ dim.label }}</span>
              <ElTag v-if="scoreByDimension.has(dim.key)" type="success" size="small">已打分</ElTag>
              <ElTag v-else type="danger" size="small">缺失</ElTag>
            </div>
            <div v-if="scoreByDimension.has(dim.key)" class="mt-2">
              <div class="text-2xl font-bold text-green-700">
                {{ Number(scoreByDimension.get(dim.key)?.value).toFixed(1) }}
                <span class="text-sm font-normal text-gray-500">分</span>
              </div>
              <div class="text-xs text-gray-500 mt-1">
                评委：{{ scoreByDimension.get(dim.key)?.judgeName }} ·
                {{ formatTime(scoreByDimension.get(dim.key)?.scoredAt ?? null) }}
              </div>
            </div>
            <div v-else class="mt-2 text-sm text-red-600">
              <ElIcon class="align-middle"><Warning /></ElIcon>
              该维度尚无评委打分，不得按 0 分计入综合分
            </div>
          </div>
        </div>

        <ElTable v-if="scores.length > 0" :data="scores" stripe>
          <ElTableColumn prop="judgeName" label="评委姓名" width="120" />
          <ElTableColumn label="维度" width="120">
            <template #default="{ row }">
              {{ DIMENSIONS.find(d => d.key === row.dimension)?.label ?? row.dimension }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="得分" width="100">
            <template #default="{ row }">
              <span class="font-medium">{{ Number(row.value).toFixed(2) }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="scoredAt" label="打分时间" width="180">
            <template #default="{ row }">
              {{ new Date(row.scoredAt).toLocaleString('zh-CN') }}
            </template>
          </ElTableColumn>
        </ElTable>

        <div v-else class="text-center py-8 text-gray-400">
          暂无打分记录
        </div>
      </ElCard>
    </div>
  </div>
</template>
