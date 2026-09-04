import { useQuery } from '@tanstack/react-query';
import { systemApi, type UserListParams } from '@/api';
import { queryKeys } from './queryKeys';

// 用户管理（仅 ADMIN）。契约见 rest-api.md 第 7 节 GET /api/system/users。
export function useUsers(params: UserListParams) {
  return useQuery({ queryKey: queryKeys.users(params), queryFn: () => systemApi.users(params) });
}
