import { post } from './request'

export interface CreateReportPayload {
  /** 1 用户 2 动态 3 评论 */
  targetType: number
  targetId: string
  /** 1 垃圾广告 2 辱骂骚扰 3 色情低俗 4 违法违规 5 其他 */
  reasonType: number
  reasonDetail?: string
}

export const reportApi = {
  create: (payload: CreateReportPayload) =>
    post<{ id: string; targetType: number; targetId: string }>('/reports', payload),
}
