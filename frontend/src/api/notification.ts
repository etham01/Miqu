import { get, put } from './request'
import type { Notification, NotificationUnread, PageResult } from './types'

export const notificationApi = {
  list: (params: { type?: number; isRead?: number; page?: number; size?: number } = {}) =>
    get<PageResult<Notification>>('/notifications', { page: 1, size: 20, ...params }),

  unreadCount: () => get<NotificationUnread>('/notifications/unread-count'),

  markRead: (id: string) => put<void>(`/notifications/${id}/read`),

  markAllRead: () => put<void>('/notifications/read-all'),
}
