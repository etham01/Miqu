import { del, get, post } from './request'
import type { Comment, LikeResult, PageResult, Post, UserBrief } from './types'

export interface CreatePostPayload {
  content?: string
  images?: string[]
}

export const postApi = {
  /** @param tab latest 最新动态（游客可访问）| following 我关注的人（需登录） */
  list: (tab: 'latest' | 'following' = 'latest', page = 1, size = 10) =>
    get<PageResult<Post>>('/posts', { tab, page, size }),

  detail: (id: string) => get<Post>(`/posts/${id}`),

  create: (payload: CreatePostPayload) => post<Post>('/posts', payload),

  remove: (id: string) => del<void>(`/posts/${id}`),

  likes: (id: string, page = 1, size = 20) =>
    get<PageResult<UserBrief>>(`/posts/${id}/likes`, { page, size }),

  /** 重复点赞返回 409，调用方自行处理，故设为静默。 */
  like: (id: string) => post<LikeResult>(`/posts/${id}/like`, undefined, true),

  /** 未点赞时取消会返回 404，同样静默。 */
  unlike: (id: string) => del<LikeResult>(`/posts/${id}/like`, true),
}

export const commentApi = {
  list: (postId: string, page = 1, size = 20) =>
    get<PageResult<Comment>>(`/posts/${postId}/comments`, { page, size }),

  create: (postId: string, content: string) =>
    post<Comment>(`/posts/${postId}/comments`, { content }),

  remove: (id: string) => del<void>(`/comments/${id}`),
}
