<script setup lang="ts">
import { ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'

// ========== 路由 & Store ==========
const router = useRouter()
const userStore = useUserStore()

// ========== 表单状态 ==========
const username = ref('')
const password = ref('')

// ========== UI 状态 ==========
const isLoading = ref(false)
const errorMessage = ref('')

// ========== 方法 ==========

/**
 * 处理登录提交
 */
const handleLogin = async () => {
  // 清空之前的错误信息
  errorMessage.value = ''
  
  // 表单验证
  if (!username.value.trim()) {
    errorMessage.value = '请输入用户名'
    return
  }
  if (!password.value.trim()) {
    errorMessage.value = '请输入密码'
    return
  }
  
  // 开始加载
  isLoading.value = true
  
  try {
    // 调用 Store 的 login 方法
    await userStore.login({
      username: username.value.trim(),
      password: password.value
    })
    
    // 登录成功，跳转到主页
    router.push('/')
  } catch (error) {
    // 登录失败：显示错误信息
    errorMessage.value = error instanceof Error ? error.message : '登录失败，请稍后重试'
  } finally {
    // 结束加载
    isLoading.value = false
  }
}

/**
 * 跳转到注册页
 */
const goToRegister = () => {
  router.push('/register')
}

/**
 * 按 Enter 键提交
 */
const handleKeyPress = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !isLoading.value) {
    handleLogin()
  }
}
</script>

<template>
  <div class="h-screen w-screen bg-slate-900 flex items-center justify-center">
    
    <!-- 登录卡片 -->
    <div class="bg-slate-800 p-8 rounded-xl shadow-2xl border border-slate-700 w-full max-w-md">
      
      <!-- Logo & 标题 -->
      <div class="mb-6 flex justify-center">
        <div class="h-16 w-16 bg-gradient-to-tr from-blue-500 to-purple-500 rounded-lg flex items-center justify-center shadow-lg">
          <span class="text-3xl">🎵</span>
        </div>
      </div>

      <h1 class="text-3xl font-bold text-white text-center mb-2">SoundVibe</h1>
      <p class="text-slate-400 text-center mb-8">Audio Collaboration Platform</p>

      <!-- 错误提示 -->
      <div 
        v-if="errorMessage" 
        class="mb-6 p-4 bg-red-500/10 border border-red-500/50 rounded-lg"
      >
        <p class="text-red-400 text-sm">{{ errorMessage }}</p>
      </div>

      <!-- 登录表单 -->
      <div class="space-y-4">
        <!-- 用户名 -->
        <div>
          <label for="username" class="block text-sm font-medium text-slate-300 mb-2">
            用户名
          </label>
          <input
            id="username"
            v-model="username"
            type="text"
            placeholder="请输入用户名"
            class="w-full px-4 py-3 bg-slate-700 border border-slate-600 rounded-lg text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition duration-200"
            :disabled="isLoading"
            @keypress="handleKeyPress"
          />
        </div>

        <!-- 密码 -->
        <div>
          <label for="password" class="block text-sm font-medium text-slate-300 mb-2">
            密码
          </label>
          <input
            id="password"
            v-model="password"
            type="password"
            placeholder="请输入密码"
            class="w-full px-4 py-3 bg-slate-700 border border-slate-600 rounded-lg text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition duration-200"
            :disabled="isLoading"
            @keypress="handleKeyPress"
          />
        </div>

        <!-- 登录按钮 -->
        <button
          :disabled="isLoading"
          class="w-full bg-blue-600 hover:bg-blue-500 text-white font-semibold py-3 px-4 rounded-lg transition duration-200 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center"
          @click="handleLogin"
        >
          <span v-if="!isLoading">登录</span>
          <span v-else class="flex items-center">
            <svg class="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            登录中...
          </span>
        </button>
      </div>

      <!-- 还没有账号 -->
      <div class="mt-6 text-center">
        <p class="text-slate-400 text-sm">
          还没有账号？
          <button
            class="text-blue-400 hover:text-blue-300 font-medium transition duration-200"
            @click="goToRegister"
          >
            立即注册
          </button>
        </p>
      </div>

      <!-- 页脚 -->
      <p class="mt-4 text-xs text-slate-500 text-center">
        Powered by Vue 3 + Tailwind CSS
      </p>
    </div>

  </div>
</template>
