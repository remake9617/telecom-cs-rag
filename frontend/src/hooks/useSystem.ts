import { useQuery } from '@tanstack/react-query';
import { systemApi, type UserListParams } from '@/api';
import { queryKeys } from './queryKeys';

// 用户管理（管理员）。端点为提案，未在 rest-api.md 冻结，详见 api/modules/system.ts 注释。
export function useUsers(params: UserListParams) {
  return useQuery({ queryKey: queryKeys.users(params), queryFn: () => systemApi.users(params) });
}
