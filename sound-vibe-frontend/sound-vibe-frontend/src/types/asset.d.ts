/**
 * 数字资产接口（对应后端 AssetDTO.java）
 *
 * 安全设计：使用 assetCode（UUID）替代数据库自增主键，防止用户推测平台数据量
 *
 * 类型变更说明：
 * - 移除了 STEM 类型，所有音频统一为 AUDIO
 * - 新增 MIDI 类型，支持 MIDI 序列文件交易
 * - IMAGE 仅作为封面使用，不作为独立可交易商品
 */

/** 资产类型: AUDIO=音频, IMAGE=封面图片, MIDI=MIDI序列 */
export type AssetType = 'AUDIO' | 'IMAGE' | 'MIDI'

/** 资产状态：0-上传中 1-正常 2-已删除 */
export type AssetStatus = 0 | 1 | 2

/**
 * 资产数据传输对象
 * 与后端 AssetDTO Record 字段一一对应
 */
export interface Asset {
  /**
   * 数据库主键 ID
   * 用于 Catalog 模块关联（tracks.cover_id / tracks.file_id 引用 assets.id）
   */
  id: number
  /** 资产编码（UUID，对外唯一标识） */
  assetCode: string
  /** 原始文件名 */
  originalName: string
  /** 文件访问 URL（预签名，有时效） */
  url: string
  /** 文件大小（字节） */
  size: number
  /** 文件扩展名（如 mp3, wav, mid） */
  extension: string
  /** 资产类型 */
  type: AssetType
  /** 资产状态 */
  status: AssetStatus
  /** AI 自动标注标签（逗号分隔） */
  autoTags: string | null
  /** 创建时间（ISO 字符串） */
  createTime: string
}

/**
 * 分页响应格式（对应后端 MyBatis-Plus IPage）
 */
export interface AssetPage {
  records: Asset[]
  total: number
  size: number
  current: number
  pages: number
}
