import request from './request'

/**
 * 登录请求参数
 */
export interface LoginParams {
  username: string
  password: string
}

/**
 * 登录响应数据
 */
export interface LoginResult {
  token: string
  userId: number
  username: string
  email?: string
}

/**
 * 注册请求参数
 */
export interface RegisterParams {
  username: string
  password: string
}

/**
 * 认证 API 模块
 * 对应后端 vibe-auth 微服务（端口 8081）
 * 通过 Vite 代理: /api/auth -> http://localhost:8081/auth
 */
export const authApi = {
  /**
   * 用户注册
   */
  register(params: RegisterParams): Promise<void> {
    return request({
      url: '/api/auth/register',
      method: 'POST',
      data: params
    })
  },

  /**
   * 用户登录
   */
  login(params: LoginParams): Promise<LoginResult> {
    return request({
      url: '/api/auth/login',
      method: 'POST',
      data: params
    })
  },

  /**
   * 用户登出
   */
  logout(): Promise<void> {
    return request({
      url: '/api/auth/logout',
      method: 'POST'
    })
  }
}
