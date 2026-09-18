<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { Grid, Brush, TrendCharts, Setting, Promotion, Picture } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const menuItems = [
  { path: '/', name: '作品管理', icon: Grid },
  { path: '/scoring', name: '评委打分', icon: Brush },
  { path: '/exhibition', name: '展陈看板', icon: Picture },
  { path: '/publications', name: '对外公示', icon: Promotion },
  { path: '/statistics', name: '统计分析', icon: TrendCharts },
  { path: '/settings', name: '系统配置', icon: Setting }
]

const isActive = (path: string) => route.path === path
</script>

<template>
  <aside class="w-64 bg-white border-r border-gray-200 flex flex-col">
    <div class="p-6 border-b border-gray-200">
      <h1 class="text-xl font-bold text-indigo-600">评审系统</h1>
      <p class="text-xs text-gray-500 mt-1">文创投稿作品分级评审</p>
    </div>
    
    <nav class="flex-1 p-4">
      <ul class="space-y-2">
        <li v-for="item in menuItems" :key="item.path">
          <button
            @click="router.push(item.path)"
            :class="[
              'w-full flex items-center gap-3 px-4 py-3 rounded-lg transition-all duration-200',
              isActive(item.path)
                ? 'bg-indigo-50 text-indigo-700 font-medium'
                : 'text-gray-600 hover:bg-gray-50'
            ]"
          >
            <component :is="item.icon" :size="20" />
            <span>{{ item.name }}</span>
          </button>
        </li>
      </ul>
    </nav>
    
    <div class="p-4 border-t border-gray-200">
      <div class="text-xs text-gray-400 text-center">
        <p>Version 1.0.0</p>
      </div>
    </div>
  </aside>
</template>