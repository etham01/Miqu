import { del, get, put } from './request'
import type {
  AdminComment,
  AdminOperationLog,
  AdminPost,
  AdminReport,
  AdminStats,
  AdminUser,
  PageResult,
} from './types'

export interface HandleReportPayload {
  /** 1 已处理（违规成立） 2 已驳回（未违规） */
  status: number
  handleRemark?: string
  /** NONE | DELETE_POST | DELETE_COMMENT | DISABLE_USER */
  action?: string
}

export const adminApi = {
  stats: () => get<AdminStats>('/admin/stats'),

  users: (params: { keyword?: string; status?: number; page?: number; size?: number } = {}) =>
    get<PageResult<AdminUser>>('/admin/users', { page: 1, size: 10, ...params }),

  userDetail: (id: string) => get<AdminUser>(`/admin/users/${id}`),

  updateUserStatus: (id: string, status: number) =>
    put<void>(`/admin/users/${id}/status`, { status }),

  posts: (params: { userId?: string; keyword?: string; page?: number; size?: number } = {}) =>
    get<PageResult<AdminPost>>('/admin/posts', { page: 1, size: 10, ...params }),

  deletePost: (id: string) => del<void>(`/admin/posts/${id}`),

  comments: (params: { postId?: string; userId?: string; keyword?: string; page?: number; size?: number } = {}) =>
    get<PageResult<AdminComment>>('/admin/comments', { page: 1, size: 10, ...params }),

  deleteComment: (id: string) => del<void>(`/admin/comments/${id}`),

  reports: (params: { status?: number; page?: number; size?: number } = {}) =>
    get<PageResult<AdminReport>>('/admin/reports', { page: 1, size: 10, ...params }),

  handleReport: (id: string, payload: HandleReportPayload) =>
    put<void>(`/admin/reports/${id}/handle`, payload),

  logs: (params: { adminId?: string; operationType?: string; page?: number; size?: number } = {}) =>
    get<PageResult<AdminOperationLog>>('/admin/logs', { page: 1, size: 10, ...params }),
}
