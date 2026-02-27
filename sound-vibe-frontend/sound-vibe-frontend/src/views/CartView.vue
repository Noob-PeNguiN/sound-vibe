<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '@/stores/cart'
import { orderApi } from '@/api/order'
import { getAssetFileUrl } from '@/api/catalog'

const router = useRouter()
const cartStore = useCartStore()

const isCheckingOut = ref(false)
const checkoutError = ref('')
const checkoutSuccess = ref(false)
const createdOrderId = ref('')

onMounted(() => {
  cartStore.fetchCart()
})

const handleRemoveItem = async (trackId: number) => {
  try {
    await cartStore.removeItem(trackId)
  } catch (err: any) {
    alert(err.message || '移除失败')
  }
}

const handleClearCart = async () => {
  if (!confirm('确定清空购物车？')) return
  try {
    await cartStore.clearCart()
  } catch (err: any) {
    alert(err.message || '清空失败')
  }
}

const handleCheckout = async () => {
  if (cartStore.itemCount === 0) return
  isCheckingOut.value = true
  checkoutError.value = ''

  try {
    const order = await orderApi.checkout()
    createdOrderId.value = order.id
    checkoutSuccess.value = true
    cartStore.clearLocal()
  } catch (err: any) {
    checkoutError.value = err.message || '下单失败，请稍后重试'
  } finally {
    isCheckingOut.value = false
  }
}

const goToOrders = () => {
  router.push('/orders')
}

const goToMarket = () => {
  router.push('/')
}

const formatPrice = (price: number | null): string => {
  if (price === null || price === 0) return '免费'
  return `¥${price.toFixed(2)}`
}

const getLicenseLabel = (type: string) => {
  return type === 'EXCLUSIVE' ? '独占授权' : '租赁授权'
}

