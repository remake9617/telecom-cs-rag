// 统一响应与分页契约类型，严格对齐 contract/rest-api.md 与 CONVENTIONS 第 5 节。

/** 统一响应体 R<T>：code=0 成功，非 0 时 message 为可直接展示的错误信息 */
export interface R<T> {
  code: number;
  message: string;
  data: T;
  timestamp: number | string; // 后端 R.java 为 long（毫秒时间戳）；前端零读取点，声明兼容两种形态
  traceId: string;
}

/** 分页响应：请求 ?current=&size=，响应 data = PageVO<T> */
export interface PageVO<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

/** 分页请求公共参数 */
export interface PageQuery {
  current?: number;
  size?: number;
}

/**
 * 业务错误：由 axios 响应拦截器在 code!==0 时抛出。
 * 保留 code 与 traceId，便于按错误码分段处理（1xxx 通用 / 2xxx 入库 / 3xxx 问答 / 4xxx 工单 / 5xxx 系统）与问题追踪。
 */
export class BizError extends Error {
  code: number;
  traceId?: string;

  constructor(code: number, message: string, traceId?: string) {
    super(message);
    this.name = 'BizError';
    this.code = code;
    this.traceId = traceId;
  }
}
