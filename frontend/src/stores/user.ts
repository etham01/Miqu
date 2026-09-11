import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi, type RegisterPayload } from '@/api/auth'
import { userApi, type UpdateProfilePayload } from '@/api/user'
import { TOKEN_KEY, USER_KEY } from '@/api/request'
import type { UserInfo } from '@/api/types'

function readStoredUser(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserInfo
  } catch {
    // 本地缓存被手工改坏时不要让整个应用起不来，丢掉重取即可
    localStorage.removeItem(USER_KEY)
    return null
  }
}

/**
 * 登录态。
 *
 * Token 与用户信息都持久化到 localStorage：刷新页面后不必重新登录，
 * 首屏也能立即渲染出昵称与头像，而不是先闪一下"未登录"。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) ?? '')
  const user = ref<UserInfo | null>(readStoredUser())

  const isLogin = computed(() => Boolean(token.value))
  const isAdmin = computed(() => user.value?.role === 2)
  const displayName = computed(() => user.value?.nickname || user.value?.username || '')

  function persist(newToken: string, info: UserInfo) {
    token.value = newToken
    user.value = info
    localStorage.setItem(TOKEN_KEY, newToken)
    localStorage.setItem(USER_KEY, JSON.stringify(info))
  }

  function setUser(info: UserInfo) {
    user.value = info
    localStorage.setItem(USER_KEY, JSON.stringify(info))
  }

  function clear() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  async function login(username: string, password: string) {
    const result = await authApi.login(username, password)
    persist(result.token, result.user)
    return result.user
  }

  async function register(payload: RegisterPayload) {
    return authApi.register(payload)
  }

  /** 拉取当前用户信息；失败时清空登录态（Token 可能已失效）。 */
  async function fetchMe() {
    if (!token.value) return null
    try {
      const info = await userApi.me()
      setUser(info)
      return info
    } catch {
      clear()
      return null
    }
  }

  async function updateProfile(payload: UpdateProfilePayload) {
    const info = await userApi.updateProfile(payload)
    setUser(info)
    return info
  }

  async function updateAvatar(avatar: string) {
    const info = await userApi.updateAvatar(avatar)
    setUser(info)
    return info
  }

  function logout() {
    // 无状态 JWT，服务端不维护会话，调用接口只是为了留审计日志
    authApi.logout().catch(() => undefined)
    clear()
  }

  return {
    token,
    user,
    isLogin,
    isAdmin,
    displayName,
    setUser,
    clear,
    login,
    register,
    fetchMe,
    updateProfile,
    updateAvatar,
    logout,
  }
})