const getLicenseBadgeClass = (type: string) => {
  return type === 'EXCLUSIVE'
    ? 'bg-amber-500/20 text-amber-300 border-amber-500/30'
    : 'bg-blue-500/20 text-blue-300 border-blue-500/30'
}
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
              继续浏览
            </router-link>
            <router-link to="/orders" class="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white text-sm font-medium rounded-lg transition">
              我的订单
            </router-link>
          </div>
        </div>
      </div>
    </nav>

    <main class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <!-- 页面标题 -->
      <div class="flex items-center justify-between mb-8">
        <h2 class="text-2xl font-bold text-white flex items-center gap-3">
          <svg class="w-7 h-7 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 100 4 2 2 0 000-4z" />
          </svg>
          购物车
          <span v-if="cartStore.itemCount > 0" class="text-base font-normal text-slate-400">
            ({{ cartStore.itemCount }} 件)
          </span>
        </h2>
        <button
          v-if="cartStore.itemCount > 0"
          class="text-sm text-slate-500 hover:text-red-400 transition"
          @click="handleClearCart"
        >
          清空购物车
        </button>
      </div>

      <!-- 下单成功 -->
      <div v-if="checkoutSuccess" class="text-center py-20">
        <div class="w-20 h-20 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-6">
          <svg class="w-10 h-10 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
        </div>
        <h3 class="text-2xl font-bold text-white mb-3">订单创建成功!</h3>
        <p class="text-slate-400 mb-2">订单号: <span class="text-white font-mono">{{ createdOrderId }}</span></p>
        <p class="text-slate-500 text-sm mb-8">请在 15 分钟内完成支付，否则订单将自动取消</p>
        <div class="flex items-center justify-center gap-4">
          <button
            class="px-6 py-3 bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 text-white font-medium rounded-lg transition shadow-lg"
            @click="goToOrders"
          >
            查看我的订单
          </button>
          <button
            class="px-6 py-3 bg-slate-700 hover:bg-slate-600 text-white font-medium rounded-lg transition"
            @click="goToMarket"
          >
            继续浏览
          </button>
        </div>
      </div>

      <!-- 加载中 -->
      <div v-else-if="cartStore.isLoading" class="flex flex-col items-center justify-center py-20">
        <svg class="animate-spin h-10 w-10 text-blue-400 mb-4" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
        </svg>
        <p class="text-slate-400">正在加载购物车...</p>
      </div>

      <!-- 空购物车 -->
      <div v-else-if="cartStore.itemCount === 0" class="text-center py-20">
        <div class="text-6xl mb-4">🛒</div>
        <h3 class="text-xl font-semibold text-white mb-2">购物车是空的</h3>
        <p class="text-slate-400 mb-6">去市场挑选你喜欢的 Beat 吧!</p>
        <button
          class="px-6 py-3 bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 text-white font-medium rounded-lg transition shadow-lg"
          @click="goToMarket"
        >
          浏览市场
        </button>
      </div>

      <!-- 购物车列表 -->
      <div v-else class="space-y-4">
        <div
          v-for="item in cartStore.items"
          :key="item.trackId"
          class="bg-slate-800 border border-slate-700 rounded-xl p-4 flex items-center gap-4 hover:border-slate-600 transition"
        >
          <!-- 封面 -->
          <div class="w-16 h-16 rounded-lg overflow-hidden bg-slate-700 shrink-0">
            <img
              v-if="item.coverUrl"
              :src="item.coverUrl"
              :alt="item.title"
              class="w-full h-full object-cover"
            />
            <div v-else class="w-full h-full bg-gradient-to-br from-blue-600 to-purple-600 flex items-center justify-center">
              <span class="text-2xl opacity-40">🎵</span>
            </div>
          </div>

          <!-- 信息 -->
          <div class="flex-1 min-w-0">
            <h4 class="text-white font-semibold truncate">{{ item.title }}</h4>
            <div class="flex items-center gap-2 mt-1">
              <span
                class="px-2 py-0.5 rounded-full text-xs font-medium border"
                :class="getLicenseBadgeClass(item.licenseType)"
              >
                {{ getLicenseLabel(item.licenseType) }}
              </span>
            </div>
          </div>

          <!-- 价格 -->
          <div class="text-right shrink-0">
            <span class="text-white font-bold text-lg">{{ formatPrice(item.price) }}</span>
          </div>

          <!-- 删除 -->
          <button
            class="w-9 h-9 rounded-lg flex items-center justify-center text-slate-500 hover:text-red-400 hover:bg-red-500/10 transition shrink-0"
            title="移除"
            @click="handleRemoveItem(item.trackId)"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
          </button>
        </div>

        <!-- 结算区域 -->
        <div class="mt-8 bg-slate-800 border border-slate-700 rounded-xl p-6">
          <div class="flex items-center justify-between mb-4">
            <span class="text-slate-400">共 {{ cartStore.itemCount }} 件商品</span>
            <div class="text-right">
              <span class="text-slate-400 text-sm mr-2">合计:</span>
              <span class="text-2xl font-bold text-white">¥{{ cartStore.totalAmount.toFixed(2) }}</span>
            </div>
          </div>

          <div v-if="checkoutError" class="mb-4 px-4 py-3 bg-red-500/10 border border-red-500/30 rounded-lg text-red-400 text-sm">
            {{ checkoutError }}
          </div>

          <button
            class="w-full py-4 bg-gradient-to-r from-green-600 to-emerald-600 hover:from-green-500 hover:to-emerald-500 text-white font-bold text-lg rounded-xl transition shadow-lg shadow-green-600/20 flex items-center justify-center gap-2 disabled:opacity-60 disabled:cursor-not-allowed"
            :disabled="isCheckingOut"
            @click="handleCheckout"
          >
            <svg v-if="isCheckingOut" class="animate-spin w-5 h-5" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
            </svg>
            {{ isCheckingOut ? '正在创建订单...' : '结算下单' }}
          </button>

          <p class="text-xs text-slate-500 text-center mt-3">
            下单后需在 15 分钟内完成支付，超时订单将自动取消
          </p>
        </div>
      </div>

      <!-- 页脚 -->
      <div class="mt-12 pb-8 text-center">
        <p class="text-slate-600 text-sm">🎵 SoundVibe — 音乐资产交易平台</p>
      </div>
    </main>
  </div>
</template>
