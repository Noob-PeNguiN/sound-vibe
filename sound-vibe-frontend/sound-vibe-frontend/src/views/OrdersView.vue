<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { orderApi } from '@/api/order'
import type { OrderVO } from '@/types/order'

const orders = ref<OrderVO[]>([])
const isLoading = ref(false)
const errorMessage = ref('')
const actionLoading = ref<string | null>(null)

onMounted(() => {
  loadOrders()
})

const loadOrders = async () => {
  isLoading.value = true
  errorMessage.value = ''
  try {
    orders.value = await orderApi.getUserOrders()
  } catch (err: any) {
    errorMessage.value = err.message || '加载订单失败'
  } finally {
    isLoading.value = false
  }
}

const handlePay = async (orderId: string) => {
  if (!confirm('确认模拟支付此订单？')) return
  actionLoading.value = orderId
  try {
    await orderApi.payOrder(orderId)
    await loadOrders()
  } catch (err: any) {
    alert(err.message || '支付失败')
  } finally {
    actionLoading.value = null
  }
}

const handleCancel = async (orderId: string) => {
  if (!confirm('确定取消此订单？')) return
  actionLoading.value = orderId
  try {
    await orderApi.cancelOrder(orderId)
    await loadOrders()
  } catch (err: any) {
    alert(err.message || '取消失败')
  } finally {
    actionLoading.value = null
  }
}

const formatPrice = (price: number | null): string => {
  if (price === null || price === 0) return '免费'
  return `¥${price.toFixed(2)}`
}

const formatDateTime = (dateStr: string | null): string => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

const statusConfig: Record<number, { label: string; class: string; icon: string }> = {
  0: { label: '待支付', class: 'bg-amber-500/20 text-amber-300 border-amber-500/30', icon: '⏳' },
  1: { label: '已支付', class: 'bg-green-500/20 text-green-300 border-green-500/30', icon: '✅' },
  2: { label: '已取消', class: 'bg-slate-500/20 text-slate-400 border-slate-500/30', icon: '❌' }
}

const getStatusConfig = (status: number) => statusConfig[status] || statusConfig[2]
</script>

