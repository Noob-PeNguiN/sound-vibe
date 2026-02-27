/**
 * Catalog 模块类型定义（对应后端 vibe-catalog）
 *
 * 支持两种作品类型：
 * - SINGLE（单文件）：包含 1 个文件（音频或 MIDI）
 * - PACK（合集/采样包）：包含多个文件，可混合音频 + MIDI
 */

// ==================== 枚举类型 ====================

/** 作品状态：0=私密, 1=公开 */
export type TrackStatus = 0 | 1

/** 作品可见范围：0=仅自己, 1=指定用户, 2=公开 */
export type TrackVisibility = 0 | 1 | 2

/** 文件类型：AUDIO=音频, MIDI=MIDI序列 */
export type FileType = 'AUDIO' | 'MIDI'

/** 作品类型：SINGLE=单文件, PACK=合集/采样包 */
export type TrackType = 'SINGLE' | 'PACK'

// ==================== TrackFile（PACK 合集中的文件） ====================

/**
 * 作品文件视图对象（对应后端 TrackFileVO）
 * 用于所有作品类型（SINGLE 和 PACK 都通过 track_files 管理）
 * 包含来自 assets 表的分析元数据（BPM / 调式 / 时长）
 */
export interface TrackFileItem {
  /** 文件记录 ID */
  id: number
  /** 文件资产 ID（关联 assets.id） */
  assetId: number
  /** 文件类型: AUDIO / MIDI */
  fileType: FileType
  /** 原始文件名 */
  originalName: string | null
  /** 排序序号 */
  sortOrder: number
  /** 是否允许预览 */
  allowPreview: boolean
  /** BPM 节拍速度（来自 assets 表分析结果） */
  bpm: number | null
  /** 音乐调式（来自 assets 表分析结果，如 "C Major"） */
  musicalKey: string | null
  /** 音频时长/秒（来自 assets 表分析结果） */
  duration: number | null
  /** AI 自动标注标签（来自 CLAP Zero-Shot 分析，逗号分隔） */
  autoTags: string | null
}

/**
 * 作品文件 DTO（对应后端 TrackFileDTO）
 * 用于发布/更新 PACK 时传入的文件列表项
 */
export interface TrackFileForm {
  /** 文件资产 ID（必填） */
  assetId: number
  /** 文件类型: AUDIO / MIDI */
  fileType: FileType
  /** 原始文件名（可选） */
  originalName?: string
  /** 排序序号（可选，从 0 开始） */
  sortOrder?: number
  /** 是否允许预览（可选，默认 true） */
  allowPreview?: boolean
}

// ==================== Track VO（对应 TrackVO.java） ====================

/**
 * 作品数据传输对象（可交易商品）
 * 与后端 TrackVO Record 字段一一对应
 */
export interface Track {
  /** 作品 ID */
  id: number
  /** 作品标题 */
  title: string
  /** 作品描述（用户自由描述 BPM、调式、风格等） */
  description: string | null
  /** 作品类型: SINGLE=单文件, PACK=合集 */
  trackType: TrackType
  /** 发布者 ID（关联 users.id） */
  producerId: number
  /** 发布者昵称 */
  producerName: string | null
  /** 封面资产 ID（关联 assets.id，IMAGE 类型） */
  coverId: number | null
  /** 文件资产 ID（仅 SINGLE，关联 assets.id） */
  fileId: number | null
  /** 文件类型（仅 SINGLE）: AUDIO / MIDI */
  fileType: FileType | null
  /** 文件数量（SINGLE=1, PACK=合集数量） */
  fileCount: number
  /** 文件列表（仅 PACK 且在详情页，列表页为 null） */
  files: TrackFileItem[] | null
  /** 价格（null 或 0 表示免费） */
  price: number | null
  /** 状态: 0=私密, 1=公开 */
  status: TrackStatus
  /** 可见范围: 0=仅自己, 1=指定用户, 2=公开 */
  visibility: TrackVisibility
  /** 标签（逗号分隔） */
  tags: string | null
  /** AI 自动标注标签（逗号分隔，从关联音频资产聚合） */
  autoTags: string | null
  /** 音乐风格/流派（如 "Trap", "Lo-Fi"） */
  genre: string | null
  /** 是否允许预览（付费作品由发布者控制，免费作品默认全曲可听） */
  allowPreview: boolean
  /** 预览时长（秒），仅付费作品生效 */
  previewDuration: number
  /** 已售数量 */
  soldCount: number
  /** 库存数量（null 表示不限库存，0 表示售罄） */
  stock: number | null
  /** 创建时间（ISO 字符串） */
  createTime: string
  /** 更新时间（ISO 字符串） */
  updateTime: string
}

