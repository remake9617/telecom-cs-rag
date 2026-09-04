import { http } from '../http';
import type { PageVO } from '../types';
import type { UserVO } from '@/types';

// 系统 / 用户管理接口 /api/system（cs-system，管理员）。
//
// ⚠ 契约状态：用户管理端点【未在 contract/rest-api.md 冻结】。
// 本文件为「提案实现」，沿用既有 UserVO 与 R<PageVO<T>> 约定，仅用于前端 Mock 独立演示；
// 正式纳入需负责人确认并更新 rest-api.md（见 FRONTEND-DECISIONS.md FD-7）。
// 后端未就绪时该页在真实模式下会走 StateBlock 错误态，不影响其他功能。

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
