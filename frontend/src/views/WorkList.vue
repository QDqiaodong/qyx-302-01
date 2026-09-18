<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElTable, ElTableColumn, ElButton, ElInput, ElInputNumber, ElSelect, ElDialog, ElForm, ElFormItem, ElMessage, ElTag, ElPagination } from 'element-plus'
import { Plus, Search, View, Check, Delete, Edit, Promotion } from '@element-plus/icons-vue'
import { workApi, publicationApi, resolveErrorMessage } from '@/api'
import type { Work } from '@/types'

const router = useRouter()
const works = ref<Work[]>([])
const total = ref(0)
const page = ref(0)
const size = ref(10)
const keyword = ref('')
const category = ref('')
const status = ref('')
const publishedFilter = ref<'' | 'true' | 'false'>('')
const selectedIds = ref<number[]>([])

const showDialog = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editWorkId = ref<number | null>(null)

const showPublishDialog = ref(false)
const publishBatchName = ref('')
const publishing = ref(false)

const form = ref({
  category: '',
  theme: '',
  creatorName: '',
  creatorPhone: '',
  creatorEmail: '',
  workName: '',
  description: '',
  imageUrl: '',
  pieceCount: null as number | null
})

const categories = ['绘画', '手作', '设计']
const statusOptions = [
  { label: '待审核', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '待齐分', value: 'PENDING_SCORES' },
  { label: '已评分', value: 'GRADED' }
]

const handoverStatusOptions = [
  { label: '未点交', value: 'NOT_STARTED', tagType: 'info' as const },
  { label: '点交中', value: 'IN_PROGRESS', tagType: 'warning' as const },
  { label: '已点交', value: 'COMPLETED', tagType: 'success' as const }
]

const getHandoverLabel = (status: string) =>
  handoverStatusOptions.find(o => o.value === status)?.label ?? '未点交'

const getHandoverTagType = (status: string) =>
  handoverStatusOptions.find(o => o.value === status)?.tagType ?? 'info'

const getWorks = async () => {
  try {
    const response = await workApi.getWorks({
      category: category.value || undefined,
      status: status.value || undefined,
      published: publishedFilter.value === '' ? undefined : publishedFilter.value === 'true',
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value
    })
    works.value = response.data.content
    total.value = response.data.totalElements
  } catch (error) {
    console.error('获取作品列表失败:', error)
    ElMessage.error('获取作品列表失败')
  }
}

const handleSearch = () => {
  page.value = 0
  getWorks()
}

const handleSelectionChange = (rows: Work[]) => {
  selectedIds.value = rows.map(w => w.id)
}

const openPublishDialog = () => {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先勾选要整批公示的作品')
    return
  }
  publishBatchName.value = ''
  showPublishDialog.value = true
}

const handlePublish = async () => {
  publishing.value = true
  try {
    const res = await publicationApi.publish({
      batchName: publishBatchName.value.trim() || undefined,
      workIds: selectedIds.value
    })
    ElMessage.success(`公示成功：批次 #${res.data.id}，共锁定 ${res.data.workCount} 件作品`)
    showPublishDialog.value = false
    selectedIds.value = []
    getWorks()
  } catch (error) {
    console.error('公示失败:', error)
    ElMessage.error(resolveErrorMessage(error, '公示失败，整批已保持未公示状态'))
  } finally {
    publishing.value = false
  }
}

const handleCreate = () => {
  dialogMode.value = 'create'
  editWorkId.value = null
  form.value = {
    category: '',
    theme: '',
    creatorName: '',
    creatorPhone: '',
    creatorEmail: '',
    workName: '',
    description: '',
    imageUrl: '',
    pieceCount: null
  }
  showDialog.value = true
}

const handleEdit = (work: any) => {
  dialogMode.value = 'edit'
  editWorkId.value = work.id
  form.value = {
    category: work.category,
    theme: work.theme || '',
    creatorName: work.creatorName,
    creatorPhone: work.creatorPhone || '',
    creatorEmail: work.creatorEmail || '',
    workName: work.workName,
    description: work.description || '',
    imageUrl: work.imageUrl || '',
    pieceCount: work.pieceCount ?? null
  }
  showDialog.value = true
}

const handleSubmit = async () => {
  try {
    if (dialogMode.value === 'create') {
      await workApi.createWork(form.value)
      ElMessage.success('作品创建成功')
    } else {
      await workApi.updateWork(editWorkId.value!, form.value)
      ElMessage.success('作品更新成功')
    }
    showDialog.value = false
    getWorks()
  } catch (error) {
    console.error('操作失败:', error)
    ElMessage.error('操作失败')
  }
}

