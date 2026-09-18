import { createRouter, createWebHistory } from 'vue-router'
import WorkList from '@/views/WorkList.vue'
import WorkDetail from '@/views/WorkDetail.vue'
import ScoringPanel from '@/views/ScoringPanel.vue'
import Statistics from '@/views/Statistics.vue'
import Settings from '@/views/Settings.vue'
import Publications from '@/views/Publications.vue'
import ExhibitionBoard from '@/views/ExhibitionBoard.vue'

const routes = [
  {
    path: '/',
    name: 'WorkList',
    component: WorkList
  },
  {
    path: '/works/:id',
    name: 'WorkDetail',
    component: WorkDetail
  },
  {
    path: '/scoring',
    name: 'ScoringPanel',
    component: ScoringPanel
  },
  {
    path: '/exhibition',
    name: 'ExhibitionBoard',
    component: ExhibitionBoard
  },
  {
    path: '/publications',
    name: 'Publications',
    component: Publications
  },
  {
    path: '/statistics',
    name: 'Statistics',
    component: Statistics
  },
  {
    path: '/settings',
    name: 'Settings',
    component: Settings
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router