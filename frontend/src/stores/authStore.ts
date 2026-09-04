import { create } from 'zustand';
import type { UserVO } from '@/types';
import { clearToken, getToken, setToken } from '@/utils/token';

// 认证状态（客户端态）：token 持久化到 localStorage，user 刷新后经 /api/auth/me 恢复。
// 注意：本 store 不 import api 层，避免 client -> store -> api -> client 的循环依赖；
// 登录动作由页面调用 api 后再写入本 store。

interface AuthState {
  token: string | null;
  user: UserVO | null;
  /** 登录 / 注册成功：写入 token + user */
  setAuth: (token: string, user: UserVO) => void;
  /** 仅更新用户信息（如拉取 /me 后） */
  setUser: (user: UserVO) => void;
  /** 退出登录：清空内存与本地 token */
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: getToken(),
  user: null,
  setAuth: (token, user) => {
    setToken(token);
    set({ token, user });
  },
  setUser: (user) => set({ user }),
  logout: () => {
    clearToken();
    set({ token: null, user: null });
  },
}));

/** 是否已登录（有 token 即视为已登录，user 可能仍在加载） */
export const selectIsAuthed = (s: AuthState): boolean => !!s.token;
