/**
 * Order 模块类型定义（对应后端 vibe-order）
 */

/** 购物车条目 */
export interface CartItem {
  trackId: number
  title: string
  price: number
  licenseType: 'LEASE' | 'EXCLUSIVE'
  coverUrl: string
}

/** 订单项视图 */
export interface OrderItem {
  id: number | null
  orderId: string
  trackId: number
  licenseType: string
  price: number
}

/** 订单视图 */
export interface OrderVO {
  id: string
  userId: number
  totalAmount: number
  status: number
  statusDesc: string
  items: OrderItem[]
  createTime: string
  updateTime: string
}
