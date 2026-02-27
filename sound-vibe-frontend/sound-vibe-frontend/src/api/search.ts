import request from './request'

/**
 * ES 搜索结果中的作品文档（对应后端 TrackDoc v3）
 */
export interface TrackSearchResult {
  id: number
  title: string
  trackType: string | null
  producerId: number
  producerName: string | null
  coverId: number | null
  bpmValues: number[] | null
  musicalKeys: string[] | null
  /** 风格列表（从 tags 逗号分隔解析） */
  genres: string[] | null
  durations: number[] | null
  tags: string | null
  /** AI 自动标注标签列表 */
  autoTags: string[] | null
  price: number | null
  status: number
}

export interface SearchPage {
  content: TrackSearchResult[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface SearchParams {
  keyword?: string
  genre?: string
  minBpm?: number
  maxBpm?: number
  musicalKey?: string
  page?: number
  size?: number
}

/** 动态筛选选项（来自 ES 聚合） */
export interface FilterOptions {
  musicalKeys: string[]
  genres: string[]
}

export const searchApi = {
  searchTracks(params?: SearchParams): Promise<SearchPage> {
    return request({
      url: '/api/search/tracks',
      method: 'GET',
      params: {
        keyword: params?.keyword || undefined,
        genre: params?.genre || undefined,
        minBpm: params?.minBpm || undefined,
        maxBpm: params?.maxBpm || undefined,
        musicalKey: params?.musicalKey || undefined,
        page: params?.page ?? 0,
        size: params?.size ?? 20
      }
    }) as Promise<SearchPage>
  },

  /**
   * 获取当前可用的筛选选项（支持级联）
   * 选了调式 → 只返回含该调式的作品的风格
   * 选了风格 → 只返回含该风格的作品的调式
   */
  getFilters(params?: { musicalKey?: string; genre?: string }): Promise<FilterOptions> {
    return request({
      url: '/api/search/filters',
      method: 'GET',
      params: {
        musicalKey: params?.musicalKey || undefined,
        genre: params?.genre || undefined,
      }
    }) as Promise<FilterOptions>
  },

  /**
   * AI 语义搜索（基于 CLAP 音频向量的 kNN 近邻搜索）
   * 用户输入自然语言描述，后端将文本转换为向量并执行 kNN 搜索
   * 返回按相似度排序的作品列表（非分页）
   */
  semanticSearch(q: string, k: number = 10): Promise<TrackSearchResult[]> {
    return request({
      url: '/api/search/semantic',
      method: 'GET',
      params: { q, k },
      timeout: 15000
    }) as Promise<TrackSearchResult[]>
  }
}
