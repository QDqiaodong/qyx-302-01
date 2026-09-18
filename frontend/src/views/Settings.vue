<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElCard, ElForm, ElFormItem, ElInput, ElButton, ElMessage, ElSlider, ElAlert } from 'element-plus'
import { Setting, Check } from '@element-plus/icons-vue'
import { configApi, resolveErrorMessage } from '@/api'
import type { WeightConfig, ThresholdConfig } from '@/types'

const weights = ref<WeightConfig>({
  creativity: 0.3,
  completion: 0.25,
  commercialPotential: 0.25,
  craftsmanship: 0.2
})

const threshold = ref<ThresholdConfig>({
  id: 0,
  qualifiedScore: 60,
  extremeThreshold: 20,
  updatedAt: ''
})

const weightDimensions = [
  { key: 'creativity', label: '创意', description: '作品的创新性和独特性' },
  { key: 'completion', label: '完成度', description: '作品的完整性和细节处理' },
  { key: 'commercialPotential', label: '商业潜力', description: '作品的市场价值和应用前景' },
  { key: 'craftsmanship', label: '工艺', description: '作品的制作工艺和技术水平' }
]

const totalWeight = computed(() => {
  return (weights.value.creativity + weights.value.completion + 
          weights.value.commercialPotential + weights.value.craftsmanship).toFixed(2)
})

import { computed } from 'vue'

const loadConfig = async () => {
  try {
    const [weightsRes, thresholdRes] = await Promise.all([
      configApi.getWeights(),
      configApi.getThreshold()
    ])
    
    weights.value = weightsRes.data
    threshold.value = thresholdRes.data
  } catch (error) {
    console.error('获取配置失败:', error)
    ElMessage.error('获取配置失败')
  }
}

const saveWeights = async () => {
  const total = parseFloat(totalWeight.value)
  if (Math.abs(total - 1) > 0.01) {
    ElMessage.warning(`权重总和必须为1，当前总和为 ${total}`)
    return
  }
  
  try {
    await configApi.updateWeights(weights.value)
    ElMessage.success('权重配置已保存：未公示作品已按新口径重算，已公示作品保持公示快照不变')
  } catch (error) {
    console.error('保存权重配置失败:', error)
    ElMessage.error(resolveErrorMessage(error, '保存权重配置失败'))
  }
}

const saveThreshold = async () => {
  try {
    await configApi.updateThreshold({
      qualifiedScore: threshold.value.qualifiedScore,
      extremeThreshold: threshold.value.extremeThreshold
    })
    ElMessage.success('阈值配置已保存：未公示作品已按新合格线重算，已公示作品保持公示快照不变')
  } catch (error) {
    console.error('保存阈值配置失败:', error)
    ElMessage.error(resolveErrorMessage(error, '保存阈值配置失败'))
  }
}

onMounted(() => {
  loadConfig()
})
</script>

<template>
  <div class="p-6">
    <div class="flex items-center gap-3 mb-6">
      <Setting :size="24" class="text-indigo-600" />
      <h2 class="text-xl font-bold text-gray-800">系统配置</h2>
    </div>

    <ElAlert
      type="info"
      :closable="false"
      show-icon
      class="mb-6"
      title="配置生效范围：保存后立即按新权重/合格线重算所有「未公示的已评分作品」；已进入对外公示批次的作品继续展示公示当时的快照，永远不回溯重算。权重被证明不合理时，请用新配置另开下一批公示。"
    />

    <div class="grid grid-cols-12 gap-6">
      <div class="col-span-7">
        <ElCard class="shadow-sm">
          <template #header>
            <div class="flex items-center justify-between">
              <span class="font-semibold">打分维度权重配置</span>
              <span :class="['text-sm font-medium', parseFloat(totalWeight) === 1 ? 'text-green-600' : 'text-red-600']">
                权重总和: {{ totalWeight }}
              </span>
            </div>
          </template>
          
          <ElForm :model="weights" label-width="120px" class="space-y-6">
            <div v-for="dim in weightDimensions" :key="dim.key" class="bg-gray-50 rounded-lg p-4">
              <div class="flex justify-between items-center mb-2">
                <div>
                  <span class="font-medium text-gray-800">{{ dim.label }}</span>
                  <span class="text-sm text-gray-500 ml-2">({{ (weights[dim.key as keyof typeof weights] * 100).toFixed(0) }}%)</span>
                </div>
                <span class="text-xs text-gray-400">{{ dim.description }}</span>
              </div>
              <div class="flex items-center gap-4">
                <ElSlider
                  v-model="weights[dim.key as keyof typeof weights]"
                  :min="0"
                  :max="1"
                  :step="0.01"
                  class="flex-1"
                />
                <ElInput
                  v-model.number="weights[dim.key as keyof typeof weights]"
                  type="number"
                  :min="0"
                  :max="1"
                  :step="0.01"
                  class="w-20"
                />
              </div>
            </div>
            
            <div class="flex justify-end">
              <ElButton type="primary" @click="saveWeights" :icon="Check">
                保存权重配置
              </ElButton>
            </div>
          </ElForm>
        </ElCard>
      </div>

      <div class="col-span-5">
        <ElCard class="shadow-sm">
          <template #header>
            <span class="font-semibold">评分阈值配置</span>
          </template>
          
          <ElForm :model="threshold" label-width="140px" class="space-y-6">
            <ElFormItem label="合格线分值" required>
              <div class="flex items-center gap-4">
                <ElSlider
                  v-model="threshold.qualifiedScore"
                  :min="0"
                  :max="100"
                  :step="1"
                  class="flex-1"
                />
                <ElInput
                  v-model.number="threshold.qualifiedScore"
                  type="number"
                  :min="0"
                  :max="100"
                  class="w-20"
                />
              </div>
              <p class="text-xs text-gray-400 mt-1">低于此分数的作品将被标记为落选</p>
            </ElFormItem>
            
            <ElFormItem label="极端分阈值" required>
              <div class="flex items-center gap-4">
                <ElSlider
                  v-model="threshold.extremeThreshold"
                  :min="0"
                  :max="50"
                  :step="1"
                  class="flex-1"
                />
                <ElInput
                  v-model.number="threshold.extremeThreshold"
                  type="number"
                  :min="0"
                  :max="50"
                  class="w-20"
                />
              </div>
              <p class="text-xs text-gray-400 mt-1">与平均分差值超过此值的分数将被剔除</p>
            </ElFormItem>
            
            <div class="flex justify-end">
              <ElButton type="primary" @click="saveThreshold" :icon="Check">
                保存阈值配置
              </ElButton>
            </div>
          </ElForm>
        </ElCard>

        <ElCard class="shadow-sm mt-6">
          <template #header>
            <span class="font-semibold">等级划分规则</span>
          </template>
          
          <div class="space-y-3">
            <div class="flex items-center justify-between py-2 border-b border-gray-100">
              <span class="font-bold text-red-600">S级</span>
              <span class="text-gray-600">90 - 100分</span>
            </div>
            <div class="flex items-center justify-between py-2 border-b border-gray-100">
              <span class="font-bold text-orange-600">A级</span>
              <span class="text-gray-600">80 - 89分</span>
            </div>
            <div class="flex items-center justify-between py-2 border-b border-gray-100">
              <span class="font-bold text-blue-600">B级</span>
              <span class="text-gray-600">60 - 79分</span>
            </div>
            <div class="flex items-center justify-between py-2">
              <span class="font-bold text-gray-600">C级</span>
              <span class="text-gray-600">0 - 59分</span>
            </div>
          </div>
        </ElCard>
      </div>
    </div>
  </div>
</template>