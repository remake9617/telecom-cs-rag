import { http } from '../http';
import type { PageVO } from '../types';
import type { UserVO } from '@/types';

// 系统 / 用户管理接口 /api/system（cs-system，仅 ADMIN）。
// 契约见 contract/rest-api.md 第 7 节：
//   GET /api/system/users?current=&size=&keyword= -> R<PageVO<UserVO>>，keyword 按用户名/昵称模糊搜索。

export interface UserListParams {
  current?: number;
  size?: number;
  /** 按用户名/昵称模糊搜索（可选） */
  keyword?: string;
}

export const systemApi = {
  /** 用户分页列表（管理员） */
  users: (params: UserListParams) => http.get<PageVO<UserVO>>('/api/system/users', { params }),
};
