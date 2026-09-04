import { useQuery } from '@tanstack/react-query';
import { statsApi } from '@/api';
import { queryKeys } from './queryKeys';

// 统计看板：概览 / 热点问题 / 趋势。

export function useStatsOverview() {
  return useQuery({ queryKey: queryKeys.statsOverview, queryFn: statsApi.overview });
}

export function useHotQuestions(limit = 10) {
  return useQuery({
    queryKey: queryKeys.hotQuestions(limit),
    queryFn: () => statsApi.hotQuestions(limit),
  });
}

export function useTrend(days = 7) {
  return useQuery({ queryKey: queryKeys.trend(days), queryFn: () => statsApi.trend(days) });
}
