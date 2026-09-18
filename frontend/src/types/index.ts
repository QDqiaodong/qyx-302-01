export interface Work {
  id: number
  category: string
  theme: string
  creatorName: string
  creatorPhone: string
  creatorEmail: string
  workName: string
  description: string
  imageUrl: string
  status: string
  totalScore: number
  grade: string
  isQualified: boolean
  published: boolean
  publicationBatchId: number | null
  publishedTotalScore: number | null
  publishedGrade: string | null
  publishedIsQualified: boolean | null
  publishedAt: string | null
  /** 件数（作者申报，点交时核对；已点交后改动会作废点交并撤下展墙/凭证） */
  pieceCount: number | null
  /** NOT_STARTED 未点交 / IN_PROGRESS 点交中 / COMPLETED 已点交 */
  handoverStatus: string
  /** NOT_ISSUED 未发放 / ISSUED 已发放 / REVOKED 已撤销 */
  certificateStatus: string
  certificateNo: string | null
  certificateIssuedAt: string | null
  certificateRevokedAt: string | null
  createdAt: string
  updatedAt: string
}

/** 现场点交单：一件作品同时只有一张；未点交 = 没有点交单 */
export interface Handover {
  id: number
  workId: number
  /** IN_PROGRESS 点交中 / COMPLETED 已点交 */
  status: string
  conditionStatus: string | null
  receiver: string | null
  pieceCountSnapshot: number | null
  startedAt: string | null
  completedAt: string | null
  createdAt: string
  updatedAt: string
}

/** 点交登记内容：件数以作品档案为准，点交单只登记完好情况与接收人 */
export interface HandoverDraftRequest {
  conditionStatus?: string
  receiver?: string
}

/** 展陈看板条目：只有点交完成的作品才会出现 */
export interface ExhibitionBoardItem {
  workId: number
  workName: string
  category: string
  creatorName: string
  pieceCount: number | null
  conditionStatus: string | null
  receiver: string | null
  handoverCompletedAt: string | null
  certificateStatus: string
  certificateNo: string | null
}

export interface Score {
  id: number
  workId: number
  judgeId: number
  judgeName: string
  /** creativity / completion / commercial_potential / craftsmanship（下划线口径，与后端权重 key 一致） */
  dimension: string
  value: number
  scoredAt: string
}

/** 提交打分：一次可只交一个或几个维度，未交的维度保持缺失，绝不当 0 分 */
export interface ScoreSubmitRequest {
  workId: number
  judgeId: number
  judgeName: string
  dimensions: Partial<Record<'creativity' | 'completion' | 'commercial_potential' | 'craftsmanship', number>>
}

export interface WeightConfig {
  creativity: number
  completion: number
  commercialPotential: number
  craftsmanship: number
}

export interface ThresholdConfig {
  id: number
  qualifiedScore: number
  extremeThreshold: number
  updatedAt: string
}

export interface PublicationBatch {
  id: number
  batchName: string | null
  status: string
  creativityWeight: number
  completionWeight: number
  commercialPotentialWeight: number
  craftsmanshipWeight: number
  qualifiedScore: number
  extremeThreshold: number
  workCount: number
  publishedAt: string
  createdAt: string
}

export interface WorkListResponse {
  content: Work[]
  totalElements: number
  totalPages: number
  currentPage: number
}

export interface GradeDistribution {
  gradeCounts: Record<string, number>
  gradePercentages: Record<string, number>
  total: number
}

export interface DimensionDistribution {
  dimensionAverages: Record<string, number>
  dimensionNames: Record<string, string>
}
