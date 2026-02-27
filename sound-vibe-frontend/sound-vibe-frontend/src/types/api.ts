/**
 * 后端统一响应格式
 * 所有 API 调用必须使用此接口进行类型约束
 */
export interface Result<T = any> {
  code: number    // 200 = 成功, 其他 = 错误
  message: string // 错误信息或 "success"
  data: T         // 实际数据载荷
}

/**
 * 分页响应格式 (MyBatis-Plus IPage)
 */
export interface PageResult<T> {
  records: T[]   // 数据列表
  total: number  // 总记录数
  size: number   // 每页大小
  current: number // 当前页码
  pages: number  // 总页数
}