const handleDelete = async (id: number) => {
  try {
    await workApi.deleteWork(id)
    ElMessage.success('删除成功')
    getWorks()
  } catch (error) {
    console.error('删除失败:', error)
    ElMessage.error('删除失败')
  }
}

const handleApprove = async (id: number) => {
  try {
    await workApi.approveWork(id)
    ElMessage.success('审核通过')
    getWorks()
  } catch (error) {
    console.error('审核失败:', error)
    ElMessage.error('审核失败')
  }
}

const handleView = (id: number) => {
  router.push(`/works/${id}`)
}

const getStatusLabel = (status: string) => {
  const option = statusOptions.find(o => o.value === status)
  return option ? option.label : status
}

const getStatusTagClass = (status: string) => {
  switch (status) {
    case 'PENDING': return 'bg-yellow-100 text-yellow-800'
    case 'APPROVED': return 'bg-blue-100 text-blue-800'
    case 'PENDING_SCORES': return 'bg-red-100 text-red-700'
    case 'GRADED': return 'bg-green-100 text-green-800'
    default: return 'bg-gray-100 text-gray-800'
  }
}

const getGradeTagClass = (grade: string) => {
  switch (grade) {
    case 'S': return 'bg-red-100 text-red-800 font-bold'
    case 'A': return 'bg-orange-100 text-orange-800 font-bold'
    case 'B': return 'bg-blue-100 text-blue-800 font-bold'
    case 'C': return 'bg-gray-100 text-gray-800 font-bold'
    default: return 'bg-gray-100 text-gray-800'
  }
}

onMounted(() => {
  getWorks()
})
</script>

