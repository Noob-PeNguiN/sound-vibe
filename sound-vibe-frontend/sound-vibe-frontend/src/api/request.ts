import axios, { type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import type { Result } from '@/types/api'

/**
 * 创建 Axios 实例
 *
 * 使用 Vite Dev Server 代理，所有请求走相对路径：
 *   - /api/auth   -> http://localhost:8081/auth
 *   - /api/assets -> http://localhost:8082/assets
 *   - /api/catalog -> http://localhost:8083/catalog
 *
 * 生产环境可通过 VITE_API_BASE_URL 指向 API Gateway
 */
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8'
  }
})

/**
 * 请求拦截器
 * 自动注入 Authorization Token + X-User-Id
 */
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 注入 JWT Token
    const token = localStorage.getItem('soundvibe-token')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }

    // 注入 X-User-Id（当前阶段后端通过此 Header 识别用户）
    const userInfo = localStorage.getItem('soundvibe-user')
    if (userInfo && config.headers) {
      try {
        const user = JSON.parse(userInfo)
        if (user.userId) {
          config.headers['X-User-Id'] = String(user.userId)
        }
      } catch {
        // ignore parse error
      }
    }

    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

/**
 * 响应拦截器
 * 自动解包后端的 Result<T> 结构
 */
service.interceptors.response.use(
  (response: AxiosResponse<Result>) => {
    const res = response.data

    // 如果后端返回的 code 不是 200，视为业务错误
    if (res.code !== 200) {
      // 401 未授权：清除 token 并跳转登录
      if (res.code === 401) {
        localStorage.removeItem('soundvibe-token')
        window.location.href = '/login'
      }

      return Promise.reject(new Error(res.message || '请求失败'))
    }

    // 成功：返回 data 字段
    return res.data
  },
  (error) => {
    // HTTP 错误（网络错误、5xx 等）
    const message = error.response?.data?.message || error.message || '网络错误'
    return Promise.reject(new Error(message))
  }
)

export default service
