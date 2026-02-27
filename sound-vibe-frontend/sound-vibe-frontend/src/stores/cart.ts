import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { orderApi } from '@/api/order'
import type { CartItem } from '@/types/order'

/**
 * 购物车状态管理 Store
 * 数据源：Redis（通过 vibe-order API 实时同步）
 */
export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItem[]>([])
  const isLoading = ref(false)

  const itemCount = computed(() => items.value.length)

  const totalAmount = computed(() =>
    items.value.reduce((sum, item) => sum + (item.price || 0), 0)
  )

  const isInCart = (trackId: number) =>
    items.value.some(item => item.trackId === trackId)

  const fetchCart = async () => {
    isLoading.value = true
    try {
      items.value = await orderApi.getCart()
    } catch (err) {
      console.error('[CartStore] 获取购物车失败:', err)
    } finally {
      isLoading.value = false
    }
  }

  const addItem = async (item: CartItem) => {
    await orderApi.addCartItem(item)
    // 本地乐观更新
    const idx = items.value.findIndex(i => i.trackId === item.trackId)
    if (idx >= 0) {
      items.value[idx] = item
    } else {
      items.value.push(item)
    }
  }

  const removeItem = async (trackId: number) => {
    await orderApi.removeCartItem(trackId)
    items.value = items.value.filter(i => i.trackId !== trackId)
  }

  const clearCart = async () => {
    await orderApi.clearCart()
    items.value = []
  }

  /** 结算后清空本地状态 */
  const clearLocal = () => {
    items.value = []
  }

  return {
    items,
    isLoading,
    itemCount,
    totalAmount,
    isInCart,
    fetchCart,
    addItem,
    removeItem,
    clearCart,
    clearLocal
  }
})
