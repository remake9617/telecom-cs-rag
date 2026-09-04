import { HttpResponse } from 'msw';
import type { PageVO, R } from '@/api/types';

// Mock 响应助手：统一产出契约规定的 R<T> / PageVO<T>，保证与后端一致的数据形状。

/** 契约时间格式 yyyy-MM-dd HH:mm:ss */
export function nowStr(): string {
  const d = new Date();
  const pad = (n: number): string => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(
    d.getMinutes(),
  )}:${pad(d.getSeconds())}`;
}

/** 成功响应：code=0 */
export function ok<T>(data: T) {
  const body: R<T> = { code: 0, message: 'ok', data, timestamp: nowStr(), traceId: 'mock-trace' };
  return HttpResponse.json(body);
}

/** 业务失败响应：非 0 code + message（HTTP 仍 200，见契约通用约定） */
export function fail(code: number, message: string) {
  const body: R<null> = { code, message, data: null, timestamp: nowStr(), traceId: 'mock-trace' };
  return HttpResponse.json(body);
}

/** 分页响应：R<PageVO<T>> */
export function page<T>(records: T[], current = 1, size = 10, total = records.length) {
  const data: PageVO<T> = { records, total, current, size };
  return ok(data);
}

export const sleep = (ms: number): Promise<void> => new Promise((resolve) => setTimeout(resolve, ms));
