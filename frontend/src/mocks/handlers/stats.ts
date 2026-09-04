import { http } from 'msw';
import { ok } from './_helpers';
import { seedHot, seedOverview, seedTrend } from '../data/seed';

// 统计 Mock：概览 / 热点问题（limit）/ 趋势（days）。
export const statsHandlers = [
  http.get('*/api/stats/overview', () => ok(seedOverview)),

  http.get('*/api/stats/hot-questions', ({ request }) => {
    const url = new URL(request.url);
    const limit = Number(url.searchParams.get('limit') ?? 10);
    return ok(seedHot.slice(0, limit));
  }),

  http.get('*/api/stats/trend', ({ request }) => {
    const url = new URL(request.url);
    const days = Number(url.searchParams.get('days') ?? 7);
    return ok(seedTrend.slice(-days));
  }),
];
