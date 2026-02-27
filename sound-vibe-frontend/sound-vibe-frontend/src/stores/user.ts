import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { authApi, type LoginParams, type LoginResult, type RegisterParams } from '@/api/auth'

/**
 * 用户信息接口
 */
export interface UserInfo {
  userId: number
  username: string
  email?: string
}

/**
 * localStorage 键名常量
 */
const STORAGE_KEY_TOKEN = 'soundvibe-token'
const STORAGE_KEY_USER = 'soundvibe-user'

/**
 * 从 localStorage 恢复用户信息
 */
function loadUserInfo(): UserInfo | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY_USER)
    return raw ? JSON.parse(raw) : null
  } catch {
    localStorage.removeItem(STORAGE_KEY_USER)
    return null
  }
}

/**
 * 用户状态管理 Store
 * 使用 Setup Store 语法
 */
export const useUserStore = defineStore('user', () => {
  // ========== State ==========
  
  /**
   * JWT Token
   * 初始化时从 localStorage 读取
   */
  const token = ref<string | null>(localStorage.getItem(STORAGE_KEY_TOKEN))
  
  /**
   * 用户信息
   * 初始化时从 localStorage 读取（解决刷新后丢失的问题）
   */
  const userInfo = ref<UserInfo | null>(loadUserInfo())

  // ========== Getters ==========
  
  /**
   * 是否已登录
   */
  const isLoggedIn = computed(() => !!token.value)

  // ========== Actions ==========
  
  /**
   * 用户注册
   * @param params 注册参数 (username, password)
   */
  const register = async (params: RegisterParams): Promise<void> => {
    try {
      await authApi.register(params)
    } catch (error) {
      throw error
    }
  }
  
  /**
   * 用户登录
   * @param params 登录参数 (username, password)
   * @returns 登录结果
   */
  const login = async (params: LoginParams): Promise<LoginResult> => {
    try {
      const res = await authApi.login(params)
      
      // 保存 token 到状态和 localStorage
      token.value = res.token
      localStorage.setItem(STORAGE_KEY_TOKEN, res.token)
      
      // 保存用户信息到状态和 localStorage（解决刷新后丢失 + X-User-Id 注入）
      userInfo.value = {
        userId: res.userId,
        username: res.username,
        email: res.email
      }
      localStorage.setItem(STORAGE_KEY_USER, JSON.stringify(userInfo.value))
      
      return res
    } catch (error) {
      // 登录失败：清空状态
      token.value = null
      userInfo.value = null
      localStorage.removeItem(STORAGE_KEY_TOKEN)
      localStorage.removeItem(STORAGE_KEY_USER)
      throw error
    }
  }
  
  /**
   * 用户登出
   */
  const logout = async (): Promise<void> => {
    try {
      await authApi.logout()
    } catch (error) {
      // 即使后端登出失败，也清除本地状态
      console.error('登出失败:', error)
    } finally {
      // 清空状态和 localStorage
      token.value = null
      userInfo.value = null
      localStorage.removeItem(STORAGE_KEY_TOKEN)
      localStorage.removeItem(STORAGE_KEY_USER)
    }
  }

  return {
    // State
    token,
    userInfo,
    
    // Getters
    isLoggedIn,
    
    // Actions
    register,
    login,
    logout
  }
})
