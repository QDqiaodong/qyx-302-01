<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { ElCard, ElTable, ElTableColumn, ElTag, ElButton, ElAlert } from 'element-plus'
import { TrendCharts, Refresh, Warning } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { statisticsApi } from '@/api'
import type { GradeDistribution, DimensionDistribution, Work } from '@/types'

const gradeDistribution = ref<GradeDistribution | null>(null)
const dimensionDistribution = ref<DimensionDistribution | null>(null)
const failedWorks = ref<Work[]>([])

const gradeChartRef = ref<HTMLDivElement | null>(null)
const dimensionChartRef = ref<HTMLDivElement | null>(null)

let gradeChart: echarts.ECharts | null = null
let dimensionChart: echarts.ECharts | null = null

const gradeColors = {
  S: '#ef4444',
  A: '#f97316',
  B: '#3b82f6',
  C: '#6b7280'
}

const loadStatistics = async () => {
  try {
    const [gradeRes, dimensionRes, failedRes] = await Promise.all([
      statisticsApi.getGradeDistribution(),
      statisticsApi.getDimensionDistribution(),
      statisticsApi.getFailedWorks()
    ])
    
    gradeDistribution.value = gradeRes.data
    dimensionDistribution.value = dimensionRes.data
    failedWorks.value = failedRes.data
    
    updateCharts()
  } catch (error) {
    console.error('获取统计数据失败:', error)
  }
}

const updateCharts = () => {
  if (gradeChartRef.value && gradeDistribution.value) {
    if (!gradeChart) {
      gradeChart = echarts.init(gradeChartRef.value)
    }
    
    const gradeLabels = Object.keys(gradeDistribution.value.gradeCounts)
    const gradeData = gradeLabels.map(label => ({
      value: gradeDistribution.value?.gradeCounts[label] || 0,
      name: `${label}级`,
      itemStyle: { color: gradeColors[label as keyof typeof gradeColors] }
    }))
    
    gradeChart.setOption({
      title: {
        text: '作品等级分布',
        left: 'center',
        textStyle: { fontSize: 16, fontWeight: 'bold' }
      },
      tooltip: {
        trigger: 'item',
        formatter: '{b}: {c}件 ({d}%)'
      },
      legend: {
        bottom: 0,
        data: gradeLabels.map(l => `${l}级`)
      },
      series: [{
        name: '等级分布',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 8,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: true,
          formatter: '{b}\n{d}%'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold'
          }
        },
        data: gradeData
      }]
    })
  }
  
  if (dimensionChartRef.value && dimensionDistribution.value) {
    if (!dimensionChart) {
      dimensionChart = echarts.init(dimensionChartRef.value)
    }
    
    const dimensionKeys = Object.keys(dimensionDistribution.value.dimensionAverages)
    const dimensionNames = dimensionDistribution.value.dimensionNames
    const dimensionValues = dimensionKeys.map(key => dimensionDistribution.value?.dimensionAverages[key] || 0)
    
    dimensionChart.setOption({
      title: {
        text: '各维度平均得分',
        left: 'center',
        textStyle: { fontSize: 16, fontWeight: 'bold' }
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: '{b}: {c}分'
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: dimensionKeys.map(k => dimensionNames[k]),
        axisLabel: { fontSize: 12 }
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: 100,
        axisLabel: { formatter: '{value}分' }
      },
      series: [{
        name: '平均分',
        type: 'bar',
        barWidth: '50%',
        data: dimensionValues,
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#6366f1' },
            { offset: 1, color: '#8b5cf6' }
          ])
        },
        label: {
          show: true,
          position: 'top',
          formatter: '{c}分',
          fontSize: 12
        }
      }]
    })
  }
}

const handleRefresh = () => {
  loadStatistics()
}

const handleResize = () => {
  gradeChart?.resize()
  dimensionChart?.resize()
}

watch([gradeDistribution, dimensionDistribution], () => {
  updateCharts()
}, { deep: true })

onMounted(() => {
  loadStatistics()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  gradeChart?.dispose()
  dimensionChart?.dispose()
})
</script>

<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div class="flex items-center gap-3">
        <TrendCharts :size="24" class="text-indigo-600" />
        <h2 class="text-xl font-bold text-gray-800">统计分析</h2>
      </div>
      <ElButton @click="handleRefresh" :icon="Refresh">刷新数据</ElButton>
    </div>

    <ElAlert
      type="info"
      :closable="false"
      show-icon
      class="mb-4"
      title="统计口径：等级分布与各维度平均分只计入四维已齐分的作品；处于“待齐分”的缺维作品不会被当 0 分垫进任何图表与落选名单。"
    />

    <div class="grid grid-cols-12 gap-6 mb-6">
      <div class="col-span-6">
        <ElCard class="shadow-sm">
          <div ref="gradeChartRef" class="h-80"></div>
        </ElCard>
      </div>
      
      <div class="col-span-6">
        <ElCard class="shadow-sm">
          <div ref="dimensionChartRef" class="h-80"></div>
        </ElCard>
      </div>
    </div>

    <div v-if="gradeDistribution" class="grid grid-cols-4 gap-4 mb-6">
      <div v-for="(count, grade) in gradeDistribution.gradeCounts" :key="grade" 
           :class="['p-4 rounded-lg text-center', grade === 'S' ? 'bg-red-50' : grade === 'A' ? 'bg-orange-50' : grade === 'B' ? 'bg-blue-50' : 'bg-gray-50']">
        <div :class="['text-3xl font-bold mb-1', grade === 'S' ? 'text-red-600' : grade === 'A' ? 'text-orange-600' : grade === 'B' ? 'text-blue-600' : 'text-gray-600']">
          {{ grade }}级
        </div>
        <div class="text-gray-600">{{ count }}件</div>
        <div class="text-sm text-gray-400">{{ (gradeDistribution.gradePercentages[grade] || 0).toFixed(1) }}%</div>
      </div>
    </div>

    <ElCard class="shadow-sm">
      <div class="flex items-center gap-2 mb-4">
        <Warning :size="20" class="text-orange-500" />
        <h3 class="text-lg font-semibold text-gray-800">落选作品（未达合格线）</h3>
        <span class="text-sm text-gray-500 ml-auto">共 {{ failedWorks.length }} 件</span>
      </div>
      
      <ElTable v-if="failedWorks.length > 0" :data="failedWorks" stripe>
        <ElTableColumn prop="id" label="ID" width="60" />
        <ElTableColumn prop="workName" label="作品名称" min-width="150" />
        <ElTableColumn prop="category" label="品类" width="80" />
        <ElTableColumn prop="creatorName" label="创作者" width="100" />
        <ElTableColumn prop="totalScore" label="综合得分" width="100">
          <template #default="{ row }">
            <span class="text-red-600 font-medium">{{ row.totalScore.toFixed(2) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="grade" label="等级" width="80">
          <template #default="{ row }">
            <ElTag type="danger">{{ row.grade }}</ElTag>
          </template>
        </ElTableColumn>
      </ElTable>
      
      <div v-else class="text-center py-8 text-gray-400">
        暂无落选作品
      </div>
    </ElCard>
  </div>
</template>