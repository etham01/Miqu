import { get, post, put } from './request'
import type { Conversation, Message, PageResult } from './types'

export const conversationApi = {
  list: (page = 1, size = 20) => get<PageResult<Conversation>>('/conversations', { page, size }),

  /** 幂等：已存在则返回原会话。 */
  open: (targetUserId: string) => post<Conversation>('/conversations', { targetUserId }),

  /**
   * 聊天记录，按时间正序返回。
   * @param beforeId 游标：取 id 小于该值的消息（更早的）。首次加载不传。
   */
  messages: (conversationId: string, beforeId?: string, size = 20) =>
    get<Message[]>(`/conversations/${conversationId}/messages`, { beforeId, size }),

  markRead: (conversationId: string) => put<void>(`/conversations/${conversationId}/read`),
}

export const messageApi = {
  send: (receiverId: string, content: string) =>
    post<Message>('/messages', { receiverId, content }),

  unreadCount: () => get<{ total: number }>('/messages/unread-count'),
}
