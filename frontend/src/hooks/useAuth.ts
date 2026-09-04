import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { authApi } from '@/api';
import { useAuthStore } from '@/stores/authStore';
import { queryKeys } from './queryKeys';

// 拉取当前登录用户 /api/auth/me：用于刷新后恢复 authStore.user（路由守卫与角色入口依赖它）。
// 仅在有 token 时启用；成功后把 user 同步进 store。
export function useMe() {
  const token = useAuthStore((s) => s.token);
  const setUser = useAuthStore((s) => s.setUser);

  const query = useQuery({
    queryKey: queryKeys.me,
    queryFn: authApi.me,
    enabled: !!token,
    staleTime: 5 * 60 * 1000,
  });

  useEffect(() => {
    if (query.data) setUser(query.data);
  }, [query.data, setUser]);

  return query;
}
