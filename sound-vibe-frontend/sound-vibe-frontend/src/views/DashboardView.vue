<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'
import UploadZone from '@/components/UploadZone.vue'
import type { Asset } from '@/types/asset'

// ========== 路由 & Store ==========
const router = useRouter()
const userStore = useUserStore()

// ========== 上传相关 ==========
/** 是否显示上传面板 */
const showUploadPanel = ref(false)

// ========== 状态 ==========
const currentTime = ref(new Date())

// ========== 生命周期 ==========
onMounted(() => {
  // 每秒更新时间
  setInterval(() => {
    currentTime.value = new Date()
  }, 1000)
})

// ========== 方法 ==========

/**
 * 处理登出
 */
const handleLogout = async () => {
  if (confirm('确定要退出登录吗？')) {
    await userStore.logout()
    router.push('/login')
  }
}

/**
 * 格式化时间
 */
const formatTime = (date: Date): string => {
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

/**
 * 格式化日期
 */
const formatDate = (date: Date): string => {
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long'
  })
}

/**
 * 切换上传面板显示状态
 */
const toggleUploadPanel = () => {
  showUploadPanel.value = !showUploadPanel.value
}

/**
 * 处理上传成功回调
 */
const onAssetUploaded = (asset: Asset) => {
  console.log('[Dashboard] 收到上传结果:', asset)
  console.log('[Dashboard] 文件 URL:', asset.url)
}
</script>

