import request from './request'
import type { Asset, AssetType, AssetPage } from '@/types/asset'

/**
 * 上传参数
 */
export interface UploadParams {
  /** 要上传的文件 */
  file: File
  /** 资产类型（可选，不传则由后端根据扩展名推断） */
  type?: AssetType
}

/**
 * 列表查询参数
 */
export interface ListParams {
  /** 当前页码（从 1 开始） */
  current?: number
  /** 每页大小 */
  size?: number
  /** 资产类型过滤 */
  type?: AssetType
  /** 搜索关键词（文件名模糊匹配） */
  keyword?: string
}

/**
 * 数字资产 API 模块
 * 对应后端 vibe-asset 微服务（端口 8082）
 * 通过 Vite 代理: /api/assets -> http://localhost:8082/assets
 */
export const assetApi = {
  /**
   * 上传文件
   * POST /api/assets/upload
   */
  upload(params: UploadParams): Promise<Asset> {
    const formData = new FormData()
    formData.append('file', params.file)

    if (params.type) {
      formData.append('type', params.type)
    }

    return request({
      url: '/api/assets/upload',
      method: 'POST',
      data: formData,
      headers: {
        'Content-Type': 'multipart/form-data'
      },
      timeout: 60000
    }) as Promise<Asset>
  },

  /**
   * 查询当前用户的资产列表（分页）
   * GET /api/assets?current=1&size=20&type=AUDIO&keyword=xxx
   */
  list(params?: ListParams): Promise<AssetPage> {
    return request({
      url: '/api/assets',
      method: 'GET',
      params: {
        current: params?.current ?? 1,
        size: params?.size ?? 20,
        type: params?.type,
        keyword: params?.keyword
      }
    }) as Promise<AssetPage>
  },

  /**
   * 获取单个资产详情
   * GET /api/assets/{assetCode}
   */
  getByCode(assetCode: string): Promise<Asset> {
    return request({
      url: `/api/assets/${assetCode}`,
      method: 'GET'
    }) as Promise<Asset>
  },

  /**
   * 删除资产
   * DELETE /api/assets/{assetCode}
   */
  delete(assetCode: string): Promise<void> {
    return request({
      url: `/api/assets/${assetCode}`,
      method: 'DELETE'
    }) as Promise<void>
  },

  /**
   * 重命名资产
   * PUT /api/assets/{assetCode}/rename
   */
  rename(assetCode: string, newName: string): Promise<Asset> {
    return request({
      url: `/api/assets/${assetCode}/rename`,
      method: 'PUT',
      data: { newName }
    }) as Promise<Asset>
  }
}

/**
 * 下载资产文件（Blob 方式，不跳转页面）
 *
 * 通过后端 GET /assets/download/{id} 端点流式获取文件内容，
 * 在前端创建 Blob URL 触发浏览器下载。
 * 解决 302 重定向到 MinIO 跨域导致无法下载的问题。
 *
 * @param assetId  资产数据库主键 ID
 * @param fileName 期望的下载文件名（可选，后端也会通过 Content-Disposition 设置）
 */
export async function downloadAssetFile(assetId: number, fileName?: string): Promise<void> {
  try {
    // 构建下载 URL（通过 Vite 代理 → 后端流式返回文件内容）
    const url = `/api/assets/download/${assetId}`

    // 注入 Token（与 Axios 拦截器行为一致）
    const token = localStorage.getItem('soundvibe-token')
    const headers: Record<string, string> = {}
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
    const userInfo = localStorage.getItem('soundvibe-user')
    if (userInfo) {
      try {
        const user = JSON.parse(userInfo)
        if (user.userId) {
          headers['X-User-Id'] = String(user.userId)
        }
      } catch {
        // ignore
      }
    }

    const response = await fetch(url, { headers })

    if (!response.ok) {
      throw new Error(`下载失败: HTTP ${response.status}`)
    }

    // 从 Content-Disposition 提取文件名（如果前端未指定）
    if (!fileName) {
      const disposition = response.headers.get('Content-Disposition')
      if (disposition) {
        // 尝试解析 filename*=UTF-8''xxx 或 filename="xxx"
        const utf8Match = disposition.match(/filename\*=UTF-8''(.+?)(?:;|$)/)
        const basicMatch = disposition.match(/filename="(.+?)"/)
        const extracted = utf8Match?.[1] || basicMatch?.[1]
        if (extracted) {
          fileName = decodeURIComponent(extracted)
        }
      }
    }

    const blob = await response.blob()
    const blobUrl = window.URL.createObjectURL(blob)

    // 创建临时 <a> 触发下载（同源 Blob URL，download 属性一定生效）
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = fileName || 'download'
    a.style.display = 'none'
    document.body.appendChild(a)
    a.click()

    // 清理
    document.body.removeChild(a)
    window.URL.revokeObjectURL(blobUrl)
  } catch (error) {
    console.error('[downloadAssetFile] 下载失败:', error)
    throw error
  }
}