// ==================== TrackPublishForm（对应 TrackPublishDTO.java） ====================

/**
 * 发布作品请求表单
 * SINGLE: 传 fileId + fileType
 * PACK: 传 files 列表
 */
export interface TrackPublishForm {
  /** 作品标题（必填） */
  title: string
  /** 作品描述（可选） */
  description: string
  /** 作品类型（SINGLE / PACK，默认 SINGLE） */
  trackType?: TrackType
  /** 文件资产 ID（SINGLE 时必填） */
  fileId: number | null
  /** 文件类型（SINGLE 时使用，AUDIO / MIDI，默认 AUDIO） */
  fileType: FileType
  /** 文件列表（PACK 时使用） */
  files?: TrackFileForm[]
  /** 封面资产 ID（可选） */
  coverId: number | null
  /** 标签（可选，逗号分隔） */
  tags: string
  /** 音乐风格（可选，如 "Trap", "Lo-Fi"） */
  genre?: string
  /** 价格（可选） */
  price: number | null
  /** 可见范围（可选，0=仅自己 1=指定用户 2=公开） */
  visibility?: TrackVisibility
  /** 是否允许预览（可选，默认 true） */
  allowPreview?: boolean
  /** 预览时长秒数（可选，默认 30） */
  previewDuration?: number
  /** 库存数量（可选，null 表示不限库存） */
  stock?: number | null
}

// ==================== TrackUpdateForm（对应 TrackUpdateDTO.java） ====================

/**
 * 更新作品请求表单（部分更新，所有字段可选）
 * 注意：trackType 不可更改
 * PACK: 如果传 files 将全量替换文件列表
 */
export interface TrackUpdateForm {
  /** 作品标题 */
  title?: string
  /** 作品描述 */
  description?: string
  /** 文件资产 ID（仅 SINGLE） */
  fileId?: number
  /** 文件类型（仅 SINGLE） */
  fileType?: FileType
  /** 文件列表（仅 PACK，全量替换） */
  files?: TrackFileForm[]
  /** 封面资产 ID */
  coverId?: number
  /** 标签 */
  tags?: string
  /** 音乐风格（可选） */
  genre?: string
  /** 价格 */
  price?: number
  /** 可见范围 */
  visibility?: TrackVisibility
  /** 是否允许预览 */
  allowPreview?: boolean
  /** 预览时长秒数 */
  previewDuration?: number
  /** 库存数量（null 表示不限库存） */
  stock?: number | null
}

// ==================== TrackQuery（对应 TrackQueryDTO.java） ====================

/**
 * 作品查询条件
 */
export interface TrackQuery {
  /** 关键词（按标题、标签、描述模糊搜索） */
  keyword?: string
  /** 标签精确匹配 */
  tag?: string
  /** 文件类型过滤（AUDIO / MIDI，仅对 SINGLE 有效） */
  fileType?: FileType
  /** 作品类型过滤（SINGLE / PACK） */
  trackType?: TrackType
  /** 作品状态（0=私密, 1=公开） */
  status?: number
  /** 可见范围（0=仅自己, 1=指定用户, 2=公开） */
  visibility?: number
  /** 发布者 ID（查询"我的作品"时使用） */
  producerId?: number
  /** 当前页码（从 1 开始） */
  current?: number
  /** 每页大小 */
  size?: number
}

// ==================== Purchase VO（对应 PurchaseVO.java） ====================

/**
 * 购买记录视图对象
 */
export interface PurchaseItem {
  /** 购买记录 ID */
  id: number
  /** 购买者 ID */
  userId: number
  /** 作品 ID */
  trackId: number
  /** 实付金额（0 = 免费获取） */
  pricePaid: number
  /** 关联的作品信息（作品被删除时为 null） */
  track: Track | null
  /** 购买时间（ISO 字符串） */
  createTime: string
}

// ==================== 分页响应（对应 MyBatis-Plus IPage） ====================

/**
 * 作品分页响应
 */
export interface TrackPage {
  records: Track[]
  total: number
  size: number
  current: number
  pages: number
}

/**
 * 购买记录分页响应
 */
export interface PurchasePage {
  records: PurchaseItem[]
  total: number
  size: number
  current: number
  pages: number
}
