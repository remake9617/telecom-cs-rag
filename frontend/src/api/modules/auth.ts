import { http } from '../http';
import type { AuthResult, UserVO } from '@/types';

// 认证接口 /api/auth（cs-system）。register/login 免鉴权，me 需 Bearer JWT。

export interface CredentialParams {
  username: string;
  password: string;
}

export const authApi = {
  /** 访客注册，返回 JWT + user */
  register: (params: CredentialParams) => http.post<AuthResult>('/api/auth/register', params),
  /** 登录 */
  login: (params: CredentialParams) => http.post<AuthResult>('/api/auth/login', params),
  /** 当前登录用户（刷新后恢复 user 用） */
  me: () => http.get<UserVO>('/api/auth/me'),
};
