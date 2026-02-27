import request from './request'
import type { MusicGenerateForm, MusicGenerateResult } from '@/types/generate'

/**
 * AI 音乐生成 API 模块
 * 对应后端 vibe-catalog 微服务的 /catalog/ai 路径
 * 通过 Vite 代理: /api/catalog -> http://localhost:8083/catalog
 */
export const generateApi = {
  /**
   * 文本生成音乐
   * POST /api/catalog/ai/generate
   */
  generateMusic(data: MusicGenerateForm): Promise<MusicGenerateResult> {
    return request({
      url: '/api/catalog/ai/generate',
      method: 'POST',
      data,
      timeout: 180000
    }) as Promise<MusicGenerateResult>
  }
}
