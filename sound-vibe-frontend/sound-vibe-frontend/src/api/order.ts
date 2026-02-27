import request from './request'
import type { CartItem, OrderVO } from '@/types/order'

/**
 * Order（订单 / 购物车）API 模块
 * 对应后端 vibe-order 微服务（端口 8085）
 * 通过 Vite 代理: /api/order -> http://localhost:8085/order
 */
export const orderApi = {
  // ==================== 购物车 ====================

  addCartItem(item: CartItem): Promise<void> {
    return request({
      url: '/api/order/cart',
      method: 'POST',
      data: item
    }) as Promise<void>
  },

  removeCartItem(trackId: number): Promise<void> {
    return request({
      url: `/api/order/cart/${trackId}`,
      method: 'DELETE'
    }) as Promise<void>
  },

  getCart(): Promise<CartItem[]> {
    return request({
      url: '/api/order/cart',
      method: 'GET'
    }) as Promise<CartItem[]>
  },

  clearCart(): Promise<void> {
    return request({
      url: '/api/order/cart',
      method: 'DELETE'
    }) as Promise<void>
  },

  // ==================== 订单 ====================

  checkout(): Promise<OrderVO> {
    return request({
      url: '/api/order/checkout',
      method: 'POST'
    }) as Promise<OrderVO>
  },

  getOrderDetail(orderId: string): Promise<OrderVO> {
    return request({
      url: `/api/order/${orderId}`,
      method: 'GET'
    }) as Promise<OrderVO>
  },

  getUserOrders(): Promise<OrderVO[]> {
    return request({
      url: '/api/order/list',
      method: 'GET'
    }) as Promise<OrderVO[]>
  },

  payOrder(orderId: string): Promise<void> {
    return request({
      url: `/api/order/${orderId}/pay`,
      method: 'POST'
    }) as Promise<void>
  },

  cancelOrder(orderId: string): Promise<void> {
    return request({
      url: `/api/order/${orderId}/cancel`,
      method: 'POST'
    }) as Promise<void>
  }
}