<template>
  <div class="p-6">
    <div class="flex justify-between items-center mb-6">
      <h2 class="text-xl font-bold text-gray-800">作品管理</h2>
      <div class="flex gap-2">
        <ElButton type="warning" @click="openPublishDialog" :icon="Promotion">
          整批公示（{{ selectedIds.length }}）
        </ElButton>
        <ElButton type="primary" @click="handleCreate" :icon="Plus">
          新增作品
        </ElButton>
      </div>
    </div>

    <div class="bg-white rounded-lg shadow-sm p-4 mb-6">
      <div class="flex flex-wrap gap-4 items-center">
        <ElInput
          v-model="keyword"
          placeholder="搜索作品名称或创作者"
          :prefix-icon="Search"
          class="w-64"
          @keyup.enter="handleSearch"
        />
        <ElSelect v-model="category" placeholder="选择品类" class="w-32">
          <ElOption label="全部" value="" />
          <ElOption v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
        </ElSelect>
        <ElSelect v-model="status" placeholder="选择状态" class="w-32">
          <ElOption label="全部" value="" />
          <ElOption v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </ElSelect>
        <ElSelect v-model="publishedFilter" placeholder="公示状态" class="w-36">
          <ElOption label="全部" value="" />
          <ElOption label="已公示" value="true" />
          <ElOption label="未公示" value="false" />
        </ElSelect>
        <ElButton type="primary" @click="handleSearch">搜索</ElButton>
      </div>
    </div>

    <ElTable :data="works" stripe class="bg-white rounded-lg shadow-sm" @selection-change="handleSelectionChange">
      <ElTableColumn type="selection" width="45" :selectable="(row: Work) => !row.published && row.status === 'GRADED'" />
      <ElTableColumn prop="id" label="ID" width="60" />
      <ElTableColumn prop="workName" label="作品名称" min-width="150" />
      <ElTableColumn prop="category" label="品类" width="80">
        <template #default="{ row }">
          <span class="px-2 py-1 bg-gray-100 rounded text-sm">{{ row.category }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="creatorName" label="创作者" width="100" />
      <ElTableColumn prop="status" label="状态" width="100">
        <template #default="{ row }">
          <span :class="['px-2 py-1 rounded text-sm', getStatusTagClass(row.status)]">
            {{ getStatusLabel(row.status) }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="公示" width="130">
        <template #default="{ row }">
          <ElTag v-if="row.published" type="success" effect="dark">
            已公示 #{{ row.publicationBatchId }}
          </ElTag>
          <ElTag v-else type="info">未公示</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="现场点交" width="100">
        <template #default="{ row }">
          <ElTag :type="getHandoverTagType(row.handoverStatus)" effect="plain">
            {{ getHandoverLabel(row.handoverStatus) }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="件数" width="70">
        <template #default="{ row }">
          <span v-if="row.pieceCount != null">{{ row.pieceCount }}</span>
          <span v-else class="text-gray-400">-</span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="totalScore" label="综合得分" width="120">
        <template #default="{ row }">
          <span v-if="row.totalScore != null" class="font-medium">{{ Number(row.totalScore).toFixed(2) }}</span>
          <span v-else-if="row.status === 'PENDING_SCORES'" class="text-red-500 text-xs">待齐分不计分</span>
          <span v-else class="text-gray-400">-</span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="grade" label="等级" width="80">
        <template #default="{ row }">
          <span v-if="row.grade" :class="['px-2 py-1 rounded text-sm', getGradeTagClass(row.grade)]">
            {{ row.grade }}
          </span>
          <span v-else class="text-gray-400">-</span>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="isQualified" label="是否合格" width="110">
        <template #default="{ row }">
          <ElTag v-if="row.status === 'GRADED'" :type="row.isQualified ? 'success' : 'danger'">
            {{ row.isQualified ? '合格' : '不合格' }}
          </ElTag>
          <span v-else-if="row.status === 'PENDING_SCORES'" class="text-red-500 text-xs">
            待齐分
          </span>
          <span v-else class="text-gray-400">-</span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="200">
        <template #default="{ row }">
          <ElButton size="small" @click="handleView(row.id)" :icon="View">查看</ElButton>
          <ElButton size="small" @click="handleEdit(row)" :icon="Edit">编辑</ElButton>
          <ElButton v-if="row.status === 'PENDING'" size="small" type="success" @click="handleApprove(row.id)" :icon="Check">通过</ElButton>
          <ElButton size="small" type="danger" @click="handleDelete(row.id)" :icon="Delete">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <div class="flex justify-end mt-4">
      <ElPagination
        :current-page="page + 1"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next, jumper"
        @current-change="(val) => { page = val - 1; getWorks() }"
        @size-change="(val) => { size = val; getWorks() }"
      />
    </div>

    <ElDialog v-model="showPublishDialog" title="发起对外公示" width="520px">
      <ElAlert
        type="warning"
        :closable="false"
        show-icon
        class="mb-4"
        title="整批将按当前权重与合格线一次性锁定，提交后其中作品的综合分、等级、合格性不再随配置变更或评委改分而变化。"
      />
      <ElForm label-width="100px">
        <ElFormItem label="批次名称">
          <ElInput v-model="publishBatchName" placeholder="可选，如：2026 年 9 月第一批公示" />
        </ElFormItem>
        <ElFormItem label="本批作品数">
          <span class="font-bold text-indigo-600">{{ selectedIds.length }} 件</span>
          <span class="text-xs text-gray-400 ml-2">保存失败会整批回到未公示，不会出现半批锁定</span>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showPublishDialog = false">取消</ElButton>
        <ElButton type="warning" :loading="publishing" @click="handlePublish">确认整批公示</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="showDialog" :title="dialogMode === 'create' ? '新增作品' : '编辑作品'" width="600px">
      <ElForm :model="form" label-width="100px">
        <ElFormItem label="品类" required>
          <ElSelect v-model="form.category" placeholder="请选择品类">
            <ElOption v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="创作题材">
          <ElInput v-model="form.theme" placeholder="请输入创作题材" />
        </ElFormItem>
        <ElFormItem label="作品名称" required>
          <ElInput v-model="form.workName" placeholder="请输入作品名称" />
        </ElFormItem>
        <ElFormItem label="创作者姓名" required>
          <ElInput v-model="form.creatorName" placeholder="请输入创作者姓名" />
        </ElFormItem>
        <ElFormItem label="创作者电话">
          <ElInput v-model="form.creatorPhone" placeholder="请输入创作者电话" />
        </ElFormItem>
        <ElFormItem label="创作者邮箱">
          <ElInput v-model="form.creatorEmail" placeholder="请输入创作者邮箱" />
        </ElFormItem>
        <ElFormItem label="作品描述">
          <ElInput v-model="form.description" type="textarea" placeholder="请输入作品描述" :rows="3" />
        </ElFormItem>
        <ElFormItem label="作品图片">
          <ElInput v-model="form.imageUrl" placeholder="请输入作品图片URL" />
        </ElFormItem>
        <ElFormItem label="件数">
          <ElInputNumber v-model="form.pieceCount" :min="1" :max="9999" placeholder="实物件数" class="w-40" />
          <div class="text-xs text-gray-400 mt-1 w-full">
            点交三要素之一，完成点交前必填。已点交后改动件数将作废点交单、撤下展墙与参展凭证，需重新点交。
          </div>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showDialog = false">取消</ElButton>
        <ElButton type="primary" @click="handleSubmit">确定</ElButton>
      </template>
    </ElDialog>
  </div>
</template>
