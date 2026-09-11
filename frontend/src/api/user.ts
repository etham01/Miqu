import { del, get, post, put } from './request'
import type {
  FollowResult,
  PageResult,
  Post,
  UserFollow,
  UserInfo,
  UserProfile,
  UserSearchItem,
} from './types'

export interface UpdateProfilePayload {
  nickname: string
  gender?: number
  birthday?: string | null
  bio?: string
}

export const userApi = {
  me: () => get<UserInfo>('/users/me'),

  updateProfile: (payload: UpdateProfilePayload) => put<UserInfo>('/users/me', payload),

  changePassword: (oldPassword: string, newPassword: string) =>
    put<void>('/users/me/password', { oldPassword, newPassword }),

  updateAvatar: (avatar: string) => put<UserInfo>('/users/me/avatar', { avatar }),

  /** 他人主页。游客也能访问，此时关注状态字段恒为 false。 */
  profile: (id: string) => get<UserProfile>(`/users/${id}`),

  posts: (id: string, page = 1, size = 10) =>
    get<PageResult<Post>>(`/users/${id}/posts`, { page, size }),

  following: (id: string, page = 1, size = 10) =>
    get<PageResult<UserFollow>>(`/users/${id}/following`, { page, size }),

  followers: (id: string, page = 1, size = 10) =>
    get<PageResult<UserFollow>>(`/users/${id}/followers`, { page, size }),

  search: (keyword: string, page = 1, size = 10) =>
    get<PageResult<UserSearchItem>>('/users/search', { keyword, page, size }),

  follow: (id: string) => post<FollowResult>(`/users/${id}/follow`, undefined, true),

  /** 取消关注未关注的对象会返回 404，调用方据此提示，故设为静默。 */
  unfollow: (id: string) => del<FollowResult>(`/users/${id}/follow`, true),
}
