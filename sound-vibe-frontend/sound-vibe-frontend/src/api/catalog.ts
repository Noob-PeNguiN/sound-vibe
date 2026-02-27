import request from './request'
import type { Track, TrackPublishForm, TrackUpdateForm, TrackQuery, TrackPage, PurchaseItem, PurchasePage } from '@/types/catalog'

/**
 * Catalog（作品目录 / 交易市场）API 模块
 * 对应后端 vibe-catalog 微服务（端口 8083）
 * 通过 Vite 代理: /api/catalog -> http://localhost:8083/catalog
 *
 * 支持两种作品类型：
 * - SINGLE（单曲）：包含 1 个文件
 * - PACK（合集/采样包）：包含多个文件
 */
export const catalogApi = {
  /**
   * 发布新作品（支持 SINGLE 和 PACK）
   * POST /api/catalog/tracks
   */
  publishTrack(data: TrackPublishForm): Promise<Track> {
    return request({
      url: '/api/catalog/tracks',
      method: 'POST',
      data,
      timeout: 60000  // 发布作品涉及跨服务元数据填充，适当放宽超时
    }) as Promise<Track>
  },

  /**
   * 更新作品信息（部分更新，支持 SINGLE 和 PACK）
   * PUT /api/catalog/tracks/{id}
   */
  updateTrack(id: number, data: TrackUpdateForm): Promise<Track> {
    return request({
      url: `/api/catalog/tracks/${id}`,
      method: 'PUT',
      data
    }) as Promise<Track>
  },

  /**
   * 获取作品详情（含 PACK 文件列表）
   * GET /api/catalog/tracks/{id}
   */
  getTrackDetail(id: number): Promise<Track> {
    return request({
      url: `/api/catalog/tracks/${id}`,
      method: 'GET'
    }) as Promise<Track>
  },

  /**
   * 切换作品状态（私密 ↔ 公开）
   * PUT /api/catalog/tracks/{id}/toggle-status
   */
  toggleTrackStatus(id: number): Promise<Track> {
    return request({
      url: `/api/catalog/tracks/${id}/toggle-status`,
      method: 'PUT'
    }) as Promise<Track>
  },

  /**
   * 设置作品可见范围
   * PUT /api/catalog/tracks/{id}/visibility
   */
  setVisibility(id: number, visibility: number): Promise<Track> {
    return request({
      url: `/api/catalog/tracks/${id}/visibility`,
      method: 'PUT',
      data: { visibility }
    }) as Promise<Track>
  },

  /**
   * 删除作品（逻辑删除）
   * DELETE /api/catalog/tracks/{id}
   */
  deleteTrack(id: number): Promise<void> {
    return request({
      url: `/api/catalog/tracks/${id}`,
      method: 'DELETE'
    }).catch(async (err: any) => {
      // 某些网关/代理链路可能拦截 DELETE，这里回退到 POST 兼容接口
      const status = err?.response?.status
      if (status === 405) {
        await request({
          url: `/api/catalog/tracks/${id}/delete`,
          method: 'POST'
        })
        return
      }
      throw err
    }) as Promise<void>
  },

  /**
   * 分页查询作品列表（市场主页）
   * GET /api/catalog/tracks?keyword=xxx&tag=trap&fileType=AUDIO&trackType=PACK&status=1&visibility=2&current=1&size=20
   */
  listTracks(query?: TrackQuery): Promise<TrackPage> {
    return request({
      url: '/api/catalog/tracks',
      method: 'GET',
      params: {
        keyword: query?.keyword,
        tag: query?.tag,
        fileType: query?.fileType,
        trackType: query?.trackType,
        status: query?.status,
        visibility: query?.visibility,
        producerId: query?.producerId,
        current: query?.current ?? 1,
        size: query?.size ?? 20
      }
    }) as Promise<TrackPage>
  },

  // ==================== 购买相关 ====================

  /**
   * 购买作品（模拟支付）
   * POST /api/catalog/purchases/{trackId}
   */
  purchaseTrack(trackId: number): Promise<PurchaseItem> {
    return request({
      url: `/api/catalog/purchases/${trackId}`,
      method: 'POST'
    }) as Promise<PurchaseItem>
  },

  /**
   * 查询我的已购作品列表
   * GET /api/catalog/purchases?current=1&size=20
   */
  listMyPurchases(current = 1, size = 20): Promise<PurchasePage> {
    return request({
      url: '/api/catalog/purchases',
      method: 'GET',
      params: { current, size }
    }) as Promise<PurchasePage>
  },

  /**
   * 检查是否已购买某作品
   * GET /api/catalog/purchases/check/{trackId}
   */
  checkPurchased(trackId: number): Promise<boolean> {
    return request({
      url: `/api/catalog/purchases/check/${trackId}`,
      method: 'GET'
    }) as Promise<boolean>
  }
}

/**
 * 根据资产 ID 构建文件访问 URL
 * 通过 vibe-asset 的 /assets/file/{id} 端点实时获取预签名 URL（302 重定向）
 * 适用于 <img> 和 <audio> 标签的 src 属性
 */
export function getAssetFileUrl(assetId: number | null): string {
  if (!assetId) return ''
  return `/api/assets/file/${assetId}`
}
