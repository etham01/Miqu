import { post } from './request'
import type { LoginResult, UserInfo } from './types'

export interface RegisterPayload {
  username: string
  password: string
  nickname: string
  email: string
  gender?: number
  birthday?: string | null
  bio?: string
  avatar?: string
}

export const authApi = {
  register: (payload: RegisterPayload) => post<UserInfo>('/auth/register', payload),

  login: (username: string, password: string) =>
    post<LoginResult>('/auth/login', { username, password }),

  logout: () => post<void>('/auth/logout', undefined, true),
}
