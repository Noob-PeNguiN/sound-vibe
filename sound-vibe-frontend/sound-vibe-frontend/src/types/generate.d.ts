/**
 * AI 音乐生成相关类型定义
 */

/** 生成请求参数 */
export interface MusicGenerateForm {
  prompt: string
  duration: number
}

/** 生成结果 */
export interface MusicGenerateResult {
  url: string
  prompt: string
  duration: number
}

/** 历史记录项（本地存储） */
export interface GenerateHistoryItem {
  id: string
  prompt: string
  duration: number
  url: string
  createdAt: string
}
