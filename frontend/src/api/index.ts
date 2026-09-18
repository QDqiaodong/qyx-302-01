import axios from 'axios'
import type { Work, Score, ScoreSubmitRequest, WeightConfig, ThresholdConfig, WorkListResponse, GradeDistribution, DimensionDistribution, PublicationBatch, Handover, HandoverDraftRequest, ExhibitionBoardItem } from '@/types'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

/**
 * 提取后端业务错误信息（GlobalExceptionHandler 返回 { message }），
 * 让"已公示作品不能改分"这类拒绝原因能直接展示给评委/秘书。
 */
export function resolveErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error)) {
    const message = error.response?.data?.message
    if (typeof message === 'string' && message.trim()) {
      return message
    }
  }
  return fallback
}

export const workApi = {
  getWorks: (params: { category?: string; status?: string; published?: boolean; keyword?: string; page?: number; size?: number }) =>
    api.get<WorkListResponse>('/works', { params }),

  getWorkById: (id: number) =>
    api.get<Work>(`/works/${id}`),

  createWork: (data: Partial<Work>) =>
    api.post<Work>('/works', data),

  updateWork: (id: number, data: Partial<Work>) =>
    api.put<Work>(`/works/${id}`, data),

  deleteWork: (id: number) =>
    api.delete(`/works/${id}`),

  approveWork: (id: number) =>
    api.post<Work>(`/works/${id}/approve`)
}

export const scoreApi = {
  getScoresByWorkId: (workId: number) =>
    api.get<Score[]>(`/scores/work/${workId}`),

  submitScore: (data: ScoreSubmitRequest) =>
    api.post<Score[]>('/scores', data),

  updateScore: (id: number, dimension: string, value: number) =>
    api.put<Score>(`/scores/${id}`, { dimensions: { [dimension]: value } }),

  deleteScore: (id: number) =>
    api.delete(`/scores/${id}`)
}

export const statisticsApi = {
  getGradeDistribution: () =>
    api.get<GradeDistribution>('/statistics/grade-distribution'),

  getDimensionDistribution: () =>
    api.get<DimensionDistribution>('/statistics/dimension-distribution'),

  getFailedWorks: () =>
    api.get<Work[]>('/statistics/failed-works'),

  getWorkStatistics: (workId: number) =>
    api.get(`/statistics/work/${workId}`)
}

export const configApi = {
  getWeights: () =>
    api.get<WeightConfig>('/config/weights'),

  updateWeights: (data: WeightConfig) =>
    api.put<WeightConfig>('/config/weights', data),

  getThreshold: () =>
    api.get<ThresholdConfig>('/config/threshold'),

  updateThreshold: (data: { qualifiedScore: number; extremeThreshold: number }) =>
    api.put<ThresholdConfig>('/config/threshold', data)
}

export const publicationApi = {
  publish: (data: { batchName?: string; workIds?: number[] }) =>
    api.post<PublicationBatch>('/publications', data),

  listBatches: () =>
    api.get<PublicationBatch[]>('/publications'),

  listBatchWorks: (batchId: number) =>
    api.get<Work[]>(`/publications/${batchId}/works`),

  revoke: (batchId: number) =>
    api.post<PublicationBatch>(`/publications/${batchId}/revoke`)
}

/**
 * 现场点交：未点交 → 点交中 → 已点交。
 * 完成点交要三要素齐全（件数、完好情况、接收人）；失败整单作废退回未点交。
 */
export const handoverApi = {
  /** 当前点交单；未点交时后端返回 404，调用方按 null 处理 */
  getCurrent: (workId: number) =>
    api.get<Handover>(`/works/${workId}/handover`),

  start: (workId: number) =>
    api.post<Handover>(`/works/${workId}/handover/start`),

  updateDraft: (workId: number, data: HandoverDraftRequest) =>
    api.put<Handover>(`/works/${workId}/handover`, data),

  complete: (workId: number, data: HandoverDraftRequest = {}) =>
    api.post<Handover>(`/works/${workId}/handover/complete`, data),

  fail: (workId: number) =>
    api.post<Work>(`/works/${workId}/handover/fail`)
}

export const exhibitionApi = {
  /** 展陈看板：后端只返回已点交的作品，未点交/点交中的一律不出现 */
  getBoard: () =>
    api.get<ExhibitionBoardItem[]>('/exhibition/board'),

  /** 发放参展凭证：点交未完成会被后端 409 拒绝 */
  issueCertificate: (workId: number) =>
    api.post<Work>(`/exhibition/works/${workId}/certificate`)
}
