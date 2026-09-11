import axios, { type AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResult } from './types'

export const TOKEN_KEY = 'miqu_token'
export const USER_KEY = 'miqu_user'

/**
 * 业务异常。
 *
 * 后端**始终以 HTTP 200 返回**，业务结果放在响应体的 `code` 里。
 * 因此 axios 的 catch 只能捕获网络层问题，业务错误要靠这里主动抛出。
 */
export class ApiError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 静默模式：不弹全局错误提示，由调用方自行处理 */
    silent?: boolean
  }
}

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/* ------------------------------------------------------------------
   登录失效的处理通过回调注入，而不是在这里直接 import router / store。
   原因：router → views → api → request，直接 import 会形成循环依赖，
   在打包时可能表现为"运行时才发现 undefined"的隐蔽问题。
   ------------------------------------------------------------------ */
let unauthorizedHandler: (() => void) | null = null
let handlingUnauthorized = false

/** 由 main.ts 注入：清空登录态并跳转登录页。 */
export function onUnauthorized(handler: () => void) {
  unauthorizedHandler = handler
}

function handleUnauthorized() {
  // 页面首屏可能并发发出多个请求，全部 401 时只处理一次，
  // 否则会连弹好几个提示、跳转也会重复触发
  if (handlingUnauthorized) return
  handlingUnauthorized = true
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  ElMessage.warning('登录状态已失效，请重新登录')
  unauthorizedHandler?.()
  // 留出跳转时间后复位，避免下一次会话仍被判定为"正在处理中"
  window.setTimeout(() => {
    handlingUnauthorized = false
  }, 1000)
}

http.interceptors.response.use(
  (response) => {
    const result = response.data as ApiResult
    // 非标准响应体（例如后端返回静态资源）原样透传
    if (result == null || typeof result.code !== 'number') {
      return response
    }
    if (result.code === 200) {
      return response
    }
    if (result.code === 401) {
      handleUnauthorized()
      return Promise.reject(new ApiError(result.code, result.message))
    }
    return Promise.reject(new ApiError(result.code, result.message))
  },
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      handleUnauthorized()
      return Promise.reject(new ApiError(401, '登录状态已失效'))
    }
    return Promise.reject(error)
  },
)

/**
 * 发起请求并直接返回 `data`。
 *
 * @param config.silent 为 true 时不弹全局错误提示（用于"重复关注"这类
 *        业务上可预期、调用方会自行处理的失败）
 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await http.request<ApiResult<T>>(config)
    return response.data.data
  } catch (error) {
    if (error instanceof ApiError) {
      if (!config.silent) {
        ElMessage.error(error.message)
      }
      throw error
    }
    // 网络层错误：超时、断网、后端没起来
    const axiosError = error as AxiosError
    const message =
      axiosError.code === 'ECONNABORTED'
        ? '请求超时，请稍后重试'
        : '网络异常，请检查后端服务是否已启动'
    if (!config.silent) {
      ElMessage.error(message)
    }
    throw new ApiError(-1, message)
  }
}

export const get = <T>(url: string, params?: Record<string, unknown>, silent = false) =>
  request<T>({ method: 'GET', url, params, silent })

export const post = <T>(url: string, data?: unknown, silent = false) =>
  request<T>({ method: 'POST', url, data, silent })

export const put = <T>(url: string, data?: unknown, silent = false) =>
  request<T>({ method: 'PUT', url, data, silent })

export const del = <T>(url: string, silent = false) =>
  request<T>({ method: 'DELETE', url, silent })