<template>
  <div class="min-h-screen bg-slate-900">
    <!-- 顶部导航栏 -->
    <nav class="bg-slate-800/80 backdrop-blur-xl border-b border-slate-700/50 shadow-lg sticky top-0 z-40">
      <div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between items-center h-16">
          <router-link to="/" class="flex items-center space-x-3 hover:opacity-80 transition">
            <div class="h-10 w-10 bg-gradient-to-tr from-blue-500 to-purple-500 rounded-lg flex items-center justify-center shadow-lg">
              <span class="text-2xl">🎵</span>
            </div>
            <div>
              <h1 class="text-xl font-bold text-white">SoundVibe</h1>
              <p class="text-xs text-slate-400">音乐资产交易平台</p>
            </div>
          </router-link>

          <div class="flex items-center gap-3">
            <router-link to="/" class="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white text-sm font-medium rounded-lg transition">
              浏览市场
            </router-link>
            <router-link to="/cart" class="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white text-sm font-medium rounded-lg transition">
              购物车
            </router-link>
          </div>
        </div>
      </div>
    </nav>

    <main class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <h2 class="text-2xl font-bold text-white mb-8 flex items-center gap-3">
        <svg class="w-7 h-7 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
        </svg>
        我的订单
      </h2>

      <!-- 加载中 -->
      <div v-if="isLoading" class="flex flex-col items-center justify-center py-20">
        <svg class="animate-spin h-10 w-10 text-blue-400 mb-4" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
        </svg>
        <p class="text-slate-400">正在加载订单...</p>
      </div>

      <!-- 错误 -->
      <div v-else-if="errorMessage" class="text-center py-20">
        <p class="text-red-400 mb-4">{{ errorMessage }}</p>
        <button class="px-6 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-lg transition" @click="loadOrders">
          重试
        </button>
      </div>

      <!-- 空状态 -->
      <div v-else-if="orders.length === 0" class="text-center py-20">
        <div class="text-6xl mb-4">📋</div>
        <h3 class="text-xl font-semibold text-white mb-2">暂无订单</h3>
        <p class="text-slate-400 mb-6">去市场挑选你喜欢的 Beat 加入购物车吧!</p>
        <router-link
          to="/"
          class="inline-block px-6 py-3 bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 text-white font-medium rounded-lg transition shadow-lg"
        >
          浏览市场
        </router-link>
      </div>

      <!-- 订单列表 -->
      <div v-else class="space-y-6">
        <div
          v-for="order in orders"
          :key="order.id"
          class="bg-slate-800 border rounded-xl overflow-hidden"
          :class="order.status === 0 ? 'border-amber-500/30' : 'border-slate-700'"
        >
          <!-- 订单头部 -->
          <div class="flex items-center justify-between px-6 py-4 border-b border-slate-700/50 bg-slate-800/50">
            <div class="flex items-center gap-4">
              <span
                class="px-3 py-1 rounded-full text-xs font-bold border"
                :class="getStatusConfig(order.status).class"
              >
                {{ getStatusConfig(order.status).icon }} {{ getStatusConfig(order.status).label }}
              </span>
              <span class="text-slate-500 text-sm font-mono">{{ order.id }}</span>
            </div>
            <span class="text-slate-500 text-sm">{{ formatDateTime(order.createTime) }}</span>
          </div>

          <!-- 订单项 -->
          <div class="px-6 py-4 space-y-3">
            <div
              v-for="item in order.items"
              :key="item.trackId"
              class="flex items-center justify-between"
            >
              <div class="flex items-center gap-3">
                <div class="w-8 h-8 rounded bg-slate-700 flex items-center justify-center">
                  <span class="text-sm">🎵</span>
                </div>
                <div>
                  <span class="text-white text-sm">Track #{{ item.trackId }}</span>
                  <span
                    class="ml-2 px-1.5 py-0.5 rounded text-[11px] font-medium"
                    :class="item.licenseType === 'EXCLUSIVE' ? 'bg-amber-500/20 text-amber-300' : 'bg-blue-500/20 text-blue-300'"
                  >
                    {{ item.licenseType === 'EXCLUSIVE' ? '独占' : '租赁' }}
                  </span>
                </div>
              </div>
              <span class="text-white font-medium">{{ formatPrice(item.price) }}</span>
            </div>
          </div>

          <!-- 订单底部 -->
          <div class="flex items-center justify-between px-6 py-4 border-t border-slate-700/50 bg-slate-900/30">
            <div>
              <span class="text-slate-400 text-sm">合计: </span>
              <span class="text-white font-bold text-lg">{{ formatPrice(order.totalAmount) }}</span>
            </div>

            <div v-if="order.status === 0" class="flex items-center gap-3">
              <button
                class="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white text-sm rounded-lg transition disabled:opacity-50"
                :disabled="actionLoading === order.id"
                @click="handleCancel(order.id)"
              >
                取消订单
              </button>
              <button
                class="px-5 py-2 bg-gradient-to-r from-green-600 to-emerald-600 hover:from-green-500 hover:to-emerald-500 text-white text-sm font-medium rounded-lg transition shadow-lg shadow-green-600/20 disabled:opacity-50 flex items-center gap-2"
                :disabled="actionLoading === order.id"
                @click="handlePay(order.id)"
              >
                <svg v-if="actionLoading === order.id" class="animate-spin w-4 h-4" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                </svg>
                模拟支付
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 页脚 -->
      <div class="mt-12 pb-8 text-center">
        <p class="text-slate-600 text-sm">🎵 SoundVibe — 音乐资产交易平台</p>
      </div>
    </main>
  </div>
</template>
