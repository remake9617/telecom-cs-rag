import { http } from '../http';
import type { HotQuestion, StatsOverview, TrendPoint } from '@/types';

// 统计接口 /api/stats（cs-stats，管理员）。

export const statsApi = {
  /** 概览看板：咨询量 / 解决率 / 转人工率 等 */
  overview: () => http.get<StatsOverview>('/api/stats/overview'),
  /** 热点问题 Top N */
  hotQuestions: (limit = 10) =>
    http.get<HotQuestion[]>('/api/stats/hot-questions', { params: { limit } }),
  /** 趋势：近 N 天咨询量 / 解决量 */
  trend: (days = 7) => http.get<TrendPoint[]>('/api/stats/trend', { params: { days } }),
};