<template>
  <div class="min-h-screen bg-slate-900">
    
    <!-- 顶部导航栏 -->
    <nav class="bg-slate-800 border-b border-slate-700 shadow-lg">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          
          <!-- Logo -->
          <router-link to="/" class="flex items-center space-x-3 hover:opacity-80 transition">
            <div class="h-10 w-10 bg-gradient-to-tr from-blue-500 to-purple-500 rounded-lg flex items-center justify-center shadow-lg">
              <span class="text-2xl">🎵</span>
            </div>
            <div>
              <h1 class="text-xl font-bold text-white">SoundVibe</h1>
              <p class="text-xs text-slate-400">音乐资产交易平台</p>
            </div>
          </router-link>

          <!-- 用户信息 & 退出 -->
          <div class="flex items-center space-x-4">
            <!-- 用户头像 & 名称 -->
            <div class="flex items-center space-x-3">
              <div class="h-9 w-9 bg-gradient-to-br from-purple-400 to-pink-400 rounded-full flex items-center justify-center text-white font-bold shadow-md">
                {{ userStore.userInfo?.username?.charAt(0).toUpperCase() || '?' }}
              </div>
              <div class="hidden md:block">
                <p class="text-sm font-medium text-white">{{ userStore.userInfo?.username || '用户' }}</p>
                <p class="text-xs text-slate-400">制作人</p>
              </div>
            </div>

            <!-- 退出按钮 -->
            <button
              class="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white text-sm font-medium rounded-lg transition duration-200 flex items-center space-x-2"
              @click="handleLogout"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"></path>
              </svg>
              <span>退出</span>
            </button>
          </div>
        </div>
      </div>
    </nav>

    <!-- 主内容区域 -->
    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      
      <!-- 欢迎卡片 -->
      <div class="bg-gradient-to-br from-blue-600 to-purple-600 rounded-xl shadow-2xl p-8 mb-8">
        <div class="flex flex-col md:flex-row justify-between items-center">
          <div class="mb-4 md:mb-0">
            <h2 class="text-3xl font-bold text-white mb-2">
              欢迎回来，{{ userStore.userInfo?.username || '用户' }}！🎉
            </h2>
            <p class="text-blue-100">
              {{ formatDate(currentTime) }} · {{ formatTime(currentTime) }}
            </p>
          </div>
          <div class="text-center">
            <div class="text-5xl mb-2">🎼</div>
            <p class="text-blue-100 text-sm">开始你的音乐创作之旅</p>
          </div>
        </div>
      </div>

      <!-- 功能卡片网格 -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        
        <!-- 我的项目 -->
        <div class="bg-slate-800 border border-slate-700 rounded-xl p-6 shadow-lg hover:shadow-xl transition duration-300 cursor-pointer hover:border-blue-500">
          <div class="flex items-center justify-between mb-4">
            <div class="h-12 w-12 bg-blue-500/20 rounded-lg flex items-center justify-center">
              <span class="text-3xl">📂</span>
            </div>
            <span class="text-2xl font-bold text-white">0</span>
          </div>
          <h3 class="text-lg font-semibold text-white mb-2">我的项目</h3>
          <p class="text-slate-400 text-sm">管理你的音频项目</p>
        </div>

        <!-- 协作邀请 -->
        <div class="bg-slate-800 border border-slate-700 rounded-xl p-6 shadow-lg hover:shadow-xl transition duration-300 cursor-pointer hover:border-purple-500">
          <div class="flex items-center justify-between mb-4">
            <div class="h-12 w-12 bg-purple-500/20 rounded-lg flex items-center justify-center">
              <span class="text-3xl">👥</span>
            </div>
            <span class="text-2xl font-bold text-white">0</span>
          </div>
          <h3 class="text-lg font-semibold text-white mb-2">协作邀请</h3>
          <p class="text-slate-400 text-sm">查看待处理的邀请</p>
        </div>

        <!-- 工作台（文件、作品、已购） -->
        <router-link
          to="/assets"
          class="bg-slate-800 border border-slate-700 rounded-xl p-6 shadow-lg hover:shadow-xl transition duration-300 cursor-pointer hover:border-green-500 block"
        >
          <div class="flex items-center justify-between mb-4">
            <div class="h-12 w-12 bg-green-500/20 rounded-lg flex items-center justify-center">
              <span class="text-3xl">🎛️</span>
            </div>
          </div>
          <h3 class="text-lg font-semibold text-white mb-2">工作台</h3>
          <p class="text-slate-400 text-sm">管理我的文件、已发布作品和已购作品</p>
        </router-link>

        <!-- 购物车 -->
        <router-link
          to="/cart"
          class="bg-slate-800 border border-slate-700 rounded-xl p-6 shadow-lg hover:shadow-xl transition duration-300 cursor-pointer hover:border-amber-500 block"
        >
          <div class="flex items-center justify-between mb-4">
            <div class="h-12 w-12 bg-amber-500/20 rounded-lg flex items-center justify-center">
              <span class="text-3xl">🛒</span>
            </div>
          </div>
          <h3 class="text-lg font-semibold text-white mb-2">购物车</h3>
          <p class="text-slate-400 text-sm">查看已加入购物车的作品，前往结算</p>
        </router-link>

        <!-- 我的订单 -->
        <router-link
          to="/orders"
          class="bg-slate-800 border border-slate-700 rounded-xl p-6 shadow-lg hover:shadow-xl transition duration-300 cursor-pointer hover:border-indigo-500 block"
        >
          <div class="flex items-center justify-between mb-4">
            <div class="h-12 w-12 bg-indigo-500/20 rounded-lg flex items-center justify-center">
              <span class="text-3xl">📋</span>
            </div>
          </div>
          <h3 class="text-lg font-semibold text-white mb-2">我的订单</h3>
          <p class="text-slate-400 text-sm">查看历史订单、完成支付或取消订单</p>
        </router-link>

        <!-- 设置 -->
        <div class="bg-slate-800 border border-slate-700 rounded-xl p-6 shadow-lg hover:shadow-xl transition duration-300 cursor-pointer hover:border-orange-500">
          <div class="flex items-center justify-between mb-4">
            <div class="h-12 w-12 bg-orange-500/20 rounded-lg flex items-center justify-center">
              <span class="text-3xl">⚙️</span>
            </div>
          </div>
          <h3 class="text-lg font-semibold text-white mb-2">账号设置</h3>
          <p class="text-slate-400 text-sm">管理你的账号信息</p>
        </div>

        <!-- 帮助中心 -->
        <div class="bg-slate-800 border border-slate-700 rounded-xl p-6 shadow-lg hover:shadow-xl transition duration-300 cursor-pointer hover:border-cyan-500">
          <div class="flex items-center justify-between mb-4">
            <div class="h-12 w-12 bg-cyan-500/20 rounded-lg flex items-center justify-center">
              <span class="text-3xl">❓</span>
            </div>
          </div>
          <h3 class="text-lg font-semibold text-white mb-2">帮助中心</h3>
          <p class="text-slate-400 text-sm">查看使用文档和教程</p>
        </div>

      </div>

      <!-- 快速操作区 -->
      <div class="mt-8 bg-slate-800 border border-slate-700 rounded-xl p-6 shadow-lg">
        <h3 class="text-xl font-bold text-white mb-4">快速操作</h3>
        <div class="flex flex-wrap gap-3">
          <router-link
            to="/publish"
            class="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-lg transition duration-200 flex items-center space-x-2"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
            </svg>
            <span>发布作品</span>
          </router-link>
          <button
            class="px-4 py-2 text-white rounded-lg transition duration-200 flex items-center space-x-2"
            :class="showUploadPanel ? 'bg-purple-500 ring-2 ring-purple-400/50' : 'bg-purple-600 hover:bg-purple-500'"
            @click="toggleUploadPanel"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path>
            </svg>
            <span>上传音频</span>
          </button>
          <router-link
            to="/"
            class="px-4 py-2 bg-green-600 hover:bg-green-500 text-white rounded-lg transition duration-200 flex items-center space-x-2"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
            </svg>
            <span>浏览市场</span>
          </router-link>
        </div>
      </div>

      <!-- 上传面板 -->
      <Transition
        enter-active-class="transition duration-400 ease-out"
        enter-from-class="opacity-0 -translate-y-4"
        enter-to-class="opacity-100 translate-y-0"
        leave-active-class="transition duration-300 ease-in"
        leave-from-class="opacity-100 translate-y-0"
        leave-to-class="opacity-0 -translate-y-4"
      >
        <div
          v-if="showUploadPanel"
          class="mt-8 bg-slate-800 border border-slate-700 rounded-xl p-6 shadow-lg"
        >
          <div class="flex items-center justify-between mb-5">
            <h3 class="text-xl font-bold text-white flex items-center gap-2">
              <svg class="w-6 h-6 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"></path>
              </svg>
              上传资产
            </h3>
            <button
              class="text-slate-400 hover:text-white transition duration-200"
              @click="showUploadPanel = false"
            >
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
              </svg>
            </button>
          </div>
          <UploadZone @uploaded="onAssetUploaded" />
        </div>
      </Transition>

      <!-- 页脚提示 -->
      <div class="mt-8 text-center">
        <p class="text-slate-500 text-sm">
          🎵 SoundVibe — 音乐资产交易平台
        </p>
      </div>

    </main>

  </div>
</template>
